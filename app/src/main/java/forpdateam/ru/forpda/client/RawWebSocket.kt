package forpdateam.ru.forpda.client

import android.util.Log
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import java.io.ByteArrayOutputStream
import java.io.EOFException
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException
import java.security.SecureRandom

/**
 * 4pda realtime events server moved from ws://app.4pda.to/ws/ (port 80, now refuses
 * connections) to app.4pda.to:993, and that one is a weird beast: no HTTP upgrade,
 * no TLS, it wants raw RFC 6455 frames straight over plain TCP from the first byte.
 * OkHttp (or any sane WS library) starts with an HTTP upgrade, so it never gets in.
 *
 * This is a tiny hand-rolled frame codec that pretends to be an okhttp3.WebSocket,
 * so WebSocketController / EventsRepository keep working without knowing the corpse
 * got a new heart. Same protocol on top: [id, "sv"], [0, "ea", "u<userId>"], etc.
 */
class RawWebSocket(
    private val host: String,
    private val port: Int,
    private val listener: WebSocketListener
) : WebSocket {

    companion object {
        private const val LOG_TAG = "RawWebSocket"
        private const val CONNECT_TIMEOUT_MS = 15_000
        // server gets a ping this often; no bytes back for READ_TIMEOUT_MS = dead socket
        private const val PING_INTERVAL_MS = 30_000L
        private const val READ_TIMEOUT_MS = 90_000

        private const val OP_CONTINUATION = 0x0
        private const val OP_TEXT = 0x1
        private const val OP_BINARY = 0x2
        private const val OP_CLOSE = 0x8
        private const val OP_PING = 0x9
        private const val OP_PONG = 0xA
    }

    private val fakeRequest = Request.Builder().url("http://$host:$port/").build()
    private val random = SecureRandom()
    private val writeLock = Any()

    @Volatile
    private var socket: Socket? = null
    @Volatile
    private var output: OutputStream? = null
    @Volatile
    private var closed = false

    fun connect(): RawWebSocket {
        Thread({ run() }, "RawWebSocket-$port").apply { isDaemon = true }.start()
        return this
    }

    private fun run() {
        try {
            val s = Socket()
            socket = s
            s.connect(InetSocketAddress(host, port), CONNECT_TIMEOUT_MS)
            s.soTimeout = READ_TIMEOUT_MS
            s.tcpNoDelay = true
            if (closed) {
                s.close()
                return
            }
            output = s.getOutputStream()
            listener.onOpen(this, fakeResponse())
            startPinger()
            readLoop(s.getInputStream())
        } catch (ex: Exception) {
            if (!closed) {
                closed = true
                closeSocketQuietly()
                listener.onFailure(this, ex, null)
            }
        }
    }

    private fun startPinger() {
        Thread({
            try {
                while (!closed) {
                    Thread.sleep(PING_INTERVAL_MS)
                    if (!closed) writeFrame(OP_PING, ByteArray(0))
                }
            } catch (ignored: Exception) {
                // read loop notices the dead socket and reports it
            }
        }, "RawWebSocket-ping-$port").apply { isDaemon = true }.start()
    }

    private fun readLoop(input: InputStream) {
        val message = ByteArrayOutputStream()
        var messageOpcode = OP_TEXT
        while (!closed) {
            val b0 = readByte(input)
            val b1 = readByte(input)
            val fin = b0 and 0x80 != 0
            val opcode = b0 and 0x0F
            val masked = b1 and 0x80 != 0
            var length = (b1 and 0x7F).toLong()
            if (length == 126L) {
                length = ((readByte(input) shl 8) or readByte(input)).toLong()
            } else if (length == 127L) {
                length = 0
                repeat(8) { length = (length shl 8) or readByte(input).toLong() }
            }
            val mask = if (masked) readFully(input, 4) else null
            val payload = readFully(input, length.toInt())
            if (mask != null) {
                for (i in payload.indices) payload[i] = (payload[i].toInt() xor mask[i % 4].toInt()).toByte()
            }

            when (opcode) {
                OP_PING -> writeFrame(OP_PONG, payload)
                OP_PONG -> Unit
                OP_CLOSE -> {
                    val code = if (payload.size >= 2) ((payload[0].toInt() and 0xFF) shl 8) or (payload[1].toInt() and 0xFF) else 1005
                    closed = true
                    closeSocketQuietly()
                    listener.onClosed(this, code, "")
                    return
                }
                OP_TEXT, OP_BINARY, OP_CONTINUATION -> {
                    if (opcode != OP_CONTINUATION) {
                        message.reset()
                        messageOpcode = opcode
                    }
                    message.write(payload)
                    if (fin) {
                        val bytes = message.toByteArray()
                        message.reset()
                        if (messageOpcode == OP_TEXT) {
                            listener.onMessage(this, String(bytes, Charsets.UTF_8))
                        } else {
                            listener.onMessage(this, ByteString.of(*bytes))
                        }
                    }
                }
                else -> Log.w(LOG_TAG, "unknown opcode $opcode")
            }
        }
    }

    private fun readByte(input: InputStream): Int {
        val b = try {
            input.read()
        } catch (ex: SocketTimeoutException) {
            throw IOException("raw ws: nothing from server for ${READ_TIMEOUT_MS / 1000}s", ex)
        }
        if (b == -1) throw EOFException("raw ws: socket EOF")
        return b
    }

    private fun readFully(input: InputStream, size: Int): ByteArray {
        val buf = ByteArray(size)
        var off = 0
        while (off < size) {
            val r = input.read(buf, off, size - off)
            if (r == -1) throw EOFException("raw ws: socket EOF mid-frame")
            off += r
        }
        return buf
    }

    /** Client frames must be masked (RFC 6455), the server rejects unmasked ones. */
    private fun writeFrame(opcode: Int, payload: ByteArray): Boolean {
        val out = output ?: return false
        val frame = ByteArrayOutputStream(payload.size + 14)
        frame.write(0x80 or opcode)
        when {
            payload.size < 126 -> frame.write(0x80 or payload.size)
            payload.size <= 0xFFFF -> {
                frame.write(0x80 or 126)
                frame.write(payload.size shr 8)
                frame.write(payload.size and 0xFF)
            }
            else -> {
                frame.write(0x80 or 127)
                for (shift in 56 downTo 0 step 8) frame.write(((payload.size.toLong() shr shift) and 0xFF).toInt())
            }
        }
        val mask = ByteArray(4).also { random.nextBytes(it) }
        frame.write(mask)
        for (i in payload.indices) frame.write(payload[i].toInt() xor mask[i % 4].toInt())
        return try {
            synchronized(writeLock) {
                out.write(frame.toByteArray())
                out.flush()
            }
            true
        } catch (ex: IOException) {
            false
        }
    }

    private fun fakeResponse(): Response = Response.Builder()
        .request(fakeRequest)
        .protocol(Protocol.HTTP_1_1)
        .code(101)
        .message("raw ws")
        .build()

    private fun closeSocketQuietly() {
        try {
            socket?.close()
        } catch (ignored: Exception) {
        }
    }

    override fun request(): Request = fakeRequest

    override fun queueSize(): Long = 0

    override fun send(text: String): Boolean = !closed && writeFrame(OP_TEXT, text.toByteArray(Charsets.UTF_8))

    override fun send(bytes: ByteString): Boolean = !closed && writeFrame(OP_BINARY, bytes.toByteArray())

    override fun close(code: Int, reason: String?): Boolean {
        if (closed) return false
        val reasonBytes = reason.orEmpty().toByteArray(Charsets.UTF_8)
        val payload = ByteArray(2 + reasonBytes.size)
        payload[0] = (code shr 8).toByte()
        payload[1] = code.toByte()
        System.arraycopy(reasonBytes, 0, payload, 2, reasonBytes.size)
        writeFrame(OP_CLOSE, payload)
        closed = true
        closeSocketQuietly()
        return true
    }

    override fun cancel() {
        closed = true
        closeSocketQuietly()
    }
}

package forpdateam.ru.forpda.notifications.push

import android.util.Base64
import android.util.Log
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.EOFException
import java.io.IOException
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.charset.Charset
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.zip.Deflater
import java.util.zip.Inflater
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLException
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory

/**
 * Tells 4PDA's server "this user, this FCM token, these notification kinds".
 *
 * The only route is a WebSocket over TLS (address in [PushConfig]), and the certificate has to
 * match the host name. There is no plain TCP fallback on purpose: the pass_hash would travel in the
 * open. If the tunnel does not come up, nothing is registered and the periodic check keeps working.
 *
 * Inside the WebSocket the payload is a JSON-ish array in cp1251, deflated with a 4 byte length in
 * front. A request is [reqId, "<two letter command>", args...] and the answer is [reqId, status, ...],
 * status 0 meaning fine. Three commands are enough here:
 *
 *   ah  hello: [clientVersion, "", "", 0, userId]
 *   ma  auth:  [userId, passHash]   (the forum's own pass_hash cookie works as the key)
 *   ai  push:  [fcmToken, typeMask, 0]
 *
 * Blocking, so call it off the main thread.
 */
object PushRegistration {

    private const val TAG = "PushRegistration"
    private val CP1251 = Charset.forName("windows-1251")
    private val random = SecureRandom()

    private const val CONNECT_TIMEOUT_MS = 15_000
    private const val READ_TIMEOUT_MS = 20_000
    private const val WS_GUID = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11"
    private const val MAX_HEAD = 8192

    private const val CMD_HELLO = 26721   // "ah"
    private const val CMD_AUTH = 24941    // "ma"
    private const val CMD_PUSH = 26977    // "ai"

    private class Conn(val output: OutputStream, val input: DataInputStream, val inflater: Inflater)

    /** @return null when the server accepted everything, otherwise what went wrong. Never contains the pass_hash. */
    fun register(userId: Int, passHash: String, token: String, typeMask: Int): String? {
        val raw = Socket()
        var open: Socket = raw
        var inflater: Inflater? = null
        return try {
            raw.connect(InetSocketAddress(PushConfig.HOST, PushConfig.PORT), CONNECT_TIMEOUT_MS)
            raw.soTimeout = READ_TIMEOUT_MS
            val ssl = (SSLSocketFactory.getDefault() as SSLSocketFactory)
                    .createSocket(raw, PushConfig.HOST, PushConfig.PORT, true) as SSLSocket
            open = ssl
            ssl.startHandshake()
            // A bare SSLSocket checks the chain but not the name in the certificate. Do it by hand.
            if (!HttpsURLConnection.getDefaultHostnameVerifier().verify(PushConfig.HOST, ssl.session)) {
                return "tls: certificate does not match ${PushConfig.HOST}"
            }
            inflater = Inflater(true)
            val conn = Conn(ssl.getOutputStream(), DataInputStream(ssl.getInputStream()), inflater)
            upgrade(conn)?.let { return "upgrade: $it" }

            call(conn, 1, CMD_HELLO, listOf(PushConfig.CLIENT_VERSION, "", "", 0, userId))?.let { return "hello: $it" }
            call(conn, 2, CMD_AUTH, listOf(userId, passHash))?.let { return "auth: $it" }
            call(conn, 3, CMD_PUSH, listOf(token, typeMask, 0))?.let { return "push: $it" }
            null
        } catch (ex: SSLException) {
            Log.w(TAG, "tls failed: ${ex.javaClass.simpleName}")
            "tls: $ex"
        } catch (ex: Exception) {
            Log.w(TAG, "register failed: ${ex.javaClass.simpleName}")
            ex.toString()
        } finally {
            inflater?.end()
            try {
                open.close()
            } catch (ignore: Exception) {
            }
        }
    }

    /** Plain HTTP upgrade to a WebSocket, sent inside the TLS tunnel. @return null when accepted. */
    private fun upgrade(conn: Conn): String? {
        val key = Base64.encodeToString(ByteArray(16).also { random.nextBytes(it) }, Base64.NO_WRAP)
        val request = "GET ${PushConfig.PATH} HTTP/1.1\r\n" +
                "Host: ${PushConfig.HOST}\r\n" +
                "Upgrade: websocket\r\n" +
                "Connection: Upgrade\r\n" +
                "Sec-WebSocket-Key: $key\r\n" +
                "Sec-WebSocket-Version: 13\r\n" +
                "Sec-WebSocket-Protocol: app\r\n\r\n"
        conn.output.write(request.toByteArray(Charsets.US_ASCII))
        conn.output.flush()

        val lines = readHead(conn.input).split("\r\n")
        if (lines.firstOrNull()?.contains(" 101 ") != true) return lines.firstOrNull() ?: "empty reply"
        val expected = Base64.encodeToString(
                MessageDigest.getInstance("SHA-1").digest((key + WS_GUID).toByteArray(Charsets.US_ASCII)),
                Base64.NO_WRAP
        )
        val accept = lines.drop(1)
                .map { it.split(":", limit = 2) }
                .firstOrNull { it.size == 2 && it[0].trim().equals("Sec-WebSocket-Accept", ignoreCase = true) }
                ?.get(1)?.trim()
        return if (accept == expected) null else "bad Sec-WebSocket-Accept"
    }

    /** Reads up to the empty line that ends the HTTP headers, not a byte more. */
    private fun readHead(input: DataInputStream): String {
        val sb = StringBuilder()
        while (!sb.endsWith("\r\n\r\n")) {
            if (sb.length > MAX_HEAD) throw IOException("HTTP head too long")
            sb.append(input.readUnsignedByte().toChar())
        }
        return sb.toString()
    }

    private fun call(conn: Conn, requestId: Int, command: Int, args: List<Any>): String? {
        val line = StringBuilder().append('[').append(requestId).append(',')
        appendValue(line, ((command and 255).toChar().toString() + ((command shr 8) and 255).toChar()))
        for (arg in args) {
            line.append(',')
            appendValue(line, arg)
        }
        line.append(']')
        writeFrame(conn, line.toString().toByteArray(CP1251))

        // Skip anything that is not the answer to this request (pings, other replies).
        repeat(20) {
            val frame = readFrame(conn) ?: return@repeat
            val text = String(frame, CP1251)
            val parts = text.trim('[', ']').split(',')
            if (parts.size >= 2 && parts[0].toIntOrNull() == requestId) {
                val status = parts[1].toIntOrNull()
                return if (status == 0) null else "status ${parts[1]}"
            }
        }
        return "no reply"
    }

    private fun appendValue(sb: StringBuilder, value: Any) {
        when (value) {
            is Int -> sb.append(value)
            else -> {
                sb.append('"')
                for (ch in value.toString()) {
                    if (ch == '"' || ch == '\\') sb.append('\\')
                    sb.append(ch)
                }
                sb.append('"')
            }
        }
    }

    /** Client frames must be masked (RFC 6455), the server drops the unmasked ones. */
    private fun writeFrame(conn: Conn, payload: ByteArray) {
        val deflater = Deflater(6, true)
        val packed = ByteArray(payload.size + 100)
        val packedSize = try {
            deflater.setInput(payload)
            deflater.finish()
            deflater.deflate(packed)
        } finally {
            deflater.end()
        }
        val body = ByteArray(packedSize + 4)
        for ((index, shift) in intArrayOf(0, 8, 16, 24).withIndex()) body[index] = (payload.size shr shift).toByte()
        System.arraycopy(packed, 0, body, 4, packedSize)

        val mask = ByteArray(4).also { random.nextBytes(it) }
        for (i in body.indices) body[i] = (body[i].toInt() xor mask[i % 4].toInt()).toByte()

        val length = body.size
        val header = ByteArrayOutputStream()
        header.write(0x80 or 0x40 or 1) // FIN, RSV1 = compressed, text
        when {
            length <= 125 -> header.write(0x80 or length)
            length <= 65535 -> {
                header.write(0x80 or 126)
                header.write(length shr 8)
                header.write(length and 255)
            }
            else -> throw IllegalArgumentException("request too big")
        }
        header.write(mask)
        conn.output.write(header.toByteArray())
        conn.output.write(body)
        conn.output.flush()
    }

    /** Next data frame, or null for control frames. Pings get a pong right away. */
    private fun readFrame(conn: Conn): ByteArray? {
        val input = conn.input
        val first = input.readUnsignedByte()
        val second = input.readUnsignedByte()
        val opcode = first and 15
        var length = second and 127
        if (length == 126) length = input.readUnsignedShort()
        else if (length == 127) {
            input.readInt()
            length = input.readInt()
        }
        val serverMask = if (second and 0x80 != 0) ByteArray(4).also { input.readFully(it) } else null
        val body = ByteArray(length)
        input.readFully(body)
        if (serverMask != null) for (i in body.indices) body[i] = (body[i].toInt() xor serverMask[i % 4].toInt()).toByte()
        when (opcode) {
            9 -> {
                conn.output.write(byteArrayOf(0x8A.toByte(), 0x80.toByte()))
                conn.output.write(ByteArray(4).also { random.nextBytes(it) })
                conn.output.flush()
                return null
            }
            1, 2 -> {
                if (first and 0x40 == 0) return body
                val size = (body[0].toInt() and 255) or ((body[1].toInt() and 255) shl 8) or
                        ((body[2].toInt() and 255) shl 16) or ((body[3].toInt() and 255) shl 24)
                val out = ByteArray(size)
                conn.inflater.setInput(body, 4, body.size - 4)
                conn.inflater.inflate(out)
                // The sender flushes its deflate stream; feed the tail so the next frame starts clean.
                conn.inflater.setInput(byteArrayOf(0, 0, -1, -1))
                conn.inflater.inflate(ByteArray(64))
                return out
            }
            8 -> throw EOFException("server closed the connection")
            else -> return null
        }
    }
}

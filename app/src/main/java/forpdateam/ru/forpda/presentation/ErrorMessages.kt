package forpdateam.ru.forpda.presentation

import forpdateam.ru.forpda.client.GoogleCaptchaException
import forpdateam.ru.forpda.client.OkHttpResponseException
import java.io.IOException
import java.io.InterruptedIOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.security.cert.CertificateException
import java.util.concurrent.CancellationException
import java.util.concurrent.TimeoutException
import javax.net.ssl.SSLException

/**
 * Turns an exception into what the user should be told. Pure Kotlin (no Android), so every case is
 * unit tested; [ErrorHandler] maps the [Kind] to a string resource. Raw exception text is for the log,
 * it reaches the user only as a last resort and only when it reads like a sentence.
 */
object ErrorMessages {

    enum class Kind {
        /** Nothing to show: the work was cancelled (a screen closed), not failed. */
        SILENT,
        TIMEOUT,
        NO_HOST_4PDA,
        NO_HOST,
        TLS,
        HTTP_401,
        HTTP_403,
        /** [Result.detail] is the short path of the page. */
        HTTP_404,
        HTTP_451,
        /** [Result.code] is the HTTP code. */
        HTTP_SERVER,
        NO_NETWORK,
        /** [Result.detail] is the exception's own message, readable as is. */
        RAW,
        UNKNOWN
    }

    data class Result(val kind: Kind, val code: Int = 0, val detail: String? = null)

    fun classify(t: Throwable): Result {
        val chain = chain(t)
        if (chain.any { it is CancellationException }) return Result(Kind.SILENT)
        if (chain.any { isTimeout(it) }) return Result(Kind.TIMEOUT)

        chain.firstOrNull { it is UnknownHostException }?.let {
            return Result(if (it.message?.contains("4pda.to") == true) Kind.NO_HOST_4PDA else Kind.NO_HOST)
        }
        if (chain.any { isTls(it) }) return Result(Kind.TLS)

        (chain.firstOrNull { it is OkHttpResponseException } as? OkHttpResponseException)?.let { http ->
            return when (val code = http.code) {
                401 -> Result(Kind.HTTP_401, code)
                403 -> Result(Kind.HTTP_403, code)
                404 -> Result(Kind.HTTP_404, code, shortPath(http.url.orEmpty()))
                451 -> Result(Kind.HTTP_451, code)
                in 500..599 -> Result(Kind.HTTP_SERVER, code)
                else -> rawOrUnknown(t)
            }
        }
        // A 403 that is not a Cloudflare check (those are ServerRefusedException, handled before us).
        if (chain.any { it is GoogleCaptchaException }) return Result(Kind.HTTP_403, 403)
        if (chain.any { it is IOException }) return Result(Kind.NO_NETWORK)
        return rawOrUnknown(t)
    }

    /** "https://4pda.to/forum/index.php?showtopic=1" -> "/forum/index.php?showtopic=1", at most 120 chars. */
    fun shortPath(url: String): String {
        val noScheme = url.substringAfter("://", url)
        val path = if (noScheme.contains('/')) "/" + noScheme.substringAfter('/') else ""
        return if (path.length > MAX_PATH) path.take(MAX_PATH - 1) + "…" else path
    }

    /**
     * Seconds to wait from the forum's own text ("не чаще, чем раз в 30 секунд", "подождите 2 минуты"),
     * or null when the text names no wait.
     */
    fun waitSecondsFromText(text: String?): Long? {
        if (text.isNullOrBlank()) return null
        val m = WAIT_PATTERN.find(text) ?: return null
        val n = m.groupValues[1].toLongOrNull() ?: return null
        val unit = m.groupValues[2].lowercase()
        return if (unit.startsWith("мин") || unit.startsWith("min")) n * 60 else n
    }

    /** A message that is really a class name / stack dump, not a sentence for a person. */
    fun isExceptionDump(message: String): Boolean {
        val m = message.trim()
        return DUMP_PATTERN.containsMatchIn(m) || m.startsWith("java.") || m.startsWith("kotlin.") ||
                m.startsWith("android.") || m.contains("Exception:") || m.contains("\tat ")
    }

    private fun rawOrUnknown(t: Throwable): Result {
        val message = t.message?.trim()
        return if (!message.isNullOrBlank() && !isExceptionDump(message)) Result(Kind.RAW, detail = message)
        else Result(Kind.UNKNOWN)
    }

    private fun chain(t: Throwable): List<Throwable> =
        generateSequence(t) { it.cause?.takeIf { c -> c !== it } }.take(MAX_CAUSE_DEPTH).toList()

    private fun isTimeout(t: Throwable): Boolean =
        t is SocketTimeoutException || t is TimeoutException ||
                (t is InterruptedIOException && t.message?.contains("timeout", ignoreCase = true) == true) ||
                t.message?.contains("did not signal an event", ignoreCase = true) == true

    private fun isTls(t: Throwable): Boolean =
        t is SSLException || t is CertificateException ||
                t.message?.contains("certificate", ignoreCase = true) == true

    private const val MAX_CAUSE_DEPTH = 16
    private const val MAX_PATH = 120
    private val WAIT_PATTERN = Regex("(\\d+)\\s*(сек|мин|sec|min)", RegexOption.IGNORE_CASE)
    private val DUMP_PATTERN = Regex("^[a-z][a-z0-9_]*(\\.[A-Za-z0-9_$]+)+(Exception|Error)\\b")
}

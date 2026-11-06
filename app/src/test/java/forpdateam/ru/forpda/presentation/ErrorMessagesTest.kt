package forpdateam.ru.forpda.presentation

import forpdateam.ru.forpda.client.GoogleCaptchaException
import forpdateam.ru.forpda.client.OkHttpResponseException
import forpdateam.ru.forpda.presentation.ErrorMessages.Kind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.net.ConnectException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.security.cert.CertificateException
import java.util.concurrent.CancellationException
import java.util.concurrent.TimeoutException
import javax.net.ssl.SSLHandshakeException

class ErrorMessagesTest {

    private fun kind(t: Throwable) = ErrorMessages.classify(t).kind

    @Test fun cancellationIsSilent() {
        assertEquals(Kind.SILENT, kind(CancellationException("Job was cancelled")))
        assertEquals(Kind.SILENT, kind(RuntimeException("wrapped", CancellationException())))
    }

    @Test fun timeouts() {
        assertEquals(Kind.TIMEOUT, kind(SocketTimeoutException("timeout")))
        assertEquals(Kind.TIMEOUT, kind(TimeoutException()))
        assertEquals(Kind.TIMEOUT, kind(RuntimeException("The source did not signal an event for 30 seconds")))
        assertEquals(Kind.TIMEOUT, kind(RuntimeException("x", SocketTimeoutException())))
    }

    @Test fun unknownHost() {
        assertEquals(Kind.NO_HOST_4PDA, kind(UnknownHostException("Unable to resolve host \"4pda.to\": No address")))
        assertEquals(Kind.NO_HOST, kind(UnknownHostException("Unable to resolve host \"example.com\"")))
    }

    @Test fun tls() {
        assertEquals(Kind.TLS, kind(SSLHandshakeException("handshake failed")))
        assertEquals(Kind.TLS, kind(IOException("x", CertificateException("bad"))))
        assertEquals(Kind.TLS, kind(IOException("Unacceptable certificate: CN=None")))
    }

    @Test fun httpCodes() {
        fun http(code: Int, url: String = "https://4pda.to/forum/index.php?showtopic=1") =
            ErrorMessages.classify(OkHttpResponseException(code, "", url))
        assertEquals(Kind.HTTP_401, http(401).kind)
        assertEquals(Kind.HTTP_403, http(403).kind)
        val notFound = http(404)
        assertEquals(Kind.HTTP_404, notFound.kind)
        assertEquals("/forum/index.php?showtopic=1", notFound.detail)
        assertEquals(Kind.HTTP_451, http(451).kind)
        val server = http(500)
        assertEquals(Kind.HTTP_SERVER, server.kind)
        assertEquals(500, server.code)
        assertEquals(Kind.HTTP_SERVER, http(599).kind)
    }

    @Test fun captchaWithoutCloudflareIs403() {
        assertEquals(Kind.HTTP_403, kind(GoogleCaptchaException("<html>")))
    }

    @Test fun noNetwork() {
        assertEquals(Kind.NO_NETWORK, kind(ConnectException("Connection refused")))
        assertEquals(Kind.NO_NETWORK, kind(SocketException("Connection reset")))
        assertEquals(Kind.NO_NETWORK, kind(IOException("unexpected end of stream")))
    }

    @Test fun fallback() {
        val raw = ErrorMessages.classify(RuntimeException("Тема закрыта"))
        assertEquals(Kind.RAW, raw.kind)
        assertEquals("Тема закрыта", raw.detail)
        assertEquals(Kind.UNKNOWN, kind(RuntimeException()))
        assertEquals(Kind.UNKNOWN, kind(RuntimeException("   ")))
        assertEquals(Kind.UNKNOWN, kind(RuntimeException("java.lang.NullPointerException: x")))
        assertEquals(Kind.UNKNOWN, kind(IllegalStateException("kotlin.UninitializedPropertyAccessException: y")))
    }

    @Test fun shortPath() {
        assertEquals("/forum/index.php?act=fav", ErrorMessages.shortPath("https://4pda.to/forum/index.php?act=fav"))
        assertEquals("", ErrorMessages.shortPath("https://4pda.to"))
        val long = ErrorMessages.shortPath("https://4pda.to/" + "a".repeat(300))
        assertEquals(120, long.length)
        assertTrue(long.endsWith("…"))
    }

    @Test fun waitFromForumText() {
        assertEquals(30L, ErrorMessages.waitSecondsFromText("Искать можно не чаще, чем раз в 30 секунд"))
        assertEquals(120L, ErrorMessages.waitSecondsFromText("Подождите 2 минуты"))
        assertNull(ErrorMessages.waitSecondsFromText("Неверный пароль"))
        assertNull(ErrorMessages.waitSecondsFromText(null))
    }

    @Test fun exceptionDump() {
        assertTrue(ErrorMessages.isExceptionDump("java.io.IOException: closed"))
        assertTrue(ErrorMessages.isExceptionDump("okhttp3.internal.http2.StreamResetException: x"))
        assertFalse(ErrorMessages.isExceptionDump("Тема закрыта для ответов"))
    }
}

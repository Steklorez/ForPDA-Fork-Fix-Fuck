package forpdateam.ru.forpda.client

import android.content.SharedPreferences
import okhttp3.Authenticator
import okhttp3.Credentials
import java.io.IOException
import java.net.InetSocketAddress
import java.net.PasswordAuthentication
import java.net.Proxy
import java.net.ProxySelector
import java.net.SocketAddress
import java.net.URI

/**
 * User proxy from Settings → Network. Read on every connection, so switching it on or off
 * takes effect without restarting the app. Off by default: then everything goes direct.
 *
 * Used by the forum client (OkHttp) and, through the process-wide defaults, by the image loader.
 */
object AppProxy {

    const val KEY_ENABLED = "net.proxy.enabled"
    const val KEY_TYPE = "net.proxy.type"
    const val KEY_HOST = "net.proxy.host"
    const val KEY_PORT = "net.proxy.port"
    const val KEY_USER = "net.proxy.user"
    const val KEY_PASSWORD = "net.proxy.password"

    const val TYPE_HTTP = "HTTP"
    const val TYPE_SOCKS = "SOCKS"

    private lateinit var prefs: SharedPreferences

    @JvmStatic
    fun init(preferences: SharedPreferences) {
        prefs = preferences
        ProxySelector.setDefault(selector)
        // SOCKS credentials and HttpURLConnection (image loader) go through the JVM authenticator.
        java.net.Authenticator.setDefault(object : java.net.Authenticator() {
            override fun getPasswordAuthentication(): PasswordAuthentication? {
                // Java's SOCKS client asks with requestorType SERVER and protocol "SOCKS5",
                // so both that and a real proxy request get the saved login.
                val socks = requestingProtocol?.startsWith("SOCKS", ignoreCase = true) == true
                if (requestorType != RequestorType.PROXY && !socks) return null
                val user = user() ?: return null
                return PasswordAuthentication(user, password().toCharArray())
            }
        })
    }

    private fun current(): Proxy? {
        if (!::prefs.isInitialized || !prefs.getBoolean(KEY_ENABLED, false)) return null
        val host = prefs.getString(KEY_HOST, null)?.trim().orEmpty()
        val port = prefs.getString(KEY_PORT, null)?.trim()?.toIntOrNull() ?: return null
        if (host.isEmpty() || port !in 1..65535) return null
        val type = if (prefs.getString(KEY_TYPE, TYPE_HTTP) == TYPE_SOCKS) Proxy.Type.SOCKS else Proxy.Type.HTTP
        return Proxy(type, InetSocketAddress.createUnresolved(host, port))
    }

    private fun user(): String? = prefs.getString(KEY_USER, null)?.trim()?.takeIf { it.isNotEmpty() }

    private fun password(): String = prefs.getString(KEY_PASSWORD, null).orEmpty()

    @JvmField
    val selector: ProxySelector = object : ProxySelector() {
        override fun select(uri: URI?): List<Proxy> = listOf(current() ?: Proxy.NO_PROXY)

        override fun connectFailed(uri: URI?, sa: SocketAddress?, ioe: IOException?) {}
    }

    /** Answers an HTTP proxy's 407 with the saved login. Without a login there is nothing to answer. */
    @JvmField
    val authenticator = Authenticator { _, response ->
        val user = user()
        if (user == null || response.request().header("Proxy-Authorization") != null) {
            null
        } else {
            response.request().newBuilder()
                .header("Proxy-Authorization", Credentials.basic(user, password()))
                .build()
        }
    }
}

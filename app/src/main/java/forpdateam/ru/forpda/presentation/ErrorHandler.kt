package forpdateam.ru.forpda.presentation

import forpdateam.ru.forpda.App
import forpdateam.ru.forpda.R
import forpdateam.ru.forpda.client.OnlyShowException
import forpdateam.ru.forpda.client.ServerRefusedException

/**
 * Created by radiationx on 23.02.18.
 */
class ErrorHandler(
    private val router: TabRouter
) : IErrorHandler {

    override fun handle(throwable: Throwable, messageListener: ((Throwable, String?) -> Unit)?) {
        throwable.printStackTrace()
        if (throwable is ServerRefusedException) {
            showServerRefused(throwable)
            return
        }
        // The forum's own error box ("search no more than once in N seconds" and the like):
        // a toast was gone before it could be read.
        if (throwable is OnlyShowException && !throwable.message.isNullOrBlank()) {
            router.showSystemAlert(
                App.get().getString(R.string.forum_error_title),
                throwable.message.orEmpty(),
                ErrorMessages.waitSecondsFromText(throwable.message)
            )
            return
        }
        val message = getMessage(throwable) ?: return
        if (messageListener != null) {
            messageListener.invoke(throwable, message)
        } else {
            router.showSystemMessage(message)
        }
    }

    private fun showServerRefused(ex: ServerRefusedException) {
        val app = App.get()
        val (titleRes, textRes) = when (ex.kind) {
            ServerRefusedException.Kind.TOO_MANY_REQUESTS ->
                R.string.server_refused_title_rate to R.string.server_refused_text_rate
            ServerRefusedException.Kind.CLOUDFLARE ->
                R.string.server_refused_title_cloudflare to R.string.server_refused_text_cloudflare
            ServerRefusedException.Kind.UNAVAILABLE ->
                R.string.server_refused_title_unavailable to R.string.server_refused_text_unavailable
        }
        val text = StringBuilder(app.getString(textRes))
        ex.retryAfterSeconds?.takeIf { it > 0 }?.let {
            text.append("\n\n").append(app.getString(R.string.server_refused_retry_after, (it + 59) / 60))
        }
        text.append("\n\n").append(app.getString(R.string.server_refused_details, ex.code, ex.url))
        // Countdown: the server's Retry-After, or about a minute for a 429 that did not say.
        val wait = ex.retryAfterSeconds?.takeIf { it > 0 }
            ?: if (ex.kind == ServerRefusedException.Kind.TOO_MANY_REQUESTS) RATE_LIMIT_ESTIMATE_SECONDS else null
        router.showSystemAlert(app.getString(titleRes), text.toString(), wait)
    }

    /** Text for the user, or null when there is nothing to say (a cancelled job). */
    private fun getMessage(throwable: Throwable): String? {
        val app = App.get()
        val result = ErrorMessages.classify(throwable)
        return when (result.kind) {
            ErrorMessages.Kind.SILENT -> null
            ErrorMessages.Kind.TIMEOUT -> app.getString(R.string.error_timeout)
            ErrorMessages.Kind.NO_HOST_4PDA -> app.getString(R.string.error_no_host_4pda)
            ErrorMessages.Kind.NO_HOST -> app.getString(R.string.error_no_host)
            ErrorMessages.Kind.TLS -> app.getString(R.string.error_tls)
            ErrorMessages.Kind.HTTP_401 -> app.getString(R.string.error_http_401)
            ErrorMessages.Kind.HTTP_403 -> app.getString(R.string.error_http_403)
            ErrorMessages.Kind.HTTP_404 -> app.getString(R.string.error_http_404) +
                    (result.detail?.takeIf { it.isNotEmpty() }?.let { "\n$it" } ?: "")
            ErrorMessages.Kind.HTTP_451 -> app.getString(R.string.error_http_451)
            ErrorMessages.Kind.HTTP_SERVER -> app.getString(R.string.error_http_server, result.code)
            ErrorMessages.Kind.NO_NETWORK -> app.getString(R.string.error_no_network)
            ErrorMessages.Kind.RAW -> result.detail
            ErrorMessages.Kind.UNKNOWN -> app.getString(R.string.error_unknown)
        }
    }

    companion object {
        private const val RATE_LIMIT_ESTIMATE_SECONDS = 60L
    }
}
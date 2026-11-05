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
            router.showSystemAlert(App.get().getString(R.string.forum_error_title), throwable.message.orEmpty())
            return
        }
        val message = getMessage(throwable)
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
        router.showSystemAlert(app.getString(titleRes), text.toString())
    }

    private fun getMessage(throwable: Throwable): String {
        return throwable.message.orEmpty()
    }
}
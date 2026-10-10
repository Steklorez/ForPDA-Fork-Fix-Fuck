package forpdateam.ru.forpda.notifications.push

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import forpdateam.ru.forpda.notifications.NotificationsService
import java.util.ArrayDeque

/**
 * A push from Google arrives here. We do not read what it says: the server only pokes us
 * ("something happened"), and the usual events check figures out what and shows the notification.
 * That way there is exactly one place that decides what gets shown.
 */
class PushReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != "com.google.android.c2dm.intent.RECEIVE") return
        if (!PushConfig.isPushMode(context)) return

        // Google asking us to throw the token away and get a new one.
        if (intent.getStringExtra("from") == "google.com/iid") {
            when (intent.getStringExtra("CMD")) {
                "RST", "RST_FULL", "SYNC" -> PushRegistrar.ensureRegistered(context, dropToken = true)
            }
            return
        }

        val messageId = intent.getStringExtra("google.message_id")
        if (messageId != null) {
            synchronized(seen) {
                if (seen.contains(messageId)) return
                if (seen.size >= 16) seen.remove()
                seen.add(messageId)
            }
        }
        Log.i(TAG, "push received: ${intent.extras?.keySet()?.filter { !it.startsWith("google.") && !it.startsWith("gcm.") }}")

        // Hold the process for a while: the check runs asynchronously and Android is quick to freeze
        // a receiver that returned.
        val pending = goAsync()
        NotificationsService.startAndCheck()
        Handler(Looper.getMainLooper()).postDelayed({ pending.finish() }, HOLD_MS)
    }

    companion object {
        private const val TAG = "PushReceiver"
        // Google's push broadcast is a foreground one: the system gives it 10 s, then it is an ANR.
        private const val HOLD_MS = 8_000L
        private val seen = ArrayDeque<String>(16)
    }
}

package forpdateam.ru.forpda.notifications.push

import android.content.Context
import forpdateam.ru.forpda.App
import forpdateam.ru.forpda.common.Preferences

/**
 * 4PDA's push plumbing. All of it is public: the sender id and app id are printed in the
 * official client, the server address is what the site itself hands out.
 */
object PushConfig {
    const val SENDER_ID = "1043483203481"
    const val GMP_APP_ID = "1:1043483203481:android:43c96e036dc3fe54"

    // The registration goes to a WebSocket over TLS, nothing else.
    const val HOST = "appbk.4pda.to"
    const val PORT = 443
    const val PATH = "/ws/"

    /** The server wants a client version in the hello. Any real, recent one will do. */
    const val CLIENT_VERSION = "1.9.43"

    const val PREFS = "push_state"

    // The "notifications.main.push_mode" setting: push first with the periodic check as a safety net,
    // or the periodic check alone (nothing is registered, incoming pushes are ignored).
    const val MODE_PUSH = "push"
    const val MODE_POLL = "poll"

    fun isPushMode(context: Context): Boolean =
            App.getPreferences(context)
                    .getString(Preferences.Notifications.Main.PUSH_MODE, MODE_PUSH) != MODE_POLL

    // Notification-type bits the server understands in the registration.
    const val BIT_QMS = 1
    const val BIT_QMS_SYSTEM = 2
    const val BIT_FAVORITES = 4
    const val BIT_FAVORITES_IMPORTANT = 8
    const val BIT_MENTIONS = 16
}

package forpdateam.ru.forpda.notifications.push

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import forpdateam.ru.forpda.App
import forpdateam.ru.forpda.common.Preferences
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/**
 * Keeps 4PDA's server aware of this device: get an FCM token from Google, then tell the server
 * "user N, this token, these notification kinds". After that the server pokes us through
 * Firebase whenever something happens, and [PushReceiver] wakes the usual events check.
 *
 * Idempotent and cheap to call: it does nothing when the same token/user/kinds were already
 * accepted recently. Not logged in, or no Google Play Services: silently does nothing, the
 * periodic check keeps working as before.
 */
object PushRegistrar {

    private const val TAG = "PushRegistrar"
    private val TOKEN_MAX_AGE = TimeUnit.DAYS.toMillis(7)
    private val REREGISTER_AFTER = TimeUnit.DAYS.toMillis(3)

    private val running = java.util.concurrent.atomic.AtomicBoolean(false)

    @JvmStatic
    @JvmOverloads
    fun ensureRegistered(context: Context, dropToken: Boolean = false) {
        val app = context.applicationContext
        if (!PushConfig.isPushMode(app)) return
        if (!running.compareAndSet(false, true)) return
        val userId = app.let { App.getPreferences(it).getString(Preferences.Auth.USER_ID, "0")?.toIntOrNull() ?: 0 }
        val passHash = passHash(app)
        if (userId <= 0 || passHash == null) {
            running.set(false)
            return
        }
        val prefs = app.getSharedPreferences(PushConfig.PREFS, Context.MODE_PRIVATE)
        if (dropToken) prefs.edit().remove("token").remove("token_time").remove("registered").apply()

        val token = prefs.getString("token", null)
        val tokenAge = System.currentTimeMillis() - prefs.getLong("token_time", 0)
        if (token != null && tokenAge < TOKEN_MAX_AGE) {
            sendToServer(app, userId, passHash, token)
            return
        }
        // GmsTokenClient binds a service and wants the main thread for its callback.
        Handler(Looper.getMainLooper()).post {
            GmsTokenClient(app).request { newToken, error ->
                if (newToken == null) {
                    Log.w(TAG, "no token: $error")
                    running.set(false)
                    return@request
                }
                prefs.edit().putString("token", newToken).putLong("token_time", System.currentTimeMillis()).apply()
                sendToServer(app, userId, passHash, newToken)
            }
        }
    }

    private fun sendToServer(app: Context, userId: Int, passHash: String, token: String) {
        val prefs = app.getSharedPreferences(PushConfig.PREFS, Context.MODE_PRIVATE)
        val mask = typeMask()
        val state = "$userId:$mask:${token.hashCode()}"
        val recently = System.currentTimeMillis() - prefs.getLong("registered_time", 0) < REREGISTER_AFTER
        if (prefs.getString("registered", null) == state && recently) {
            running.set(false)
            return
        }
        thread(name = "push-register", isDaemon = true) {
            try {
                val error = PushRegistration.register(userId, passHash, token, mask)
                if (error == null) {
                    prefs.edit().putString("registered", state).putLong("registered_time", System.currentTimeMillis()).apply()
                    Log.i(TAG, "registered, mask=$mask")
                } else {
                    Log.w(TAG, "server said no: $error")
                }
            } finally {
                running.set(false)
            }
        }
    }

    private fun typeMask(): Int {
        val holder = App.get().Di().notificationPreferencesHolder
        var mask = 0
        if (!holder.getMainEnabled()) return mask
        if (holder.getQmsEnabled()) mask = mask or PushConfig.BIT_QMS or PushConfig.BIT_QMS_SYSTEM
        if (holder.getFavEnabled()) mask = mask or PushConfig.BIT_FAVORITES
        if (holder.getFavOnlyImportant()) mask = mask or PushConfig.BIT_FAVORITES_IMPORTANT
        if (holder.getMentionsEnabled()) mask = mask or PushConfig.BIT_MENTIONS
        return mask
    }

    /** The forum's own pass_hash cookie, kept by the HTTP client as "url|:|pass_hash=...; ...". */
    private fun passHash(context: Context): String? {
        val raw = App.getPreferences(context).getString("cookie_pass_hash", null) ?: return null
        return Regex("pass_hash=([0-9a-f]{32})").find(raw)?.groupValues?.get(1)
    }
}

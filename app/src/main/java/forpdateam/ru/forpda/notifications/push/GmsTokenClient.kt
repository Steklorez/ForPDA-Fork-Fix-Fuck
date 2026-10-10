package forpdateam.ru.forpda.notifications.push

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.os.Parcel
import android.util.Base64
import android.util.Log
import java.security.SecureRandom

/**
 * Asks Google Play Services for an FCM registration token, no Firebase SDK involved.
 *
 * 4PDA's own push goes through their Firebase project, and the token for it can be pulled out
 * of GMS by anyone who knows the project number (it is written in plain text in their APK).
 * The official client does exactly this with a hand-rolled request, and so do we: bind to the
 * GMS registration service, throw a Messenger at it, get "registration_id" back.
 *
 * One request per instance. The callback fires exactly once, on the main thread.
 */
class GmsTokenClient(private val context: Context) {

    private val main = Handler(Looper.getMainLooper())
    private val requestId = (1000..16000).random()
    private var callback: ((String?, String?) -> Unit)? = null
    private var connection: ServiceConnection? = null

    private val replyMessenger = Messenger(object : Handler(Looper.getMainLooper()) {
        override fun handleMessage(msg: Message) {
            val data = msg.data
            val body = data?.getBundle("data")
            when {
                data?.getBoolean("unsupported", false) == true -> finish(null, "GMS: request not supported")
                body == null -> finish(null, "GMS: empty reply")
                !body.getString("registration_id").isNullOrEmpty() -> finish(body.getString("registration_id"), null)
                else -> finish(null, body.getString("error") ?: "GMS: no registration_id")
            }
        }
    })

    private val timeout = Runnable { finish(null, "GMS: timed out") }

    fun request(done: (token: String?, error: String?) -> Unit) {
        callback = done
        val gmsVersion = try {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(GMS_PACKAGE, 0).versionCode
        } catch (ex: Exception) {
            finish(null, "Google Play Services not installed")
            return
        }
        val conn = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, binder: IBinder) {
                try {
                    send(binder, buildRequest(gmsVersion))
                } catch (ex: Exception) {
                    finish(null, "GMS: send failed: $ex")
                }
            }

            override fun onServiceDisconnected(name: ComponentName) {
                finish(null, "GMS: service disconnected")
            }
        }
        connection = conn
        main.postDelayed(timeout, 30_000L)
        val intent = Intent("com.google.android.c2dm.intent.REGISTER").setPackage(GMS_PACKAGE)
        val bound = try {
            context.bindService(intent, conn, Context.BIND_AUTO_CREATE)
        } catch (ex: Exception) {
            false
        }
        if (!bound) finish(null, "GMS: cannot bind registration service")
    }

    private fun buildRequest(gmsVersion: Int): Bundle {
        @Suppress("DEPRECATION")
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        return Bundle().apply {
            putString("scope", "*")
            putString("sender", PushConfig.SENDER_ID)
            putString("subtype", PushConfig.SENDER_ID)
            putString("appid", instanceId())
            putString("gmp_app_id", PushConfig.GMP_APP_ID)
            putString("gmsv", gmsVersion.toString())
            putString("osv", Build.VERSION.SDK_INT.toString())
            @Suppress("DEPRECATION")
            putString("app_ver", info.versionCode.toString())
            putString("app_ver_name", info.versionName)
            putString("cliv", "fiid-20.0.2")
        }
    }

    private fun send(binder: IBinder, request: Bundle) {
        val message = Message.obtain().apply {
            what = 1
            arg1 = requestId
            replyTo = replyMessenger
            data = Bundle().apply {
                putBoolean("oneWay", false)
                putString("pkg", context.packageName)
                putBundle("data", request)
            }
        }
        when (val descriptor = binder.interfaceDescriptor) {
            "android.os.IMessenger" -> Messenger(binder).send(message)
            "com.google.android.gms.iid.IMessengerCompat" -> {
                // Old GMS speaks its own binder interface instead of a plain Messenger.
                val parcel = Parcel.obtain()
                try {
                    parcel.writeInterfaceToken(descriptor)
                    parcel.writeInt(1)
                    message.writeToParcel(parcel, 0)
                    binder.transact(1, parcel, null, IBinder.FLAG_ONEWAY)
                } finally {
                    parcel.recycle()
                }
            }
            else -> throw IllegalStateException("unknown GMS binder $descriptor")
        }
    }

    /** Random per-install id GMS uses to tell instances apart. Same shape the official client makes. */
    private fun instanceId(): String {
        val prefs = context.getSharedPreferences(PushConfig.PREFS, Context.MODE_PRIVATE)
        prefs.getString("iid", null)?.let { return it }
        val bytes = ByteArray(8).also { SecureRandom().nextBytes(it) }
        bytes[0] = ((bytes[0].toInt() and 15) + 112).toByte()
        val id = Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
        prefs.edit().putString("iid", id).apply()
        return id
    }

    private fun finish(token: String?, error: String?) {
        val cb = callback ?: return
        callback = null
        main.removeCallbacks(timeout)
        try {
            connection?.let { context.unbindService(it) }
        } catch (ignore: Exception) {
        }
        connection = null
        if (token == null) Log.w(TAG, "token request failed: $error")
        cb(token, error)
    }

    private fun IntRange.random() = SecureRandom().nextInt(last - first + 1) + first

    companion object {
        private const val TAG = "GmsTokenClient"
        private const val GMS_PACKAGE = "com.google.android.gms"
    }
}

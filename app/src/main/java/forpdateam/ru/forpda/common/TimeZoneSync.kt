package forpdateam.ru.forpda.common

import android.content.Context
import android.util.Log
import forpdateam.ru.forpda.App
import forpdateam.ru.forpda.entity.common.AuthState
import forpdateam.ru.forpda.model.data.remote.api.NetworkRequest
import java.util.TimeZone
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

/**
 * The forum prints every date in the time zone of the profile ("Settings of the forum"), and the app
 * shows those strings as they are. A phone in another zone than the profile sees shifted times.
 *
 * Once per (user, device UTC offset) this copies the device offset into the profile and remembers that
 * it did, so the forum settings are not touched again until the account or the device zone changes.
 * Every other setting of that form is sent back exactly as it was read.
 */
object TimeZoneSync {

    private const val TAG = "TimeZoneSync"
    private const val PREF_DONE = "tz_sync_done"
    private const val PREF_TRY_TIME = "tz_sync_try_time"
    private const val URL_FORM = "https://4pda.to/forum/index.php?act=UserCP&CODE=04"
    private const val URL_SAVE = "https://4pda.to/forum/index.php?act=usercp&action=forum-settings"
    private val RETRY_AFTER = TimeUnit.HOURS.toMillis(6)

    private val running = AtomicBoolean(false)

    private val formPattern = Regex("<form[^>]*forum-settings[^>]*>([\\s\\S]*?)</form>")
    private val inputPattern = Regex("<input\\b[^>]*>")
    private val selectPattern = Regex("<select\\b([^>]*)>([\\s\\S]*?)</select>")
    private val optionPattern = Regex("<option\\b([^>]*)>")
    private val attrPattern = Regex("([\\w-]+)=\"([^\"]*)\"")
    private val checkedPattern = Regex("\\schecked(?:[\\s=>/]|$)")
    private val selectedPattern = Regex("\\sselected(?:[\\s=>/]|$)")

    @JvmStatic
    fun ensureSynced(context: Context) {
        val prefs = App.getPreferences(context.applicationContext)
        val auth = App.get().Di().authHolder.get()
        if (auth.state != AuthState.AUTH) return

        val state = "${auth.userId}:${deviceOffsetMinutes()}"
        if (prefs.getString(PREF_DONE, null) == state) return
        if (System.currentTimeMillis() - prefs.getLong(PREF_TRY_TIME, 0) < RETRY_AFTER &&
            prefs.getString(PREF_TRY_TIME + "_state", null) == state
        ) return
        if (!running.compareAndSet(false, true)) return

        prefs.edit().putLong(PREF_TRY_TIME, System.currentTimeMillis()).putString(PREF_TRY_TIME + "_state", state).apply()
        thread(name = "tz-sync", isDaemon = true) {
            try {
                if (sync(deviceOffsetMinutes())) {
                    prefs.edit().putString(PREF_DONE, state).apply()
                }
            } catch (ex: Exception) {
                Log.w(TAG, "sync failed: $ex")
            } finally {
                running.set(false)
            }
        }
    }

    /** Current offset, daylight saving of the device included. */
    private fun deviceOffsetMinutes(): Int =
        TimeZone.getDefault().getOffset(System.currentTimeMillis()) / 60000

    private class Form(val params: LinkedHashMap<String, String>, val offsets: List<String>)

    private fun sync(offsetMinutes: Int): Boolean {
        val client = App.get().Di().webClient
        val form = parse(client.get(URL_FORM).body) ?: run {
            Log.w(TAG, "forum settings form not found")
            return false
        }
        val hours = offsetMinutes / 60.0
        val target = form.offsets.minByOrNull { Math.abs(it.toDouble() - hours) } ?: return false

        val alreadyRight = form.params["time-offset"] == target &&
                !form.params.containsKey("dst-in-use") && !form.params.containsKey("tz-autoset")
        if (!alreadyRight) {
            val params = LinkedHashMap(form.params)
            params["time-offset"] = target
            params.remove("dst-in-use")
            params.remove("tz-autoset")
            client.request(NetworkRequest.Builder().url(URL_SAVE).formHeaders(params).build())

            val check = parse(client.get(URL_FORM).body)?.params
            if (check == null || check["time-offset"] != target || check.containsKey("dst-in-use")) {
                Log.w(TAG, "profile time zone did not change to $target")
                return false
            }
            Log.i(TAG, "profile time zone set to GMT $target")
        }
        return true
    }

    private fun parse(html: String): Form? {
        val body = formPattern.find(html)?.groupValues?.get(1) ?: return null
        val params = LinkedHashMap<String, String>()
        var offsets = emptyList<String>()

        inputPattern.findAll(body).forEach { match ->
            val tag = match.value
            val attrs = attributes(tag)
            val name = attrs["name"] ?: return@forEach
            when (attrs["type"]?.lowercase()) {
                "submit", "button", "image", "file", "reset" -> {}
                "checkbox", "radio" -> if (checkedPattern.containsMatchIn(tag)) params[name] = attrs["value"] ?: "on"
                else -> params[name] = unescape(attrs["value"] ?: "")
            }
        }

        selectPattern.findAll(body).forEach { match ->
            val name = attributes(match.groupValues[1])["name"] ?: return@forEach
            val options = optionPattern.findAll(match.groupValues[2]).map { it.groupValues[1] }.toList()
            val chosen = options.firstOrNull { selectedPattern.containsMatchIn(" $it") } ?: options.firstOrNull()
            chosen?.let { params[name] = unescape(attributes(it)["value"] ?: "") }
            if (name == "time-offset") {
                offsets = options.mapNotNull { attributes(it)["value"] }
            }
        }

        if (offsets.isEmpty() || !params.containsKey("time-offset")) return null
        return Form(params, offsets)
    }

    private fun attributes(tag: String): Map<String, String> =
        attrPattern.findAll(tag).associate { it.groupValues[1].lowercase() to it.groupValues[2] }

    private fun unescape(value: String): String =
        value.replace("&amp;", "&").replace("&quot;", "\"").replace("&lt;", "<").replace("&gt;", ">")
}

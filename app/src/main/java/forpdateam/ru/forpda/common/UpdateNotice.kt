package forpdateam.ru.forpda.common

import com.jakewharton.rxrelay2.BehaviorRelay
import forpdateam.ru.forpda.App
import forpdateam.ru.forpda.BuildConfig
import io.reactivex.Observable
import java.util.concurrent.TimeUnit

/**
 * Remembers the newest version the update check has seen, so the app can show a small red dot
 * (on the menu button, Settings and "Check for updates") without asking the server every time.
 * The automatic check runs at most once a day; the dot goes away once that version is installed.
 */
object UpdateNotice {

    private const val KEY_LAST_CHECK = "update.last_check"
    private const val KEY_AVAILABLE_CODE = "update.available_code"
    private val CHECK_EVERY = TimeUnit.DAYS.toMillis(1)

    private val prefs get() = App.get().preferences

    private val relay = BehaviorRelay.createDefault(hasUpdate())

    fun observe(): Observable<Boolean> = relay.distinctUntilChanged()

    fun hasUpdate(): Boolean = prefs.getInt(KEY_AVAILABLE_CODE, 0) > BuildConfig.VERSION_CODE

    /** True when the automatic check is due: never done, or more than a day ago. */
    fun isCheckDue(): Boolean =
        System.currentTimeMillis() - prefs.getLong(KEY_LAST_CHECK, 0) >= CHECK_EVERY

    fun onChecked(availableCode: Int) {
        prefs.edit()
            .putLong(KEY_LAST_CHECK, System.currentTimeMillis())
            .putInt(KEY_AVAILABLE_CODE, availableCode)
            .apply()
        relay.accept(hasUpdate())
    }
}

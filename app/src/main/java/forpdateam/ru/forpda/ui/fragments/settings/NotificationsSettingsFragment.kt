package forpdateam.ru.forpda.ui.fragments.settings

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.preference.ListPreference
import forpdateam.ru.forpda.R
import forpdateam.ru.forpda.common.Preferences
import forpdateam.ru.forpda.notifications.push.PushConfig
import forpdateam.ru.forpda.notifications.push.PushRegistrar
import forpdateam.ru.forpda.ui.activities.SettingsActivity

/**
 * Created by radiationx on 12.07.17.
 */

class NotificationsSettingsFragment : BaseSettingFragment() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        addPreferencesFromResource(R.xml.preferences_notifications)
        findPreference<ListPreference>(Preferences.Notifications.Main.PUSH_MODE)?.setOnPreferenceChangeListener { _, value ->
            if (value == PushConfig.MODE_PUSH) {
                // The preference is saved only after this returns, so register once it has landed.
                Handler(Looper.getMainLooper()).post { PushRegistrar.ensureRegistered(requireContext()) }
            }
            true
        }
        // The registration carries the notification kinds, so a change of any of them has to reach the server.
        REGISTRATION_KEYS.forEach { key ->
            findPreference<androidx.preference.Preference>(key)?.setOnPreferenceChangeListener { _, _ ->
                Handler(Looper.getMainLooper()).post { PushRegistrar.ensureRegistered(requireContext()) }
                true
            }
        }
        (activity as? SettingsActivity)?.supportActionBar?.title = preferenceScreen.title
    }

    companion object {
        const val PREFERENCE_SCREEN_NAME = "notifications"

        private val REGISTRATION_KEYS = listOf(
                "notifications.main.enabled",
                "notifications.fav.enabled",
                "notifications.fav.only_important",
                "notifications.mentions.enabled",
                "notifications.qms.enabled"
        )
    }
}

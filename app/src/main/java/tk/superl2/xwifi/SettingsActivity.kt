package tk.superl2.xwifi

import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import android.preference.Preference
import android.preference.PreferenceActivity
import android.preference.PreferenceFragment
import android.support.v7.app.AppCompatActivity
import android.support.v7.app.AppCompatDelegate
import android.widget.Toast


/**
 * A [PreferenceActivity] that presents a set of application settings. On
 * handset devices, settings are presented as a single list. On tablets,
 * settings are split by category, with category headers shown to the left of
 * the list of settings.
 *
 * See [Android Design: Settings](http://developer.android.com/design/patterns/settings.html)
 * for design guidelines and the [Settings API Guide](http://developer.android.com/guide/topics/ui/settings.html)
 * for more information on developing a Settings UI.
 */
class SettingsActivity: AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Display the fragment as the main context
        fragmentManager.beginTransaction().replace(android.R.id.content, SettingsFragment()).commit()
    }
}

class SettingsFragment: PreferenceFragment() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Load the preferences from an XML resource
        addPreferencesFromResource(R.xml.preferences)

        findPreference("theme").setOnPreferenceChangeListener { _, newValue ->
            AppCompatDelegate.setDefaultNightMode(getThemeFromPreferenceString(newValue as String))
            Toast.makeText(activity.applicationContext, getString(R.string.theme_restart_message), Toast.LENGTH_SHORT).show()
            activity.recreate()
            true
        }
    }
}
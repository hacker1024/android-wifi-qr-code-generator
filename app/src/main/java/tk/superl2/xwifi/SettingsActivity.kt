package tk.superl2.xwifi

import android.os.Bundle
import android.preference.PreferenceFragment
import android.preference.PreferenceManager
import android.support.v7.app.AppCompatActivity
import android.support.v7.app.AppCompatDelegate
import android.widget.Toast

internal const val DEFAULT_QR_CODE_RESOLUTION = "300"
internal const val DEFAULT_SORTING_ORDER = true

class SettingsActivity: AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        PreferenceManager.setDefaultValues(this, R.xml.preferences, false)

        if (intent.extras.getBoolean("xposed")) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        } else {
            setThemeFromSharedPrefs(PreferenceManager.getDefaultSharedPreferences(this))
        }

        super.onCreate(savedInstanceState)

        // Display the fragment as the main context
        fragmentManager.beginTransaction().replace(android.R.id.content, if (intent.extras.getBoolean("xposed")) XposedSettingsFragment() else SettingsFragment()).commit()
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

class XposedSettingsFragment: PreferenceFragment() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        addPreferencesFromResource(R.xml.xposed_preferences)
    }
}
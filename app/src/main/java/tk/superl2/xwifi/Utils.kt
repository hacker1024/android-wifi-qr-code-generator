package tk.superl2.xwifi

import android.content.SharedPreferences
import android.graphics.Color
import android.os.Build
import android.support.v7.app.AppCompatActivity
import android.support.v7.app.AppCompatDelegate
import android.util.Log
import android.view.WindowManager

private const val TAG = "Utils"

// Sets the theme based on the value in the shared preferences (light/dark)
fun setThemeFromSharedPrefs(prefs: SharedPreferences) {
    AppCompatDelegate.setDefaultNightMode(getThemeFromPreferenceString(prefs.getString("theme", "Light")))
}

// Returns night mode constant from string
fun getThemeFromPreferenceString(value: String) =
        when (value) {
            "Light" -> AppCompatDelegate.MODE_NIGHT_NO
            "Dark" -> AppCompatDelegate.MODE_NIGHT_YES
            "System" -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            else -> {
                Log.w(TAG, "Invalid theme set in shared preferences! Using system theme.")
                AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            }
        }
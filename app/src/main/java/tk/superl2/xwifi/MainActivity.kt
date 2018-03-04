package tk.superl2.xwifi

import android.app.Activity
import android.os.Bundle
import android.util.Log
import android.widget.Button

const val TAG = "MainActivity"

class MainActivity: Activity() {
    // This variable holds an ArrayList of WifiEntry objects that each contain a saved wifi SSID and
    // password. It is updated whenever focus returns to the app (onResume).
    lateinit var wifiEntries: ArrayList<WifiEntry>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        loadWifiEntries()

        findViewById<Button>(R.id.button).setOnClickListener {
            Log.v(TAG, "Displaying wifi entries:")
            for (wifiEntry in wifiEntries) {
                Log.v(TAG, "Wifi SSID: ${wifiEntry.title}")
                Log.v(TAG, "Wifi Password: ${wifiEntry.getPassword(true)}")
            }
        }
    }

    // This function saves wifi entry data into the wifiEntries ArrayList.
    private fun loadWifiEntries() {
        Log.v(TAG, "Loading wifi entries...")
        wifiEntries = WifiEntryLoader().readOreoFile()
        Log.v(TAG, "Wifi entries loaded.")
    }
}

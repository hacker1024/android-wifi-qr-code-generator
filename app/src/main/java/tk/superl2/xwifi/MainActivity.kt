package tk.superl2.xwifi

import android.app.Activity
import android.app.AlertDialog
import android.app.Dialog
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.Toast

const val TAG = "MainActivity"

class MainActivity: Activity() {
    // This variable holds an ArrayList of WifiEntry objects that each contain a saved wifi SSID and
    // password. It is updated whenever focus returns to the app (onResume).
    private var wifiEntries: ArrayList<WifiEntry>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        findViewById<Button>(R.id.button).setOnClickListener {
            Log.v(TAG, "Displaying wifi entries:")
            if (wifiEntries != null) {
                for (wifiEntry in wifiEntries!!) {
                    Log.v(TAG, "Wifi SSID: ${wifiEntry.title}")
                    Log.v(TAG, "Wifi Password: ${wifiEntry.getPassword(true)}")
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        loadWifiEntries()
    }

    // This function saves wifi entry data into the wifiEntries ArrayList.
    private lateinit var errorDialogBuilder: AlertDialog.Builder
    private lateinit var errorDialog: AlertDialog
    private fun loadWifiEntries() {
        Log.v(TAG, "Loading wifi entries...")
        wifiEntries = WifiEntryLoader.readOreoFile()
        if (wifiEntries == null) {
            if (!::errorDialogBuilder.isInitialized) {
                errorDialogBuilder = AlertDialog.Builder(this)
                errorDialogBuilder.setCancelable(false)
                errorDialogBuilder.setTitle(R.string.error_dialog_title)
                errorDialogBuilder.setMessage(R.string.error_dialog_message)
                errorDialogBuilder.setNeutralButton("RETRY", { dialog, which ->
                    dialog.dismiss()
                    loadWifiEntries()
                })
                errorDialogBuilder.setNegativeButton("EXIT", { dialog, which ->
                    dialog.dismiss()
                    finishAndRemoveTask()
                })
            }
            errorDialog = errorDialogBuilder.create()
            errorDialog.show()
            Log.v(TAG, "Wifi entries failed to load.")
        } else {
            Log.v(TAG, "Wifi entries loaded.")
        }
    }

    public override fun onStop() {
        super.onStop()
        if (::errorDialog.isInitialized) errorDialog.dismiss()
    }
}
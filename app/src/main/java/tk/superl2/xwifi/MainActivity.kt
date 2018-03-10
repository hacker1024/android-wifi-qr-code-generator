package tk.superl2.xwifi

import android.app.Activity
import android.app.AlertDialog
import android.app.Dialog
import android.os.AsyncTask
import android.os.Bundle
import android.util.Log
import android.widget.*

const val TAG = "MainActivity"

class MainActivity: Activity() {
    // ListView reference object
    private lateinit var mWifiListView: ListView

    // This variable holds an ArrayList of WifiEntry objects that each contain a saved wifi SSID and
    // password. It is updated whenever focus returns to the app (onResume).
    private lateinit var wifiEntries: ArrayList<WifiEntry>
    private val wifiEntrySSIDs: ArrayList<String> = ArrayList()
    private val wifiEntryPasswords: ArrayList<String> = ArrayList()
    private val loadWifiEntriesInBackgroundTask = LoadWifiEntriesInBackground()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        mWifiListView = findViewById(R.id.wifi_ListView)
        mWifiListView.adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, wifiEntrySSIDs)
    }

    override fun onResume() {
        super.onResume()
        loadWifiEntriesInBackgroundTask.execute()
    }

    public override fun onStop() {
        super.onStop()
        if (::errorDialog.isInitialized) errorDialog.dismiss()
        loadWifiEntriesInBackgroundTask.cancel(true)
        if (::loadingDialog.isInitialized) loadingDialog.dismiss()
    }

    // This task loads the wifi entries in the background, and notifies the list adapter that the data has changed.
    lateinit var loadingDialog: Dialog
    private inner class LoadWifiEntriesInBackground: AsyncTask<Unit, Unit, Unit>() {
        override fun onPreExecute() {
            val loadingDialogBuilder = AlertDialog.Builder(this@MainActivity)
            loadingDialogBuilder.setCancelable(false)
            loadingDialogBuilder.setMessage(R.string.wifi_loading_message)
            loadingDialogBuilder.setView(ProgressBar(this@MainActivity))
            loadingDialog = loadingDialogBuilder.create()
            loadingDialog.show()
        }
        override fun doInBackground(vararg params: Unit?) {
            loadWifiEntries()
        }
        override fun onPostExecute(result: Unit?) {
            (mWifiListView.adapter as ArrayAdapter<*>).notifyDataSetChanged()
            loadingDialog.dismiss()
        }
    }

    // This function saves wifi entry data into the wifiEntries ArrayList. It also throws up a dialog if the loading fails.
    private lateinit var errorDialogBuilder: AlertDialog.Builder
    private lateinit var errorDialog: AlertDialog
    private fun loadWifiEntries() {
        Log.v(TAG, "Loading wifi entries...")
        if (::wifiEntries.isInitialized) wifiEntries.clear()
        wifiEntrySSIDs.clear()
        wifiEntryPasswords.clear()
        try {
            wifiEntries = WifiEntryLoader.readOreoFile()
            wifiEntries.mapTo(wifiEntrySSIDs) { it.title }
            wifiEntries.mapTo(wifiEntryPasswords) { it.getPassword(true) }
            Log.v(TAG, "Wifi entries loaded.")
        } catch(e: WifiUnparseableException) {
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
        }
    }
}
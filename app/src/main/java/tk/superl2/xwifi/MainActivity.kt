package tk.superl2.xwifi

import android.app.Activity
import android.app.AlertDialog
import android.app.Dialog
import android.os.AsyncTask
import android.os.Bundle
import android.text.Html
import android.util.Log
import android.widget.*
import net.glxn.qrgen.android.QRCode
import net.glxn.qrgen.core.scheme.Wifi

const val TAG = "MainActivity"
const val QR_GENERATION_RESOLUTION = 300

class MainActivity: Activity() {
    // ListView reference object
    private lateinit var mWifiListView: ListView

    // This variable holds an ArrayList of WifiEntry objects that each contain a saved wifi SSID and
    // password. It is updated whenever focus returns to the app (onResume).
    private lateinit var wifiEntries: ArrayList<WifiEntry>
    private val wifiEntrySSIDs = ArrayList<String>()

    private lateinit var loadWifiEntriesInBackgroundTask: LoadWifiEntriesInBackground

    lateinit var qrDialog: AlertDialog
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        mWifiListView = findViewById(R.id.wifi_ListView)
        mWifiListView.adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, wifiEntrySSIDs)
        mWifiListView.setOnItemClickListener { parent, view, position, id ->
            val qrCodeView = ImageView(this)
            qrCodeView.setPadding(0, 0, 0, -60)
            qrCodeView.adjustViewBounds = true
            qrCodeView.setImageBitmap(QRCode
                    .from(Wifi()
                        .withSsid(wifiEntrySSIDs[position])
                        .withPsk(wifiEntries[position].getPassword(true))
                        .withAuthentication(wifiEntries[position].type.asQRCodeAuth))
                    .withSize(QR_GENERATION_RESOLUTION, QR_GENERATION_RESOLUTION)
                    .bitmap())

            val builder = AlertDialog.Builder(this)
            builder.setView(qrCodeView)
            builder.setPositiveButton("DONE") {dialog, which -> dialog.dismiss()}
            qrDialog = builder.create()
            qrDialog.show()
        }
        mWifiListView.setOnItemLongClickListener { parent, view, position, id ->
            val builder = AlertDialog.Builder(this)
            builder.setMessage(Html.fromHtml(
                    "<b>SSID</b>: ${wifiEntrySSIDs[position]}<br>" +
                    (if (wifiEntries[position].getPassword(true) != "") "<b>Password</b>: ${wifiEntries[position].getPassword(true)}<br>" else {""}) +
                    "<b>Type</b>: ${wifiEntries[position].type}",
                    Html.FROM_HTML_MODE_LEGACY))
            builder.setPositiveButton("DONE") {dialog, which -> dialog.dismiss()}
            qrDialog = builder.create()
            qrDialog.show()
            true
        }
    }

    override fun onResume() {
        super.onResume()
        loadWifiEntriesInBackgroundTask = LoadWifiEntriesInBackground()
        loadWifiEntriesInBackgroundTask.execute()
    }

    public override fun onStop() {
        super.onStop()
        if (::errorDialog.isInitialized) errorDialog.dismiss()
        loadWifiEntriesInBackgroundTask.cancel(true)
        if (::loadingDialog.isInitialized) loadingDialog.dismiss()
        if (::qrDialog.isInitialized) qrDialog.dismiss()
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
        try {
            wifiEntries = WifiEntryLoader.readOreoFile()
            wifiEntries.mapTo(wifiEntrySSIDs) { it.title }
            Log.v(TAG, "Wifi entries loaded.")
        } catch(e: WifiUnparseableException) {
            if (!::errorDialogBuilder.isInitialized) {
                errorDialogBuilder = AlertDialog.Builder(this)
                errorDialogBuilder.setCancelable(false)
                errorDialogBuilder.setTitle(R.string.error_dialog_title)
                errorDialogBuilder.setMessage(R.string.error_dialog_message)
                errorDialogBuilder.setNeutralButton("RETRY", { dialog, which ->
                    runOnUiThread {
                        dialog.dismiss()
                    }
                    loadWifiEntries()
                })
                errorDialogBuilder.setNegativeButton("EXIT", { dialog, which ->
                    runOnUiThread {
                        dialog.dismiss()
                    }
                    finishAndRemoveTask()
                })
            }
            runOnUiThread {
                errorDialog = errorDialogBuilder.create()
                errorDialog.show()
            }
            Log.v(TAG, "Wifi entries failed to load.")
        }
    }
}
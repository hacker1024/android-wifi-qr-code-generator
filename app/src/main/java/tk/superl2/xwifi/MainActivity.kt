package tk.superl2.xwifi

import android.app.AlertDialog
import android.content.Intent
import android.content.SharedPreferences
import android.os.AsyncTask
import android.os.Build
import android.os.Bundle
import android.preference.PreferenceManager
import android.support.v7.app.AppCompatActivity
import android.support.v7.app.AppCompatDelegate
import android.text.Html
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.Toast
import kotlinx.android.synthetic.main.activity_main.*
import net.glxn.qrgen.android.QRCode
import net.glxn.qrgen.core.scheme.Wifi

private const val TAG = "MainActivity"
private const val DEFAULT_QR_GENERATION_RESOLUTION = "300"

class MainActivity: AppCompatActivity() {
    // This variable holds an ArrayList of WifiEntry objects that each contain a saved wifi SSID and
    // password. It is updated whenever focus returns to the app (onResume).
    private lateinit var wifiEntries: ArrayList<WifiEntry>
    private val wifiEntrySSIDs = ArrayList<String>()
    private lateinit var loadWifiEntriesInBackgroundTask: LoadWifiEntriesInBackground
    private lateinit var prefs: SharedPreferences

    private lateinit var qrDialog: AlertDialog
    override fun onCreate(savedInstanceState: Bundle?) {
        PreferenceManager.setDefaultValues(this, R.xml.preferences, false)
        prefs = PreferenceManager.getDefaultSharedPreferences(this)

        setThemeFromSharedPrefs(prefs)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        wifi_ListView.adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, wifiEntrySSIDs)
        wifi_ListView.setOnItemClickListener { _, _, position, _ ->
            val qrCodeView = ImageView(this)
            qrCodeView.setPadding(0, 0, 0, -60)
            qrCodeView.adjustViewBounds = true
            qrCodeView.setImageBitmap(QRCode
                    .from(Wifi()
                            .withSsid(wifiEntrySSIDs[position])
                            .withPsk(wifiEntries[position].getPassword(true))
                            .withAuthentication(wifiEntries[position].type.asQRCodeAuth()))
                    .withColor((if (AppCompatDelegate.getDefaultNightMode() == AppCompatDelegate.MODE_NIGHT_NO) 0xFF000000 else 0xFFE0E0E0).toInt(), 0x00000000) //TODO Better colour handling - atm, the colours may be wrong if the theme is set to system or auto.
                    .withSize(prefs.getString("qr_code_resolution", DEFAULT_QR_GENERATION_RESOLUTION).toInt(), prefs.getString("qr_code_resolution", DEFAULT_QR_GENERATION_RESOLUTION).toInt())
                    .bitmap())

            val builder = AlertDialog.Builder(this)
            builder.setView(qrCodeView)
            builder.setPositiveButton("DONE") { dialog, _ -> dialog.dismiss() }
            qrDialog = builder.create()
            qrDialog.show()
        }
        wifi_ListView.setOnItemLongClickListener { _, _, position, _ ->
            val builder = AlertDialog.Builder(this)
            builder.setMessage(if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                Html.fromHtml(
                        "<b>SSID</b>: ${wifiEntries[position].title}<br>" +
                                (if (wifiEntries[position].getPassword(true) != "") "<b>Password</b>: ${wifiEntries[position].getPassword(true)}<br>" else { "" }) +
                                "<b>Type</b>: ${wifiEntries[position].type}",
                        Html.FROM_HTML_MODE_LEGACY)
            } else {
                @Suppress("DEPRECATION")
                Html.fromHtml(
                        "<b>SSID</b>: ${wifiEntries[position].title}<br>" +
                                (if (wifiEntries[position].getPassword(true) != "") "<b>Password</b>: ${wifiEntries[position].getPassword(true)}<br>" else { "" }) +
                                "<b>Type</b>: ${wifiEntries[position].type}"
                )
            }
            )
            builder.setPositiveButton("DONE") { dialog, _ -> dialog.dismiss() }
            qrDialog = builder.create()
            qrDialog.show()
            true
        }
    }

    override fun onRestart() {
        super.onRestart()
        recreate()
    }

    override fun onResume() {
        super.onResume()
        loadWifiEntriesInBackgroundTask = LoadWifiEntriesInBackground()
        loadWifiEntriesInBackgroundTask.execute()
    }

    public override fun onPause() {
        super.onPause()
        if (::errorDialog.isInitialized) errorDialog.dismiss()
        loadWifiEntriesInBackgroundTask.cancel(true)
        if (::loadingDialog.isInitialized) loadingDialog.dismiss()
        if (::qrDialog.isInitialized) qrDialog.dismiss()
    }

    // This task loads the wifi entries in the background, and notifies the list adapter that the data has changed.
    private lateinit var errorDialogBuilder: AlertDialog.Builder
    private lateinit var errorDialog: AlertDialog
    private lateinit var loadingDialog: AlertDialog

    private inner class LoadWifiEntriesInBackground : AsyncTask<Unit, Unit, Unit>() {
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
            (wifi_ListView.adapter as ArrayAdapter<*>).notifyDataSetChanged()
            runOnUiThread { loadingDialog.dismiss() }
        }

        // This function saves wifi entry data into the wifiEntries ArrayList. It also throws up a dialog if the loading fails.
        private fun loadWifiEntries() {
            Log.v(TAG, "Loading wifi entries...")
            if (::wifiEntries.isInitialized) wifiEntries.clear()
            wifiEntrySSIDs.clear()
            try {
                wifiEntries = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) readOreoFile() else readNonOreoFile()
                wifiEntries.mapTo(wifiEntrySSIDs) { it.title }
                Log.v(TAG, "Wifi entries loaded.")
            } catch (e: WifiUnparseableException) {
                if (!::errorDialogBuilder.isInitialized) {
                    errorDialogBuilder = AlertDialog.Builder(this@MainActivity)
                    errorDialogBuilder.setCancelable(false)
                    errorDialogBuilder.setTitle(R.string.error_dialog_title)
                    errorDialogBuilder.setMessage(R.string.error_dialog_message)
                    errorDialogBuilder.setNeutralButton("RETRY", { dialog, _ ->
                        runOnUiThread {
                            dialog.dismiss()
                        }
                        loadWifiEntries()
                    })
                    errorDialogBuilder.setNegativeButton("EXIT", { dialog, _ ->
                        runOnUiThread {
                            dialog.dismiss()
                        }
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                            finishAndRemoveTask()
                        } else {
                            finish()
                        }
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

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_activity_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem?): Boolean {
        return when (item!!.itemId) {
            R.id.settingsItem -> {
                startActivity(Intent(this, SettingsActivity::class.java))
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}
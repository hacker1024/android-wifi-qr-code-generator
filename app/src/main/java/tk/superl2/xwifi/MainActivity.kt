package tk.superl2.xwifi

import android.Manifest
import android.app.AlertDialog
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.AsyncTask
import android.os.Build
import android.os.Bundle
import android.preference.PreferenceManager
import android.support.v4.app.ActivityCompat
import android.support.v4.content.ContextCompat
import android.support.v7.app.AppCompatActivity
import android.support.v7.app.AppCompatDelegate
import android.text.Html
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.ProgressBar
import kotlinx.android.synthetic.main.activity_main.*
import net.glxn.qrgen.android.QRCode
import net.glxn.qrgen.core.scheme.Wifi

private const val TAG = "MainActivity"
const val PERMISSION_CODE_GROUP_ADS = 0

internal const val QR_CODE_DIALOG_BOTTOM_IMAGE_MARGIN = 0

class MainActivity: AppCompatActivity() {
    // This variable holds an ArrayList of WifiEntry objects that each contain a saved wifi SSID and
    // password. It is updated whenever focus returns to the app (onResume).
    private val wifiEntries = ArrayList<WifiEntry>()
    private val wifiEntrySSIDs = ArrayList<String>()
    private lateinit var loadWifiEntriesInBackgroundTask: LoadWifiEntriesInBackground
    private lateinit var qrDialog: AlertDialog
    private lateinit var prefs: SharedPreferences

    fun sortWifiEntries(updateListView: Boolean) {
        if (prefs.getBoolean("case_sensitivity", DEFAULT_CASE_SENSITIVITY)) {
            wifiEntries.sortBy { it.title }
        } else {
            wifiEntries.sortWith(compareBy(String.CASE_INSENSITIVE_ORDER, { it.title }))
        }
        if (!prefs.getBoolean("sorting_order", DEFAULT_SORTING_ORDER)) wifiEntries.reverse()
        if (updateListView) {
            wifiEntrySSIDs.clear()
            wifiEntries.mapTo(wifiEntrySSIDs) { it.title }
            (wifi_ListView.adapter as ArrayAdapter<*>).notifyDataSetChanged()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        PreferenceManager.setDefaultValues(this, R.xml.preferences, false)
        prefs = PreferenceManager.getDefaultSharedPreferences(this)

        setThemeFromSharedPrefs(prefs)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Request permissions
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED)
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.ACCESS_FINE_LOCATION), PERMISSION_CODE_GROUP_ADS)

        adview.adUnitId = "a6acc0938ffd4af29f71abce19f035ec"
        adview.loadAd()

        wifi_ListView.adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, wifiEntrySSIDs)
        wifi_ListView.setOnItemClickListener { _, _, position, _ ->
            qrDialog = AlertDialog.Builder(this).apply {
                setView(ImageView(this@MainActivity).apply {
                    setPadding(0, 0, 0, QR_CODE_DIALOG_BOTTOM_IMAGE_MARGIN)
                    adjustViewBounds = true
                    setImageBitmap(QRCode
                            .from(Wifi()
                                    .withSsid(wifiEntrySSIDs[position])
                                    .withPsk(wifiEntries[position].password)
                                    .withAuthentication(wifiEntries[position].type.asQRCodeAuth()))
                            .withColor((if (AppCompatDelegate.getDefaultNightMode() != AppCompatDelegate.MODE_NIGHT_YES) 0xFF000000 else 0xFFE0E0E0).toInt(), 0x00000000) //TODO Better colour handling - atm, the colours may be wrong if the theme is set to system or auto.
                            .withSize(prefs.getString("qr_code_resolution", DEFAULT_QR_CODE_RESOLUTION).toInt(), prefs.getString("qr_code_resolution", DEFAULT_QR_CODE_RESOLUTION).toInt())
                            .bitmap())
                })
                setPositiveButton("Done") { dialog, _ -> dialog.dismiss() }
            }.create()
            qrDialog.show()
        }
        wifi_ListView.setOnItemLongClickListener { _, _, position, _ ->
            qrDialog = AlertDialog.Builder(this).apply {
                setMessage(if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    Html.fromHtml(
                            "<b>SSID:</b> ${wifiEntries[position].title}<br>" +
                                    if (wifiEntries[position].password != "") "<b>Password:</b> ${if (wifiEntries[position].type != WifiEntry.Type.WEP) wifiEntries[position].password else wifiEntries[position].password.removePrefix("\"").removeSuffix("\"")}<br>" else { "" } +
                                    "<b>Type:</b> ${wifiEntries[position].type}",
                            Html.FROM_HTML_MODE_LEGACY)
                } else {
                    @Suppress("DEPRECATION")
                    Html.fromHtml(
                            "<b>SSID:</b> ${wifiEntries[position].title}<br>" +
                                    if (wifiEntries[position].password != "") "<b>Password:</b> ${if (wifiEntries[position].type != WifiEntry.Type.WEP) wifiEntries[position].password else wifiEntries[position].password.removePrefix("\"").removeSuffix("\"")}<br>" else { "" } +
                                    "<b>Type:</b> ${wifiEntries[position].type}"
                    )
                }
                )
                setPositiveButton("Done") { dialog, _ -> dialog.dismiss() }
            }.create()
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

    override fun onDestroy() {
        super.onDestroy()
        adview.destroy()
    }

    // This task loads the wifi entries in the background, and notifies the list adapter that the data has changed.
    private lateinit var errorDialogBuilder: AlertDialog.Builder
    private lateinit var errorDialog: AlertDialog
    private lateinit var loadingDialog: AlertDialog

    private inner class LoadWifiEntriesInBackground: AsyncTask<Unit, Unit, Unit>() {
        override fun onPreExecute() {
            loadingDialog = AlertDialog.Builder(this@MainActivity).apply {
                setCancelable(false)
                setMessage(R.string.wifi_loading_message)
                setView(ProgressBar(this@MainActivity))
            }.create()
            loadingDialog.show()
        }

        override fun doInBackground(vararg params: Unit?) {
            loadWifiEntries()
            sortWifiEntries(false)
            wifiEntrySSIDs.clear()
            wifiEntries.mapTo(wifiEntrySSIDs) { it.title }
        }

        override fun onPostExecute(result: Unit?) {
            (wifi_ListView.adapter as ArrayAdapter<*>).notifyDataSetChanged()
            loadingDialog.dismiss()
        }

        // This function saves wifi entry data into the wifiEntries ArrayList. It also throws up a dialog if the loading fails.
        private fun loadWifiEntries() {
            Log.v(TAG, "Loading wifi entries...")
            wifiEntries.clear()
            try {
                wifiEntries.addAll(if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) readOreoFile() else readNonOreoFile())
                Log.v(TAG, "Wifi entries loaded.")
            } catch (e: WifiUnparseableException) {
                if (!::errorDialogBuilder.isInitialized) {
                    errorDialogBuilder = AlertDialog.Builder(this@MainActivity)
                    errorDialogBuilder.setCancelable(false)
                    errorDialogBuilder.setTitle(R.string.error_dialog_title)
                    errorDialogBuilder.setMessage(R.string.error_dialog_message)
                    errorDialogBuilder.setNeutralButton("Retry") { dialog, _ ->
                        runOnUiThread {
                            dialog.dismiss()
                        }
                        loadWifiEntries()
                    }
                    errorDialogBuilder.setNegativeButton("Exit") { dialog, _ ->
                        runOnUiThread {
                            dialog.dismiss()
                        }
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                            finishAndRemoveTask()
                        } else {
                            finish()
                        }
                    }
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

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.settingsItem -> {
                startActivity(Intent(this, SettingsActivity::class.java).putExtra("xposed", false))
                true
            }
            R.id.sortItem -> {
                prefs.edit().putBoolean("sorting_order", !prefs.getBoolean("sorting_order", DEFAULT_SORTING_ORDER)).apply()
                sortWifiEntries(true)
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}
package tk.superl2.xwifi

import android.Manifest
import android.app.AlertDialog
import android.app.SearchManager
import android.content.Context
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
import android.support.v7.widget.DividerItemDecoration
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.text.Html
import android.util.Log
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.ViewGroup
import android.widget.*
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
    private var wifiList = wifiEntries
    private lateinit var loadWifiEntriesInBackgroundTask: LoadWifiEntriesInBackground
    private lateinit var qrDialog: AlertDialog
    private lateinit var searchView: SearchView
    private lateinit var prefs: SharedPreferences

    fun sortWifiEntries(updateListView: Boolean) {
        if (prefs.getBoolean("case_sensitivity", DEFAULT_CASE_SENSITIVITY)) {
            wifiEntries.sortBy { it.title }
        } else {
            wifiEntries.sortWith(compareBy(String.CASE_INSENSITIVE_ORDER, { it.title }))
        }
        if (!prefs.getBoolean("sorting_order", DEFAULT_SORTING_ORDER)) wifiEntries.reverse()
        if (updateListView) {
            wifi_RecyclerView.adapter.notifyDataSetChanged()
        }
    }

    inner class WifiListAdapter: RecyclerView.Adapter<WifiListAdapter.ViewHolder>(), Filterable {
        inner class ViewHolder(val item: TextView) : RecyclerView.ViewHolder(item) {
            lateinit var wifiEntry: WifiEntry
            init {
                item.setOnClickListener {
                    qrDialog = AlertDialog.Builder(this@MainActivity).apply {
                        setView(ImageView(this@MainActivity).apply {
                            setPadding(0, 0, 0, QR_CODE_DIALOG_BOTTOM_IMAGE_MARGIN)
                            adjustViewBounds = true
                            setImageBitmap(QRCode
                                    .from(Wifi()
                                            .withSsid(wifiEntry.title)
                                            .withPsk(wifiEntry.password)
                                            .withAuthentication(wifiEntry.type.asQRCodeAuth()))
                                    .withColor((if (AppCompatDelegate.getDefaultNightMode() != AppCompatDelegate.MODE_NIGHT_YES) 0xFF000000 else 0xFFE0E0E0).toInt(), 0x00000000) //TODO Better colour handling - atm, the colours may be wrong if the theme is set to system or auto.
                                    .withSize(prefs.getString("qr_code_resolution", DEFAULT_QR_CODE_RESOLUTION).toInt(), prefs.getString("qr_code_resolution", DEFAULT_QR_CODE_RESOLUTION).toInt())
                                    .bitmap())
                        })
                        setPositiveButton("Done") { dialog, _ -> dialog.dismiss() }
                    }.create()
                    qrDialog.show()
                }
                item.setOnLongClickListener {
                    qrDialog = AlertDialog.Builder(this@MainActivity).apply {
                        setMessage(if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                            Html.fromHtml(
                                    "<b>SSID:</b> ${wifiEntry.title ?: "<i>ERROR</i>"}<br>" +
                                            if (wifiEntry.password != "") "<b>Password:</b> ${if (wifiEntry.type != WifiEntry.Type.WEP) wifiEntry.password else wifiEntry.password.removePrefix("\"").removeSuffix("\"")}<br>"  else { "" } +
                                            "<b>Type:</b> ${wifiEntry.type ?: "</i>ERROR</i>"}",
                                    Html.FROM_HTML_MODE_LEGACY)
                        } else {
                            @Suppress("DEPRECATION")
                            Html.fromHtml(
                                    "<b>SSID:</b> ${wifiEntry.title ?: "<i>ERROR</i>"}<br>" +
                                            if (wifiEntry.password != "") "<b>Password:</b> ${if (wifiEntry.type != WifiEntry.Type.WEP) wifiEntry.password else wifiEntry.password.removePrefix("\"").removeSuffix("\"")}<br>"  else { "" } +
                                            "<b>Type:</b> ${wifiEntry.type ?: "</i>ERROR</i>"}"
                            )
                        }
                        )
                        setPositiveButton("Done") { dialog, _ -> dialog.dismiss() }
                    }.create()
                    qrDialog.show()
                    true
                }
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
                ViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.wifi_list_item, parent, false) as TextView)

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.wifiEntry = wifiList[position]
            holder.item.text = holder.wifiEntry.title
        }

        override fun getItemCount() = wifiList.size

        override fun getFilter() = object: Filter() {
            override fun performFiltering(constraint: CharSequence): FilterResults {
                return FilterResults().apply {
                    values = ArrayList<WifiEntry>().apply {
                        wifiEntries.filterTo(this) { it.title.contains(constraint, true) }
                    }
                }
            }

            override fun publishResults(constraint: CharSequence, results: FilterResults) {
                wifiList = if (constraint == "") wifiEntries else results.values as ArrayList<WifiEntry>
                notifyDataSetChanged()
            }

        }
    }

    override fun onBackPressed() {
        if (searchView.isIconified) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) finishAndRemoveTask() else finish()
        } else {
            searchView.isIconified = true
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

        wifi_RecyclerView.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = WifiListAdapter()
            addItemDecoration(DividerItemDecoration(context, DividerItemDecoration.VERTICAL))
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
        }

        override fun onPostExecute(result: Unit?) {
            wifi_RecyclerView.adapter.notifyDataSetChanged()
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

        (menu.findItem(R.id.app_bar_search).actionView as SearchView).apply {
            searchView = this
            setSearchableInfo((getSystemService(Context.SEARCH_SERVICE) as SearchManager).getSearchableInfo(componentName))
            setOnSearchClickListener {
                menu.findItem(R.id.sortItem).isVisible = false
            }
            setOnQueryTextListener(object: SearchView.OnQueryTextListener {
                override fun onQueryTextSubmit(query: String?) = false

                override fun onQueryTextChange(newText: String): Boolean {
                    (wifi_RecyclerView.adapter as Filterable).filter.filter(newText)
                    return true
                }
            })
            setOnCloseListener {
                wifiList = wifiEntries
                menu.findItem(R.id.sortItem).isVisible = true
                invalidateOptionsMenu()
                false
            }
        }

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
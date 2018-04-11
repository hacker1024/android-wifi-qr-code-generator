package tk.superl2.xwifi

import android.app.AlertDialog
import android.app.SearchManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.AsyncTask
import android.os.Build
import android.os.Bundle
import android.preference.PreferenceManager
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
import kotlinx.android.synthetic.main.wifi_list_item.view.*
import net.glxn.qrgen.android.QRCode
import net.glxn.qrgen.core.scheme.Wifi

private const val TAG = "MainActivity"
const val PERMISSION_CODE_GROUP_ADS = 0

internal const val QR_CODE_DIALOG_BOTTOM_IMAGE_MARGIN = 0

class MainActivity: AppCompatActivity() {
    // SharedPreferences object
    private lateinit var prefs: SharedPreferences
    // AsyncTask object to load wifi entries
    private lateinit var loadWifiEntriesInBackgroundTask: LoadWifiEntriesInBackground
    // List of saved wifi networks (each network stored in a wifiEntry object)
    private val wifiEntries = ArrayList<WifiEntry>()
    // The list of WifiEntry objects used in the RecyclerView.
    private var wifiList = wifiEntries
    // The SearchView object
    private lateinit var searchView: SearchView
    // The QR dialog object
    private lateinit var qrDialog: AlertDialog

    // Sorts the list of wifi entries
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

    // The adapter class for the RecyclerView
    inner class WifiListAdapter: RecyclerView.Adapter<WifiListAdapter.ViewHolder>(), Filterable {
        // The ViewHolder class for the adapter
        inner class ViewHolder(val item: LinearLayout) : RecyclerView.ViewHolder(item) {
            // The WifiEntry object associated with the list item
            lateinit var wifiEntry: WifiEntry
            init {
                fun createAndShowInfoDialog(): Boolean {
                    qrDialog = AlertDialog.Builder(this@MainActivity).apply {
                        setMessage(if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                            Html.fromHtml(
                                    "<b>SSID:</b> ${wifiEntry.title}<br>" +
                                            if (wifiEntry.password != "") "<b>Password:</b> ${if (wifiEntry.type != WifiEntry.Type.WEP) wifiEntry.password else wifiEntry.password.removePrefix("\"").removeSuffix("\"")}<br>"  else { "" } +
                                            "<b>Type:</b> ${wifiEntry.type}",
                                    Html.FROM_HTML_MODE_LEGACY)
                        } else {
                            @Suppress("DEPRECATION")
                            Html.fromHtml(
                                    "<b>SSID:</b> ${wifiEntry.title}<br>" +
                                            if (wifiEntry.password != "") "<b>Password:</b> ${if (wifiEntry.type != WifiEntry.Type.WEP) wifiEntry.password else wifiEntry.password.removePrefix("\"").removeSuffix("\"")}<br>"  else { "" } +
                                            "<b>Type:</b> ${wifiEntry.type}"
                            )
                        }
                        )
                        setPositiveButton("Done") { dialog, _ -> dialog.dismiss() }
                    }.create()
                    qrDialog.show()
                    return true
                }

                // Set label onClick action
                item.label.setOnClickListener {
                    qrDialog = AlertDialog.Builder(this@MainActivity).apply {
                        setTitle(wifiEntry.title)
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
                // Set label onLongClick action
                item.label.setOnLongClickListener { createAndShowInfoDialog() }

                // Set security icon onClick action
                item.security.setOnClickListener { createAndShowInfoDialog() }
            }
        }

        // Creates and returns ViewHolders
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
                ViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.wifi_list_item, parent, false) as LinearLayout)

        // Binds new data to recycled ViewHolders
        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.wifiEntry = wifiList[position]
            holder.item.label.text = holder.wifiEntry.title
            holder.item.security.setImageDrawable(
                    if (holder.wifiEntry.type == WifiEntry.Type.NONE) {
                        ContextCompat.getDrawable(this@MainActivity, R.drawable.ic_signal_wifi_4_bar_24dp)
                    } else {
                        ContextCompat.getDrawable(this@MainActivity, R.drawable.ic_signal_wifi_4_bar_lock_24dp)
                    }
            )
        }

        // Returns how many list items there are
        override fun getItemCount() = wifiList.size

        // Returns Filter object
        override fun getFilter() = object: Filter() {
            // Filters the wifiEntries ArrayList with the qeury from the SearchView, and sets the results.
            override fun performFiltering(constraint: CharSequence) =
                    FilterResults().apply {
                        values = ArrayList<WifiEntry>().apply {
                            wifiEntries.filterTo(this) { it.title.contains(constraint, true) }
                        }
                    }

            // Publishes the filtered results
            override fun publishResults(constraint: CharSequence, results: FilterResults) {
                // If the query string isn't empty, set the RecyclerView data source to the filtered ArrayList
                wifiList = if (constraint == "") wifiEntries else results.values as ArrayList<WifiEntry>
                notifyDataSetChanged()
            }

        }
    }

    override fun onBackPressed() {
        // If the SearchView is collapsed, exit the app. Otherwise, collapse the SearchView.
        if (searchView.isIconified) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) finishAndRemoveTask() else finish()
        } else {
            searchView.isIconified = true
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Initialize the prefs object
        PreferenceManager.setDefaultValues(this, R.xml.preferences, false)
        prefs = PreferenceManager.getDefaultSharedPreferences(this)

        // Set the theme based on the value in the shared preferences (light/dark)
        setThemeFromSharedPrefs(prefs)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Ads disabled while my acount's getting reviewed
//        // Request permissions
//        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED)
//            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.ACCESS_FINE_LOCATION), PERMISSION_CODE_GROUP_ADS)
//
//        // Initialize ads
//        adview.adUnitId = "a6acc0938ffd4af29f71abce19f035ec"
//        adview.loadAd()

        // Set RecyclerView properties
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
        // Load wifi entries on resume
        loadWifiEntriesInBackgroundTask = LoadWifiEntriesInBackground()
        loadWifiEntriesInBackgroundTask.execute()
    }

    public override fun onPause() {
        super.onPause()
        // Dismiss error dialog on pause
        if (::errorDialog.isInitialized) errorDialog.dismiss()
        // Cancel loading task on pause
        loadWifiEntriesInBackgroundTask.cancel(true)
        // Dismiss loading dialog on pause
        if (::loadingDialog.isInitialized) loadingDialog.dismiss()
        // Dismiss qr dialog dialog on pause
        if (::qrDialog.isInitialized) qrDialog.dismiss()
    }

    override fun onDestroy() {
        super.onDestroy()
        // Ads are diabled while my account's under review
        // Destroy ad view on destroy
//        adview.destroy()
    }

    // Error dialog builder
    private lateinit var errorDialogBuilder: AlertDialog.Builder
    // Error dialog object
    private lateinit var errorDialog: AlertDialog
    // Loading dialog object
    private lateinit var loadingDialog: AlertDialog
    // This task loads the wifi entries in the background, and notifies the list adapter that the data has changed.
    private inner class LoadWifiEntriesInBackground: AsyncTask<Unit, Unit, Unit>() {
        override fun onPreExecute() {
            // Create and show loading dialog
            loadingDialog = AlertDialog.Builder(this@MainActivity).apply {
                setCancelable(false)
                setMessage(R.string.wifi_loading_message)
                setView(ProgressBar(this@MainActivity))
            }.create()
            loadingDialog.show()
        }

        override fun doInBackground(vararg params: Unit?) {
            // Load and sort wifi entries
            loadWifiEntries()
            sortWifiEntries(false)
        }

        override fun onPostExecute(result: Unit?) {
            // Update RecyclerView
            wifi_RecyclerView.adapter.notifyDataSetChanged()
            // Dismiss loading dialog
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

        // Set SearchView properties
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
                // Start settings activity
                startActivity(Intent(this, SettingsActivity::class.java).putExtra("xposed", false))
                true
            }
            R.id.sortItem -> {
                // Toggle sorting order
                prefs.edit().putBoolean("sorting_order", !prefs.getBoolean("sorting_order", DEFAULT_SORTING_ORDER)).apply()
                sortWifiEntries(true)
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}
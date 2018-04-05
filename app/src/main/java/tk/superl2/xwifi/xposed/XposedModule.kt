package tk.superl2.xwifi.xposed

import android.app.Activity
import android.app.AlertDialog
import android.app.AndroidAppHelper
import android.app.Fragment
import android.content.ComponentName
import android.content.Intent
import android.os.AsyncTask
import android.os.Build
import android.text.Html
import android.util.Log
import android.view.ContextMenu
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.ImageView
import android.widget.ProgressBar
import com.crossbowffs.remotepreferences.RemotePreferences
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers.*
import de.robv.android.xposed.callbacks.XC_LoadPackage
import net.glxn.qrgen.android.QRCode
import net.glxn.qrgen.core.scheme.Wifi
import tk.superl2.xwifi.*

private const val TAG = "WQRCC: Xposed Module"

private const val MENU_ID_WPS_PBC = Menu.FIRST
private const val MENU_ID_WPS_PIN = Menu.FIRST + 1
private const val MENU_ID_CONNECT = Menu.FIRST + 6
private const val MENU_ID_FORGET = Menu.FIRST + 7
private const val MENU_ID_MODIFY = Menu.FIRST + 8
private const val MENU_ID_SHOW_PASSWORD = Menu.FIRST + 10
private const val MENU_ID_SHOW_QR_CODE = Menu.FIRST + 11


class XposedModule: IXposedHookLoadPackage {
    lateinit var prefs: RemotePreferences
    private lateinit var loadingDialog: AlertDialog
    private lateinit var loadWifiEntriesInBackgroundTask: LoadWifiEntriesInBackground
    private lateinit var qrDialog: AlertDialog

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        if (lpparam.packageName != "com.android.settings") return

        findAndHookMethod("com.android.settings.wifi.WifiSettings", lpparam.classLoader, "onCreateContextMenu", ContextMenu::class.java, View::class.java, ContextMenu.ContextMenuInfo::class.java, object: XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                if ((param.args[1] as View).tag != null && (param.args[1] as View).tag::class.java == findClass("com.android.settings.wifi.LongPressAccessPointPreference", lpparam.classLoader)) {
                    if (getIntField(getObjectField(param.thisObject, "mSelectedAccessPoint"), "security") != ANDROID_SECURITY_EAP) {
                        (param.args[0] as ContextMenu).add(Menu.NONE, MENU_ID_SHOW_PASSWORD, 0, "Network information")
                    }
                    if (getObjectField(getObjectField(param.thisObject, "mSelectedAccessPoint"), "networkId") as Int != -1 && getIntField(getObjectField(param.thisObject, "mSelectedAccessPoint"), "security") != ANDROID_SECURITY_EAP) {
                        (param.args[0] as ContextMenu).add(Menu.NONE, MENU_ID_SHOW_QR_CODE, 0, "Show QR code")
                    }
                }
            }
        })

        findAndHookMethod("com.android.settings.wifi.WifiSettings", lpparam.classLoader, "onContextItemSelected", MenuItem::class.java, object: XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                fun searchForWifiEntry(ssid: String, security: Int, pskType: Int = ANDROID_PSK_UNKNOWN): WifiEntry {
                    if (getObjectField(getObjectField(param.thisObject, "mSelectedAccessPoint"), "networkId") as Int == -1) {
                        return WifiEntry(ssid, "", WifiEntry.Type.fromAndroidSecurityInt(getIntField(getObjectField(param.thisObject, "mSelectedAccessPoint"), "security"), getIntField(getObjectField(param.thisObject, "mSelectedAccessPoint"), "pskType")))
                    } else {
                        (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) readOreoFile() else readNonOreoFile()).forEach {
                            if (it.title == ssid && it.type == WifiEntry.Type.fromAndroidSecurityInt(security, pskType)) {
                                return it
                            }
                        }
                    }
                    Log.e(TAG, "Network \"$ssid\" is marked saved, but cannot be found in saved networks file!")
                    return WifiEntry(ssid, "", WifiEntry.Type.fromAndroidSecurityInt(getIntField(getObjectField(param.thisObject, "mSelectedAccessPoint"), "security"), getIntField(getObjectField(param.thisObject, "mSelectedAccessPoint"), "pskType")))
                }

                when ((param.args[0] as MenuItem).itemId) {
                    MENU_ID_SHOW_PASSWORD -> {
                        val selectedAccessPoint = searchForWifiEntry(getObjectField(getObjectField(param.thisObject, "mSelectedAccessPoint"), "ssid") as String, getObjectField(getObjectField(param.thisObject, "mSelectedAccessPoint"), "security") as Int)
                        val builder = AlertDialog.Builder((param.thisObject as Fragment).activity)
                        builder.setMessage(if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                            Html.fromHtml(
                                    "<b>SSID</b>: ${selectedAccessPoint.title}<br>" +
                                            (if (selectedAccessPoint.password != "") "<b>Password</b>: ${if (selectedAccessPoint.type != WifiEntry.Type.WEP) selectedAccessPoint.password else selectedAccessPoint.password.removePrefix("\"").removeSuffix("\"")}<br>" else { "" }) +
                                            "<b>Type</b>: ${selectedAccessPoint.type}",
                                    Html.FROM_HTML_MODE_LEGACY)
                        } else {
                            @Suppress("DEPRECATION")
                            Html.fromHtml(
                                    "<b>SSID</b>: ${selectedAccessPoint.title}<br>" +
                                            (if (selectedAccessPoint.password != "") "<b>Password</b>: ${if (selectedAccessPoint.type != WifiEntry.Type.WEP) selectedAccessPoint.password else selectedAccessPoint.password.removePrefix("\"").removeSuffix("\"")}<br>" else { "" }) +
                                            "<b>Type</b>: ${selectedAccessPoint.type}"
                            )
                        }
                        )
                        builder.setPositiveButton("Done") { dialog, _ -> dialog.dismiss() }
                        qrDialog = builder.create()
                        (param.thisObject as Fragment).activity.runOnUiThread { qrDialog.show() }
                        param.result = true
                    }
                    MENU_ID_SHOW_QR_CODE -> {
                        val selectedAccessPoint = searchForWifiEntry(getObjectField(getObjectField(param.thisObject, "mSelectedAccessPoint"), "ssid") as String, getObjectField(getObjectField(param.thisObject, "mSelectedAccessPoint"), "security") as Int)
                        if (!::prefs.isInitialized) prefs = RemotePreferences(AndroidAppHelper.currentApplication(), "tk.superl2.xwifi.preferences", "tk.superl2.xwifi_preferences")
                        val qrCodeView = ImageView((param.thisObject as Fragment).activity)
                        qrCodeView.setPadding(0, 0, 0, QR_CODE_DIALOG_BOTTOM_IMAGE_MARGIN)
                        qrCodeView.adjustViewBounds = true
                        qrCodeView.setImageBitmap(QRCode
                                .from(Wifi()
                                        .withSsid(selectedAccessPoint.title)
                                        .withPsk(selectedAccessPoint.password)
                                        .withAuthentication(selectedAccessPoint.type.asQRCodeAuth()))
                                .withSize(prefs.getString("qr_code_resolution", DEFAULT_QR_GENERATION_RESOLUTION).toInt(), prefs.getString("qr_code_resolution", DEFAULT_QR_GENERATION_RESOLUTION).toInt())
                                .bitmap())
                        val builder = AlertDialog.Builder((param.thisObject as Fragment).activity)
                        builder.setTitle(selectedAccessPoint.title)
                        builder.setView(qrCodeView)
                        builder.setNeutralButton("Settings") { dialog, _ -> dialog.dismiss(); (param.thisObject as Fragment).startActivity(Intent().setComponent(ComponentName("tk.superl2.xwifi", "tk.superl2.xwifi.SettingsActivity")).putExtra("xposed", true))}
                        builder.setPositiveButton("Done") { dialog, _ -> dialog.dismiss() }
                        qrDialog = builder.create()
                        (param.thisObject as Fragment).activity.runOnUiThread { qrDialog.show() }
                        param.result = true
                    }
                }
            }
        })

        findAndHookMethod("com.android.settings.wifi.WifiSettings", lpparam.classLoader, "onPause", object: XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam?) {
                if (::loadingDialog.isInitialized) loadingDialog.dismiss()
                if (::loadWifiEntriesInBackgroundTask.isInitialized) loadWifiEntriesInBackgroundTask.cancel(true)
                if (::qrDialog.isInitialized) qrDialog.dismiss()
            }
        })
    }

    private inner class LoadWifiEntriesInBackground(val activity: Activity): AsyncTask<Unit, Unit, ArrayList<WifiEntry>>() {
        override fun onPreExecute() {
            val loadingDialogBuilder = AlertDialog.Builder(AndroidAppHelper.currentApplication())
            loadingDialogBuilder.setCancelable(false)
            loadingDialogBuilder.setMessage(R.string.wifi_loading_message)
            loadingDialogBuilder.setView(ProgressBar(AndroidAppHelper.currentApplication()))
            loadingDialog = loadingDialogBuilder.create()
            loadingDialog.show()
        }

        override fun doInBackground(vararg params: Unit?): ArrayList<WifiEntry>? {
            return loadWifiEntries()
        }

        override fun onPostExecute(result: ArrayList<WifiEntry>?) {
            activity.runOnUiThread { loadingDialog.dismiss() }
        }

        // This function saves wifi entry data into the wifiEntries ArrayList. It also throws up a dialog if the loading fails.
        private fun loadWifiEntries(): ArrayList<WifiEntry>? {
            Log.v(TAG, "Loading wifi entries...")
            val wifiEntries: ArrayList<WifiEntry>
            return try {
                wifiEntries = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) readOreoFile() else readNonOreoFile()
                Log.v(TAG, "Wifi entries loaded.")
                wifiEntries
            } catch (e: WifiUnparseableException) {
                null
            }
        }
    }
}
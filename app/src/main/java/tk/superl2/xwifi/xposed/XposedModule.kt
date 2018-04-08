package tk.superl2.xwifi.xposed

import android.app.AlertDialog
import android.app.AndroidAppHelper
import android.app.Fragment
import android.content.ComponentName
import android.content.Intent
import android.os.Build
import android.text.Html
import android.util.Log
import android.view.ContextMenu
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.Toast
import com.crossbowffs.remotepreferences.RemotePreferences
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers.*
import de.robv.android.xposed.callbacks.XC_LoadPackage
import kotlinx.coroutines.experimental.launch
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

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        if (lpparam.packageName != "com.android.settings") return

        // com.android.settings.wifi.WifiSettings.onCreateContextMenu(menu: ContextMenu, view: View, info: ContextMenuInfo)
        findAndHookMethod("com.android.settings.wifi.WifiSettings", lpparam.classLoader, "onCreateContextMenu", ContextMenu::class.java, View::class.java, ContextMenu.ContextMenuInfo::class.java, object: XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                if ((param.args[1] as View).tag != null && (param.args[1] as View).tag::class.java == findClass("com.android.settings.wifi.LongPressAccessPointPreference", lpparam.classLoader)) {
                    if (getIntField(getObjectField(param.thisObject, "mSelectedAccessPoint"), "security") != ANDROID_SECURITY_EAP) {
                        (param.args[0] as ContextMenu).add(Menu.NONE, MENU_ID_SHOW_PASSWORD, 0, "Network information")
                    }
                    if ((getIntField(getObjectField(param.thisObject, "mSelectedAccessPoint"), "networkId") != -1 || getIntField(getObjectField(param.thisObject, "mSelectedAccessPoint"), "security") == ANDROID_SECURITY_NONE) && getIntField(getObjectField(param.thisObject, "mSelectedAccessPoint"), "security") != ANDROID_SECURITY_EAP) {
                        (param.args[0] as ContextMenu).add(Menu.NONE, MENU_ID_SHOW_QR_CODE, 0, "Show QR code")
                    }
                }
            }
        })

        // com.android.settings.wifi.WifiSettings.onContextItemSelected(item: MenuItem)
        findAndHookMethod("com.android.settings.wifi.WifiSettings", lpparam.classLoader, "onContextItemSelected", MenuItem::class.java, object: XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                fun searchForWifiEntry(ssid: String, security: Int, pskType: Int = ANDROID_PSK_UNKNOWN): WifiEntry {
                    Log.v(TAG, "Selected access point: ${getObjectField(param.thisObject, "mSelectedAccessPoint")}")
                    if (getObjectField(getObjectField(param.thisObject, "mSelectedAccessPoint"), "networkId") as Int == -1) {
                        return WifiEntry(ssid, "", WifiEntry.Type.fromAndroidSecurityInt(getIntField(getObjectField(param.thisObject, "mSelectedAccessPoint"), "security"), getIntField(getObjectField(param.thisObject, "mSelectedAccessPoint"), "pskType")))
                    } else {
                        try {
                            (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) readOreoFile() else readNonOreoFile()).forEach {
                                if (it.title == ssid && it.type == WifiEntry.Type.fromAndroidSecurityInt(security, pskType)) {
                                    return it
                                }
                            }
                        } catch (e: WifiUnparseableException) {
                            if ((param.args[0] as MenuItem).itemId == MENU_ID_SHOW_PASSWORD || getIntField(getObjectField(param.thisObject, "mSelectedAccessPoint"), "security") == ANDROID_SECURITY_NONE) {
                                (param.thisObject as Fragment).activity.runOnUiThread { Toast.makeText((param.thisObject as Fragment).activity, "The wifi configuration file cannot be found or parsed! Are you sure this app has root access? Can't show password.", Toast.LENGTH_LONG).show() }
                            } else {
                                throw WifiUnparseableException()
                            }
                        }
                    }
                    Log.w(TAG, "Network \"$ssid\" is marked saved, but cannot be found in saved networks file!")
                    return WifiEntry(ssid, "", WifiEntry.Type.fromAndroidSecurityInt(getIntField(getObjectField(param.thisObject, "mSelectedAccessPoint"), "security"), getIntField(getObjectField(param.thisObject, "mSelectedAccessPoint"), "pskType")))
                }

                when ((param.args[0] as MenuItem).itemId) {
                    MENU_ID_SHOW_PASSWORD -> {
                        val loadingDialog = AlertDialog.Builder((param.thisObject as Fragment).activity).apply {
                            setCancelable(false)
                            setMessage("Loading...")
                            setView(ProgressBar((param.thisObject as Fragment).activity))
                        }.create()
                        loadingDialog.show()
                        launch {
                            val selectedAccessPoint = searchForWifiEntry(getObjectField(getObjectField(param.thisObject, "mSelectedAccessPoint"), "ssid") as String, getObjectField(getObjectField(param.thisObject, "mSelectedAccessPoint"), "security") as Int)
                            AlertDialog.Builder((param.thisObject as Fragment).activity).apply {
                                setMessage(if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                                    Html.fromHtml(
                                            "<b>SSID:</b> ${selectedAccessPoint.title}<br>" +
                                                    (if (selectedAccessPoint.password != "") "<b>Password:</b> ${if (selectedAccessPoint.type != WifiEntry.Type.WEP) selectedAccessPoint.password else selectedAccessPoint.password.removePrefix("\"").removeSuffix("\"")}<br>" else { "" }) +
                                                    "<b>Type:</b> ${selectedAccessPoint.type}<br>" +
                                                    "<b>Metered:</b> ${if (callMethod(getObjectField(param.thisObject, "mSelectedAccessPoint"), "isMetered") as Boolean) "Yes" else "No"}<br>" +
                                                    "<b>Saved:</b> ${if (getObjectField(getObjectField(param.thisObject, "mSelectedAccessPoint"), "networkId") == -1) "No" else "Yes"}<br>" +
                                                    "<b>Connected:</b> ${if (callMethod(getObjectField(param.thisObject, "mSelectedAccessPoint"), "isActive") as Boolean) "Yes" else "No"}",
                                            Html.FROM_HTML_MODE_LEGACY)
                                } else {
                                    @Suppress("DEPRECATION")
                                    Html.fromHtml(
                                            "<b>SSID:</b> ${selectedAccessPoint.title}<br>" +
                                                    (if (selectedAccessPoint.password != "") "<b>Password:</b> ${if (selectedAccessPoint.type != WifiEntry.Type.WEP) selectedAccessPoint.password else selectedAccessPoint.password.removePrefix("\"").removeSuffix("\"")}<br>" else { "" }) +
                                                    "<b>Type:</b> ${selectedAccessPoint.type}" +
                                                    "<b>Metered:</b> ${if (callMethod(getObjectField(param.thisObject, "mSelectedAccessPoint"), "isMetered") as Boolean) "Yes" else "No"}<br>" +
                                                    "<b>Saved:</b> ${if (getObjectField(getObjectField(param.thisObject, "mSelectedAccessPoint"), "networkId") == -1) "No" else "Yes"}<br>" +
                                                    "<b>Connected:</b> ${if (callMethod(getObjectField(param.thisObject, "mSelectedAccessPoint"), "isActive") as Boolean) "Yes" else "No"}"
                                    )
                                }
                                )
                                setPositiveButton("Done") { dialog, _ -> dialog.dismiss() }
                                loadingDialog.dismiss()
                                (param.thisObject as Fragment).activity.runOnUiThread { show() }
                            }
                        }
                        param.result = true
                    }
                    MENU_ID_SHOW_QR_CODE -> {
                        val loadingDialog = AlertDialog.Builder((param.thisObject as Fragment).activity).apply {
                            setCancelable(false)
                            setMessage("Loading...")
                            setView(ProgressBar((param.thisObject as Fragment).activity))
                        }.create()
                        loadingDialog.show()
                        launch {
                            try {
                                val prefs = RemotePreferences(AndroidAppHelper.currentApplication(), "tk.superl2.xwifi.preferences", "tk.superl2.xwifi_preferences")
                                val selectedAccessPoint = searchForWifiEntry(getObjectField(getObjectField(param.thisObject, "mSelectedAccessPoint"), "ssid") as String, getObjectField(getObjectField(param.thisObject, "mSelectedAccessPoint"), "security") as Int)
                                AlertDialog.Builder((param.thisObject as Fragment).activity).also { builder ->
                                    builder.setTitle(selectedAccessPoint.title)
                                    builder.setView(ImageView((param.thisObject as Fragment).activity).also { qrCodeView ->
                                        qrCodeView.setPadding(0, 0, 0, QR_CODE_DIALOG_BOTTOM_IMAGE_MARGIN)
                                        qrCodeView.adjustViewBounds = true
                                        qrCodeView.setImageBitmap(QRCode
                                                .from(Wifi()
                                                        .withSsid(selectedAccessPoint.title)
                                                        .withPsk(selectedAccessPoint.password)
                                                        .withAuthentication(selectedAccessPoint.type.asQRCodeAuth()))
                                                .withSize(prefs.getString("qr_code_resolution", DEFAULT_QR_GENERATION_RESOLUTION).toInt(), prefs.getString("qr_code_resolution", DEFAULT_QR_GENERATION_RESOLUTION).toInt())
                                                .bitmap())
                                    })
                                    builder.setNeutralButton("Settings") { dialog, _ -> dialog.dismiss(); (param.thisObject as Fragment).startActivity(Intent().setComponent(ComponentName("tk.superl2.xwifi", "tk.superl2.xwifi.SettingsActivity")).putExtra("xposed", true)) }
                                    builder.setPositiveButton("Done") { dialog, _ -> dialog.dismiss() }
                                    loadingDialog.dismiss()
                                    (param.thisObject as Fragment).activity.runOnUiThread { builder.show() }
                                }
                            } catch (e: WifiUnparseableException) {
                                AlertDialog.Builder((param.thisObject as Fragment).activity).apply {
                                    setCancelable(false)
                                    setTitle("The wifi configuration file cannot be found or parsed!")
                                    setMessage("Are you sure this app has root access?\nWithout root access, wifi passwords cannot be retrieved. This may also fail if your wifi configuration file isn't in the normal location.\nYou can still create QR codes for networks with no passwords, and you can also still see wifi networks' details.")
                                    setNeutralButton("Retry") { dialog, _ ->  dialog.dismiss(); beforeHookedMethod(param) }
                                    setNegativeButton("Cancel") { dialog, _ -> dialog.dismiss() }
                                    loadingDialog.dismiss()
                                    (param.thisObject as Fragment).activity.runOnUiThread { show() }
                                }
                            }
                        }
                        param.result = true
                    }
                }
            }
        })
    }
}
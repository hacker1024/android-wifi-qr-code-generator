package tk.superl2.xwifi

import android.content.Intent
import android.net.Uri
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.preference.PreferenceManager
import android.support.v7.app.AppCompatDelegate
import android.view.View
import husaynhakeem.com.aboutpage.AboutPage
import husaynhakeem.com.aboutpage.Item

class AboutActivity: AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        super.onCreate(savedInstanceState)
        setContentView(AboutPage(this)
                .setImage(R.mipmap.ic_launcher)
                .setDescription(R.string.about_description)
                .addItem(Item("Wifi QR Code Creator v${BuildConfig.VERSION_NAME}", R.mipmap.ic_launcher_round, View.OnClickListener {
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/hacker1024/android-wifi-qr-code-generator/releases/tag/v${BuildConfig.VERSION_NAME}")))
                }))
                .addItem(Item("README on Github", R.drawable.ic_info_outline_24dp, View.OnClickListener {
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/hacker1024/android-wifi-qr-code-generator/blob/v${BuildConfig.VERSION_NAME}/README.md")))
                }))
                .addEmail("Contact me by email", "superl2@notsharingmy.info")
                .addGithub("hacker1024/android-wifi-qr-code-generator")
                .create())
    }
}

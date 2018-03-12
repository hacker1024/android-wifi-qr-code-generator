package tk.superl2.xwifi

import android.os.Parcel
import android.os.Parcelable
import net.glxn.qrgen.core.scheme.Wifi

import java.util.Arrays


class WifiEntry : Parcelable {
    lateinit var type: Type
    lateinit var title: String
    lateinit var password: String
    var tag = ""
    var connectedInd = false

    enum class Type {
        WPA { override fun asQRCodeAuth() = Wifi.Authentication.WPA },
        WEP { override fun asQRCodeAuth() = Wifi.Authentication.WEP },
        NONE { override fun asQRCodeAuth() = Wifi.Authentication.nopass },
        ENTERPRISE {
            override fun asQRCodeAuth(): Wifi.Authentication {
                throw GetEAPTypeException()
            }
        };
        abstract fun asQRCodeAuth(): Wifi.Authentication
    }

    constructor() {}

    constructor(title: String, password: String) {
        initVars(title, password, false, "")
    }

    constructor(title: String, password: String, connectedInd: Boolean) {
        initVars(title, password, connectedInd, "")
    }

    private fun initVars(title: String, password: String, connectedInd: Boolean, tag: String) {
        this.title = title
        this.password = password
        this.connectedInd = connectedInd
        this.tag = tag
        this.type = Type.NONE
    }

    fun getPassword(showAll: Boolean): String =
        if (showAll) {
            password
        } else {
            val fill = CharArray(password.length)
            Arrays.fill(fill, '*')
            String(fill)
        }

    override fun toString(): String = "Wifi: $title, pass: $password, Connected: $connectedInd, tag: $tag\n"

    /** */
    //Parcelable Implementation


    //Parcel Constructor
    private constructor(`in`: Parcel) {
        title = `in`.readString()
        password = `in`.readString()
        this.connectedInd = `in`.readInt() == 1
        tag = `in`.readString()
    }


    override fun describeContents(): Int = 0

    override fun writeToParcel(dest: Parcel, flags: Int) {
        dest.writeString(title)
        dest.writeString(password)
        dest.writeInt(if (connectedInd) 1 else 0)
        dest.writeString(tag)
    }

    companion object {
        val CREATOR: Parcelable.Creator<WifiEntry> = object : Parcelable.Creator<WifiEntry> {
            override fun createFromParcel(`in`: Parcel): WifiEntry = WifiEntry(`in`)
            override fun newArray(size: Int): Array<WifiEntry?> = arrayOfNulls(size)
        }
    }
}

internal class GetEAPTypeException(override var message: String = "The QR Code library does not support EAP. There's no reason this function should be called on an EAP enum."): Exception(message)
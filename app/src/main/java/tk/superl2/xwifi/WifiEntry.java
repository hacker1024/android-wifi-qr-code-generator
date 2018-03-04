package tk.superl2.xwifi;

import android.os.Parcel;
import android.os.Parcelable;

import java.util.Arrays;


public class WifiEntry implements Parcelable {

    public static final Parcelable.Creator<WifiEntry> CREATOR = new Parcelable.Creator<WifiEntry>() {
        @Override
        public WifiEntry createFromParcel(Parcel in) {
            return new WifiEntry(in);
        }

        @Override
        public WifiEntry[] newArray(int size) {
            return new WifiEntry[size];
        }
    };


    private String title;
    private String password;
    private String tag = "";
    private boolean connectedInd = false;


    public WifiEntry() {
    }

    public WifiEntry(String title, String password) {
        initVars(title, password, false, "");
    }

    public WifiEntry(String title, String password, boolean connectedInd) {
        initVars(title, password, connectedInd, "");
    }

    private void initVars(String title, String password, boolean connectedInd, String tag) {
        this.title = title;
        this.password = password;
        this.connectedInd = connectedInd;
        this.tag = tag;
    }


    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getPassword(boolean showAll) {
        if (showAll) {
            return password;
        } else {
            char[] fill = new char[password.length()];
            Arrays.fill(fill, '*');
            return  new String(fill);
        }
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public boolean getConnectedInd() {
        return connectedInd;
    }

    public void setConnectedInd(boolean connectedInd) {
        this.connectedInd = connectedInd;
    }

    public String getTag() {
        return tag;
    }

    public void setTag(String tag) {
        this.tag = tag;
    }

    @Override
    public String toString() {
        return "Wifi: " + title + ", pass: " + password + ", Connected: " + connectedInd + ", tag: " + tag + "\n";
    }

    /************************************************************/
    //Parcelable Implementation


    //Parcel Constructor
    protected WifiEntry(Parcel in) {
        title = in.readString();
        password = in.readString();
        this.connectedInd = (in.readInt() == 1);
        tag = in.readString();
    }


    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {

        dest.writeString(title);
        dest.writeString(password);
        dest.writeInt(connectedInd ? 1 : 0);
        dest.writeString(tag);
    }

}
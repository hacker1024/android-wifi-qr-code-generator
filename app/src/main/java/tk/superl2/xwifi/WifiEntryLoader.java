package tk.superl2.xwifi;

import android.util.Log;
import android.util.Xml;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.util.ArrayList;

public class WifiEntryLoader {
    private final String mOreoLocation = "/data/misc/wifi/WifiConfigStore.xml";

    //****************************************************//
    //****************** Helper Methods ******************//
    //****************************************************//

    private ArrayList<WifiEntry> readNetworkList(XmlPullParser parser) throws XmlPullParserException, IOException {
        ArrayList<WifiEntry> result = new ArrayList<>();
        parser.require(XmlPullParser.START_TAG, null, "NetworkList");
        boolean doLoop = true;
        while (doLoop) {
            parser.next();
            String tagName = parser.getName();
            if (tagName == null) {
                tagName = "";
            }
            doLoop = (!tagName.equalsIgnoreCase("NetworkList"));
            if (parser.getEventType() != XmlPullParser.START_TAG) {
                continue;
            }

            if (tagName.equals("Network")) {
                WifiEntry newWifi = readNetworkEntry(parser);
                if (newWifi.getTitle().length() != 0) {
                    result.add(newWifi);
                }
            } else {
                skip(parser);
            }
        }
        return result;
    }

    // Parses a "Network" entry
    private WifiEntry readNetworkEntry(XmlPullParser parser) throws XmlPullParserException, IOException {
        parser.require(XmlPullParser.START_TAG, null, "Network");
        WifiEntry result = new WifiEntry("", "");
        while (parser.next() != XmlPullParser.END_TAG) {
            if (parser.getEventType() != XmlPullParser.START_TAG) {
                continue;
            }
            String tagName = parser.getName();
            // Starts by looking for the entry tag
            if (tagName.equals("WifiConfiguration")) {
                result = readWiFiConfig(parser, result);
//            } else if (tagName.equals("WifiEnterpriseConfiguration")) {
//                result.setTyp(WifiObject.TYP_ENTERPRISE);
            } else {
                skip(parser);
            }
        }
        return result;
    }

    // Parses a "WifiConfiguration" entry
    private WifiEntry readWiFiConfig(XmlPullParser parser, WifiEntry result) throws XmlPullParserException, IOException {
        while (parser.next() != XmlPullParser.END_TAG) {
            if (parser.getEventType() != XmlPullParser.START_TAG) {
                continue;
            }
            String tagName = parser.getName();
            String name = parser.getAttributeValue(null, "name");
            if (name.equals("SSID") && !tagName.equalsIgnoreCase("null")) {
                result.setTitle(readTag(parser, tagName));
            } else if (name.equals("PreSharedKey") && !tagName.equalsIgnoreCase("null")) {
                result.setPassword(readTag(parser, tagName));
//                result.setTyp(WifiObject.TYP_WPA);
            } else if (name.equals("WEPKeys") && !tagName.equalsIgnoreCase("null")) {
                result.setPassword(readTag(parser, tagName));
//                result.setTyp(WifiObject.TYP_WEP);
            } else {
                skip(parser);
            }
        }
        return result;
    }

    // Return the text for a specified tag.
    private String readTag(XmlPullParser parser, String tagName) throws IOException, XmlPullParserException {
        parser.require(XmlPullParser.START_TAG, null, tagName);
        String result = "";
        if (parser.next() == XmlPullParser.TEXT) {
            result = parser.getText();
            parser.nextTag();
        }
        parser.require(XmlPullParser.END_TAG, null, tagName);
        if (tagName.equalsIgnoreCase("string")
                && Character.toString(result.charAt(0)).equals("\"")) {
            result = result.substring(1, result.length() - 1);
        }
        return result;
    }

    // Skips tags the parser isn't interested in. Uses depth to handle nested tags. i.e.,
    // if the next tag after a START_TAG isn't a matching END_TAG, it keeps going until it
    // finds the matching END_TAG (as indicated by the value of "depth" being 0).
    private void skip(XmlPullParser parser) throws XmlPullParserException, IOException {
        if (parser.getEventType() != XmlPullParser.START_TAG) {
            throw new IllegalStateException();
        }
        int depth = 1;
        while (depth != 0) {
            switch (parser.next()) {
                case XmlPullParser.END_TAG:
                    depth--;
                    break;
                case XmlPullParser.START_TAG:
                    depth++;
                    break;
            }
        }
    }

    public ArrayList<WifiEntry> readOreoFile() {
        ArrayList<WifiEntry> result = new ArrayList<>();
        try {
            Process suOreoProcess = Runtime.getRuntime().exec("su -c /system/bin/cat " + mOreoLocation);
            try {
                suOreoProcess.waitFor();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            XmlPullParser parser = Xml.newPullParser();
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false);
            parser.setInput(suOreoProcess.getInputStream(), null);
            while (parser.next() != XmlPullParser.END_DOCUMENT) {
                if (parser.getEventType() != XmlPullParser.START_TAG) {
                    continue;
                }
                if (parser.getName().equalsIgnoreCase("NetworkList")) {
                    // Process the <Network> entries in the list
                    result.addAll(readNetworkList(parser));
                }
            }
        } catch (IOException | XmlPullParserException e) {
            e.printStackTrace();
        } finally {
            if (!result.isEmpty()) {
                return result;
            } else {
                return null;
            }
        }
    }
}

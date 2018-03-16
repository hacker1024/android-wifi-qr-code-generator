package tk.superl2.xwifi;

import android.util.Log;
import android.util.Xml;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;

public class WifiEntryLoaderJava {
    private static final String mOreoLocation = "/data/misc/wifi/WifiConfigStore.xml";

    private static ArrayList<WifiEntry> readNetworkList(XmlPullParser parser) throws XmlPullParserException, IOException {
        ArrayList<WifiEntry> result = new ArrayList<>();
        parser.require(XmlPullParser.START_TAG, null, "NetworkList");
        boolean doLoop = true;
        while (doLoop) {
            try {
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
            } catch (Exception e) {
                Log.e("LoadData.NetworkList", e.getMessage());
                doLoop = false;
            }
        }
        return result;
    }

    // Parses a "Network" entry
    private static WifiEntry readNetworkEntry(XmlPullParser parser) throws XmlPullParserException, IOException {
        parser.require(XmlPullParser.START_TAG, null, "Network");
        WifiEntry result = new WifiEntry();
        while (parser.next() != XmlPullParser.END_TAG) {
            if (parser.getEventType() != XmlPullParser.START_TAG) {
                continue;
            }
            String tagName = parser.getName();
            // Starts by looking for the entry tag
            if (tagName.equals("WifiConfiguration")) {
                result = readWiFiConfig(parser, result);
            } else if (tagName.equals("WifiEnterpriseConfiguration")) {
                result.type = WifiEntry.Type.ENTERPRISE;
            } else {
                skip(parser);
            }
        }
        return result;
    }

    // Parses a "WifiConfiguration" entry
    private static WifiEntry readWiFiConfig(XmlPullParser parser, WifiEntry result) throws XmlPullParserException, IOException {
        try {
            while (parser.next() != XmlPullParser.END_TAG) {
                if (parser.getEventType() != XmlPullParser.START_TAG) {
                    continue;
                }
                String tagName = parser.getName();
                String name = parser.getAttributeValue(null, "name");
                if (name.equals("SSID") && !tagName.equalsIgnoreCase("null")) {
                    result.setTitle(readTag(parser, tagName));
                } else if (name.equals("PreSharedKey") && !tagName.equalsIgnoreCase("null")) {
                    String newPwd = readTag(parser, tagName);
                    if (newPwd.length() > 0) {
                        result.setPassword(newPwd);
                  result.type = WifiEntry.Type.WPA;
                    }
                } else if (name.equals("WEPKeys") && !tagName.equalsIgnoreCase("null")) {
                    result.type = WifiEntry.Type.WEP;
                    if (tagName.equalsIgnoreCase("string-array")) {
                        try {
                            int numQty = Integer.parseInt(parser.getAttributeValue(null, "num"));
                            int loopQty = 0;
                            while ((parser.next() != XmlPullParser.END_DOCUMENT) && (loopQty < numQty)) {
                                String innerTagName = parser.getName();
                                if ((innerTagName != null) && innerTagName.equalsIgnoreCase("item")) {
                                    loopQty ++;
                                    String newPwd = parser.getAttributeValue(null, "value");
                                    if (newPwd.length() > 0) {
                                        result.setPassword(newPwd);
                                    }
                                }
                            }
                        } catch (Exception error) {
                            parser.getName();
                        }
                    } else {
                        String newPwd = readTag(parser, tagName);
                        if (newPwd.length() > 0) {
                            result.setPassword(readTag(parser, tagName));
                        }
                    }
                } else {
                    skip(parser);
                }
            }
        } catch (Exception error) {
            Log.e("LoadData.readWiFiConfig", error.getMessage() + "\n\nParser: " + parser.getText());
        }
        return result;
    }

    // Return the text for a specified tag.
    private static String readTag(XmlPullParser parser, String tagName) throws IOException, XmlPullParserException {
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
    private static void skip(XmlPullParser parser) throws XmlPullParserException, IOException {
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

    public static ArrayList<WifiEntry> readOreoFile() {
        ArrayList<WifiEntry> result = new ArrayList<>();
        try {
            Process suOreoProcess = Runtime.getRuntime().exec("su");
            OutputStream suOreoProcessOutputStream = suOreoProcess.getOutputStream();
            suOreoProcessOutputStream.write(("/system/bin/cat "+ mOreoLocation +"\n").getBytes());
            suOreoProcessOutputStream.write("exit\n".getBytes());
            suOreoProcessOutputStream.flush();
            suOreoProcessOutputStream.close();
            try {
                suOreoProcess.waitFor();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            XmlPullParser parser = Xml.newPullParser();
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false);
            parser.setInput(suOreoProcess.getInputStream(), "UTF-8");
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
        }

        return result;
    }
}


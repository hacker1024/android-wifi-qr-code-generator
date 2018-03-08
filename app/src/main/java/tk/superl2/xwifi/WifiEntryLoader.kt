package tk.superl2.xwifi

import android.util.Xml

import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException
import java.io.IOException

object WifiEntryLoader {
    private const val mOreoLocation = "/data/misc/wifi/WifiConfigStore.xml"

    //****************************************************//
    //****************** Helper Methods ******************//
    //****************************************************//

    @Throws(XmlPullParserException::class, IOException::class)
    private fun readNetworkList(parser: XmlPullParser): ArrayList<WifiEntry> {
        val result = ArrayList<WifiEntry>()
        parser.require(XmlPullParser.START_TAG, null, "NetworkList")
        var doLoop = true
        while (doLoop) {
            parser.next()
            var tagName: String? = parser.name
            if (tagName == null) {
                tagName = ""
            }
            doLoop = !tagName.equals("NetworkList", ignoreCase = true)
            if (parser.eventType != XmlPullParser.START_TAG) {
                continue
            }

            if (tagName == "Network") {
                val newWifi = readNetworkEntry(parser)
                if (newWifi.title.length != 0) {
                    result.add(newWifi)
                }
            } else {
                skip(parser)
            }
        }
        return result
    }

    // Parses a "Network" entry
    @Throws(XmlPullParserException::class, IOException::class)
    private fun readNetworkEntry(parser: XmlPullParser): WifiEntry {
        parser.require(XmlPullParser.START_TAG, null, "Network")
        var result = WifiEntry("", "")
        while (parser.next() != XmlPullParser.END_TAG) {
            if (parser.eventType != XmlPullParser.START_TAG) {
                continue
            }
            val tagName = parser.name
            // Starts by looking for the entry tag
            if (tagName == "WifiConfiguration") {
                result = readWiFiConfig(parser, result)
                //            } else if (tagName.equals("WifiEnterpriseConfiguration")) {
                //                result.setTyp(WifiObject.TYP_ENTERPRISE);
            } else {
                skip(parser)
            }
        }
        return result
    }

    // Parses a "WifiConfiguration" entry
    @Throws(XmlPullParserException::class, IOException::class)
    private fun readWiFiConfig(parser: XmlPullParser, result: WifiEntry): WifiEntry {
        while (parser.next() != XmlPullParser.END_TAG) {
            if (parser.eventType != XmlPullParser.START_TAG) {
                continue
            }
            val tagName = parser.name
            val name = parser.getAttributeValue(null, "name")
            if (name == "SSID" && !tagName.equals("null", ignoreCase = true)) {
                result.title = readTag(parser, tagName)
            } else if (name == "PreSharedKey" && !tagName.equals("null", ignoreCase = true)) {
                result.setPassword(readTag(parser, tagName))
                //                result.setTyp(WifiObject.TYP_WPA);
            } else if (name == "WEPKeys" && !tagName.equals("null", ignoreCase = true)) {
                result.setPassword(readTag(parser, tagName))
                //                result.setTyp(WifiObject.TYP_WEP);
            } else {
                skip(parser)
            }
        }
        return result
    }

    // Return the text for a specified tag.
    @Throws(IOException::class, XmlPullParserException::class)
    private fun readTag(parser: XmlPullParser, tagName: String): String {
        parser.require(XmlPullParser.START_TAG, null, tagName)
        var result = ""
        if (parser.next() == XmlPullParser.TEXT) {
            result = parser.text
            parser.nextTag()
        }
        parser.require(XmlPullParser.END_TAG, null, tagName)
        if (tagName.equals("string", ignoreCase = true) && Character.toString(result[0]) == "\"") {
            result = result.substring(1, result.length - 1)
        }
        return result
    }

    // Skips tags the parser isn't interested in. Uses depth to handle nested tags. i.e.,
    // if the next tag after a START_TAG isn't a matching END_TAG, it keeps going until it
    // finds the matching END_TAG (as indicated by the value of "depth" being 0).
    @Throws(XmlPullParserException::class, IOException::class)
    private fun skip(parser: XmlPullParser) {
        if (parser.eventType != XmlPullParser.START_TAG) {
            throw IllegalStateException()
        }
        var depth = 1
        while (depth != 0) {
            when (parser.next()) {
                XmlPullParser.END_TAG -> depth--
                XmlPullParser.START_TAG -> depth++
            }
        }
    }

    fun readOreoFile(): ArrayList<WifiEntry>? {
        val result = ArrayList<WifiEntry>()
        try {
            val suOreoProcess = Runtime.getRuntime().exec("su -c /system/bin/cat " + mOreoLocation)
            try {
                suOreoProcess.waitFor()
            } catch (e: InterruptedException) {
                e.printStackTrace()
            }

            val parser = Xml.newPullParser()
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
            parser.setInput(suOreoProcess.inputStream, null)
            while (parser.next() != XmlPullParser.END_DOCUMENT) {
                if (parser.eventType != XmlPullParser.START_TAG) {
                    continue
                }
                if (parser.name.equals("NetworkList", ignoreCase = true)) {
                    // Process the <Network> entries in the list
                    result.addAll(readNetworkList(parser))
                }
            }
        } catch (e: IOException) {
            e.printStackTrace()
        } catch (e: XmlPullParserException) {
            e.printStackTrace()
        } finally {
            return if (!result.isEmpty()) {
                result
            } else {
                null
            }
        }
    }
}

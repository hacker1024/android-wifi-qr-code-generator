package tk.superl2.xwifi

import android.util.Log
import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException
import java.io.IOException
import java.util.*

object WifiEntryLoader {
    private const val mOreoLocation = "/data/misc/wifi/WifiConfigStore.xml"

    @Throws(XmlPullParserException::class, IOException::class)
    private fun readNetworkList(parser: XmlPullParser): ArrayList<WifiEntry> {
        val result = ArrayList<WifiEntry>()
        parser.require(XmlPullParser.START_TAG, null, "NetworkList")
        var doLoop = true
        while (doLoop) {
            try {
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
                    if (newWifi.title.isNotEmpty()) {
                        result.add(newWifi)
                    }
                } else {
                    skip(parser)
                }
            } catch (e: Exception) {
                Log.e("LoadData.NetworkList", e.message)
                doLoop = false
            }

        }
        return result
    }

    // Parses a "Network" entry
    @Throws(XmlPullParserException::class, IOException::class)
    private fun readNetworkEntry(parser: XmlPullParser): WifiEntry {
        parser.require(XmlPullParser.START_TAG, null, "Network")
        var result = WifiEntry()
        while (parser.next() != XmlPullParser.END_TAG) {
            if (parser.eventType != XmlPullParser.START_TAG) {
                continue
            }
            val tagName = parser.name
            // Starts by looking for the entry tag
            when (tagName) {
                "WifiConfiguration" -> result = readWiFiConfig(parser, result)
                "WifiEnterpriseConfiguration" -> result.type = WifiEntry.Type.ENTERPRISE
                else -> skip(parser)
            }
        }
        return result
    }

    // Parses a "WifiConfiguration" entry
    @Throws(XmlPullParserException::class, IOException::class)
    private fun readWiFiConfig(parser: XmlPullParser, result: WifiEntry): WifiEntry {
        try {
            while (parser.next() != XmlPullParser.END_TAG) {
                if (parser.eventType != XmlPullParser.START_TAG) {
                    continue
                }
                val tagName = parser.name
                val name = parser.getAttributeValue(null, "name")
                if (name == "SSID" && !tagName.equals("null", ignoreCase = true)) {
                    result.title = readTag(parser, tagName)
                } else if (name == "PreSharedKey" && !tagName.equals("null", ignoreCase = true)) {
                    val newPwd = readTag(parser, tagName)
                    if (newPwd.isNotEmpty()) {
                        result.password = newPwd
                        result.type = WifiEntry.Type.WPA
                    }
                } else if (name == "WEPKeys" && !tagName.equals("null", ignoreCase = true)) {
                    result.type = WifiEntry.Type.WEP
                    if (tagName.equals("string-array", ignoreCase = true)) {
                        try {
                            val numQty = Integer.parseInt(parser.getAttributeValue(null, "num"))
                            var loopQty = 0
                            while (parser.next() != XmlPullParser.END_DOCUMENT && loopQty < numQty) {
                                val innerTagName = parser.name
                                if (innerTagName != null && innerTagName.equals("item", ignoreCase = true)) {
                                    loopQty++
                                    val newPwd = parser.getAttributeValue(null, "value")
                                    if (newPwd.isNotEmpty()) {
                                        result.password = newPwd
                                    }
                                }
                            }
                        } catch (error: Exception) {
                            parser.name
                        }

                    } else {
                        val newPwd = readTag(parser, tagName)
                        if (newPwd.isNotEmpty()) {
                            result.password = readTag(parser, tagName)
                        }
                    }
                } else {
                    skip(parser)
                }
            }
        } catch (error: Exception) {
            Log.e("LoadData.readWiFiConfig", error.message + "\n\nParser: " + parser.text)
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

    fun readOreoFile(): ArrayList<WifiEntry> {
        val result = ArrayList<WifiEntry>()
        try {
            val suOreoProcess = Runtime.getRuntime().exec("su")
            val suOreoProcessOutputStream = suOreoProcess.outputStream
            suOreoProcessOutputStream.write("/system/bin/cat $mOreoLocation\n".toByteArray())
            suOreoProcessOutputStream.write("exit\n".toByteArray())
            suOreoProcessOutputStream.flush()
            suOreoProcessOutputStream.close()
            try {
                suOreoProcess.waitFor()
            } catch (e: InterruptedException) {
                e.printStackTrace()
            }

            val parser = Xml.newPullParser()
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
            parser.setInput(suOreoProcess.inputStream, "UTF-8")
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
            return if (result.isNotEmpty()) {
                result
            } else {
                throw WifiUnparseableException()
            }
        }
    }
}

class WifiUnparseableException(message: String = "Wifi list could not be parsed."): Exception(message)
package hudson.plugins.accurev.parsers.xml

import hudson.plugins.accurev.AccurevElement
import hudson.plugins.accurev.AccurevLauncher
import hudson.plugins.accurev.AccurevUtils.Companion.cleanAccurevPath
import org.xmlpull.v1.XmlPullParser

class ParseFiles : AccurevLauncher.ICmdOutputXmlParser<Boolean, ArrayList<AccurevElement>> {
    override fun parse(parser: XmlPullParser?, context: ArrayList<AccurevElement>?): Boolean {
        while (parser?.next() != XmlPullParser.END_DOCUMENT) {
            if (parser?.eventType == XmlPullParser.START_TAG) {
                if ("element".equals(parser.name, ignoreCase = true)) {
                    val location = cleanAccurevPath(parser.getAttributeValue("", "location"))
                    val status = parser.getAttributeValue("", "status")
                    context?.add(AccurevElement(location, status))
                }
            }
        }
        return context != null
    }
}

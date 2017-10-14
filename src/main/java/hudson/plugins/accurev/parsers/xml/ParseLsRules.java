package hudson.plugins.accurev.parsers.xml;

import java.io.IOException;
import java.util.HashMap;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import hudson.plugins.accurev.AccurevLauncher.ICmdOutputXmlParser;
import hudson.plugins.accurev.AccurevLauncher.UnhandledAccurevCommandOutput;

final class ParseLsRules implements ICmdOutputXmlParser<HashMap<String, String>, Void> {
    public HashMap<String, String> parse(final XmlPullParser parser, final Void context)
        throws UnhandledAccurevCommandOutput, IOException, XmlPullParserException {
        // Parse the 'accurev lsrules' command, and build up the
        // include/exclude rules map
        final HashMap<String, String> locationToKindMap = new HashMap<>();
        // key: String location, val: String kind (incl / excl / incldo)
        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            if (parser.getEventType() == XmlPullParser.START_TAG && "element".equalsIgnoreCase(parser.getName())) {
                String kind = parser.getAttributeValue("", "kind");
                String location = parser.getAttributeValue("", "location");
                if (location != null && kind != null) {
                    locationToKindMap.put(location, kind);
                }
            }
        }
        return locationToKindMap;
    }
}

package hudson.plugins.accurev.parsers.xml;

import hudson.plugins.accurev.AccurevLauncher.ICmdOutputXmlParser;
import hudson.plugins.accurev.AccurevLauncher.UnhandledAccurevCommandOutput;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.util.HashMap;

final class ParseLsRules implements ICmdOutputXmlParser<HashMap<String, String>, Void> {
    public HashMap<String, String> parse(final XmlPullParser parser, final Void context)
            throws UnhandledAccurevCommandOutput, IOException, XmlPullParserException {
        // Parse the 'accurev lsrules' command, and build up the
        // include/exclude rules map
        final HashMap<String, String> locationToKindMap = new HashMap<>();
        // key: String location, val: String kind (incl / excl / incldo)
        while (true) {
            switch (parser.next()) {
                case XmlPullParser.START_DOCUMENT:
                    break;
                case XmlPullParser.START_TAG:
                    final String tagName = parser.getName();
                    if ("element".equalsIgnoreCase(tagName)) {
                        String kind = parser.getAttributeValue("", "kind");
                        String location = parser.getAttributeValue("", "location");
                        if (location != null && kind != null) {
                            locationToKindMap.put(location, kind);
                        }
                    }
                    break;
                case XmlPullParser.END_TAG:
                    break;
                case XmlPullParser.TEXT:
                    break;
                case XmlPullParser.END_DOCUMENT:
                    return locationToKindMap;
            }
        }
    }
}

package hudson.plugins.accurev.parsers.xml;

import hudson.plugins.accurev.AccurevLauncher.ICmdOutputXmlParser;
import hudson.plugins.accurev.AccurevLauncher.UnhandledAccurevCommandOutput;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

final class ParseStatOverlaps implements ICmdOutputXmlParser<List<String>, Void> {
    public List<String> parse(XmlPullParser parser, Void context) throws UnhandledAccurevCommandOutput, IOException,
            XmlPullParserException {
        final List<String> overlaps = new ArrayList<>();
        while (true) {
            switch (parser.next()) {
                case XmlPullParser.START_DOCUMENT:
                    break;
                case XmlPullParser.END_DOCUMENT:
                    // build the tree
                    return overlaps;
                case XmlPullParser.START_TAG:
                    final String tagName = parser.getName();
                    if ("element".equalsIgnoreCase(tagName)) {
                        final String filename = parser.getAttributeValue("", "location");
                        final String dir = parser.getAttributeValue("", "dir"); // yes or no
                        if ("no".equalsIgnoreCase(dir)) {
                            overlaps.add(filename);
                        } else {
                            // don't add dirs to overlap list
                        }
                    }
                    break;
                case XmlPullParser.END_TAG:
                    break;
                case XmlPullParser.TEXT:
                    break;
            }
        }
    }
}

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
        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            if (parser.getEventType() == XmlPullParser.START_TAG && "element".equalsIgnoreCase(parser.getName())) {
                final String filename = parser.getAttributeValue("", "location");
                final String dir = parser.getAttributeValue("", "dir"); // yes or no
                if ("no".equalsIgnoreCase(dir)) { // only add files which is answered by the dir attribute in yes or no, no meaning it is a file.
                    overlaps.add(filename);
                }
            }
        }
        return overlaps;
    }
}

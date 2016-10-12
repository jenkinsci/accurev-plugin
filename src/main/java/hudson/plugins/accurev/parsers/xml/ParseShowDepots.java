package hudson.plugins.accurev.parsers.xml;


import hudson.plugins.accurev.AccurevLauncher.ICmdOutputXmlParser;
import hudson.plugins.accurev.AccurevLauncher.UnhandledAccurevCommandOutput;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

public final class ParseShowDepots implements ICmdOutputXmlParser<List<String>, Void> {
    public List<String> parse(XmlPullParser parser, Void context) throws UnhandledAccurevCommandOutput,
            IOException, XmlPullParserException {
        final List<String> depots = new ArrayList<>();
        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            if (parser.getEventType() == XmlPullParser.START_TAG && "element".equalsIgnoreCase(parser.getName())) {
                final String name = parser.getAttributeValue("", "Name");
                if (name != null) depots.add(name);
            }
        }
        return depots;
    }
}

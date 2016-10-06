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
    private static final Logger logger = Logger.getLogger(ParseShowStreams.class.getName());

    public List<String> parse(XmlPullParser parser, Void context) throws UnhandledAccurevCommandOutput,
            IOException, XmlPullParserException {

        final List<String> depots = new ArrayList<>();
        while (true) {
            switch (parser.next()) {
                case XmlPullParser.START_DOCUMENT:
                    break;
                case XmlPullParser.END_DOCUMENT:
                    return depots;
                case XmlPullParser.START_TAG:
                    final String tagName = parser.getName();

                    if ("element".equalsIgnoreCase(tagName)) {
                        final String name = parser.getAttributeValue("", "Name");
                        if (name != null) depots.add(name);
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

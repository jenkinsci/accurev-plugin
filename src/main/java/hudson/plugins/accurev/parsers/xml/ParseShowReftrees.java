package hudson.plugins.accurev.parsers.xml;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import hudson.plugins.accurev.AccurevLauncher.ICmdOutputXmlParser;
import hudson.plugins.accurev.AccurevLauncher.UnhandledAccurevCommandOutput;
import hudson.plugins.accurev.AccurevReferenceTree;

@Deprecated
public class ParseShowReftrees implements ICmdOutputXmlParser<Map<String, AccurevReferenceTree>, Void> {
    public Map<String, AccurevReferenceTree> parse(XmlPullParser parser, Void context)
        throws UnhandledAccurevCommandOutput, IOException, XmlPullParserException {
        final Map<String, AccurevReferenceTree> reftrees = new HashMap<>();
        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            if (parser.getEventType() == XmlPullParser.START_TAG && "Element".equalsIgnoreCase(parser.getName())) {
                final String name = parser.getAttributeValue("", "Name");
                final String storage = parser.getAttributeValue("", "Storage");
                final String host = parser.getAttributeValue("", "Host");
                final String streamNumber = parser.getAttributeValue("", "Stream");
                final String depot = parser.getAttributeValue("", "depot");
                try {
                    final Long streamNumberOrNull = streamNumber == null ? null : Long.valueOf(streamNumber);
                    reftrees.put(name, new AccurevReferenceTree(depot, streamNumberOrNull, name, host, storage));
                } catch (NumberFormatException e) {
                    throw new UnhandledAccurevCommandOutput(e);
                }
            }
        }
        return reftrees;
    }
}

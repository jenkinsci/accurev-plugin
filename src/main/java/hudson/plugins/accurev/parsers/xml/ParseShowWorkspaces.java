package hudson.plugins.accurev.parsers.xml;

import hudson.plugins.accurev.AccurevLauncher.ICmdOutputXmlParser;
import hudson.plugins.accurev.AccurevLauncher.UnhandledAccurevCommandOutput;
import hudson.plugins.accurev.AccurevWorkspace;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public final class ParseShowWorkspaces implements ICmdOutputXmlParser<Map<String, AccurevWorkspace>, Void> {
    public Map<String, AccurevWorkspace> parse(XmlPullParser parser, Void context)
            throws UnhandledAccurevCommandOutput, IOException, XmlPullParserException {
        final Map<String, AccurevWorkspace> workspaces = new HashMap<>();
        while (true) {
            switch (parser.next()) {
                case XmlPullParser.START_DOCUMENT:
                    break;
                case XmlPullParser.END_DOCUMENT:
                    return workspaces;
                case XmlPullParser.START_TAG:
                    final String tagName = parser.getName();
                    if ("Element".equalsIgnoreCase(tagName)) {
                        final String name = parser.getAttributeValue("", "Name");
                        final String storage = parser.getAttributeValue("", "Storage");
                        final String host = parser.getAttributeValue("", "Host");
                        final String streamNumber = parser.getAttributeValue("", "Stream");
                        final String depot = parser.getAttributeValue("", "depot");
                        try {
                            final Long streamNumberOrNull = streamNumber == null ? null : Long.valueOf(streamNumber);
                            workspaces.put(name, new AccurevWorkspace(depot, streamNumberOrNull, name, host, storage));
                        } catch (NumberFormatException e) {
                            throw new UnhandledAccurevCommandOutput(e);
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

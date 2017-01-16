package hudson.plugins.accurev.parsers.xml;


import hudson.plugins.accurev.AccurevLauncher.ICmdOutputXmlParser;
import hudson.plugins.accurev.AccurevLauncher.UnhandledAccurevCommandOutput;
import hudson.plugins.accurev.config.AccurevServerConfig;
import hudson.plugins.accurev.extensions.impl.AccurevDepot;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.math.NumberUtils;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public final class ParseShowDepots implements ICmdOutputXmlParser<Map<String, AccurevDepot>, AccurevServerConfig> {
    public Map<String, AccurevDepot> parse(XmlPullParser parser, AccurevServerConfig config) throws UnhandledAccurevCommandOutput,
            IOException, XmlPullParserException {
        final Map<String, AccurevDepot> depots = new HashMap<>();

        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            if (parser.getEventType() == XmlPullParser.START_TAG && "element".equalsIgnoreCase(parser.getName())) {
                final int number = NumberUtils.toInt(parser.getAttributeValue("", "Number"), 0);
                final String name = parser.getAttributeValue("", "Name");
                final boolean caseSensitive = StringUtils
                        .equals("sensitive",
                                parser.getAttributeValue("", "case"));
                AccurevDepot depot = new AccurevDepot(number, name, caseSensitive, config);
                depots.put(depot.getName(), depot);
            }
        }
        return depots;
    }
}

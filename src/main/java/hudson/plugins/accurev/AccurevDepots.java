package hudson.plugins.accurev;

import java.io.IOException;
import java.util.HashMap;

import org.apache.commons.lang.math.NumberUtils;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import jenkins.plugins.accurev.AccurevException;
import jenkins.plugins.accurev.util.Parser;

public class AccurevDepots extends HashMap<String, AccurevDepot> {
    public AccurevDepots(String result) throws AccurevException {
        parse(result);
    }

    private void parse(String result) throws AccurevException {
        try {
            XmlPullParser parser = Parser.parse(result);
            while (parser.next() != XmlPullParser.END_DOCUMENT) {
                if (parser.getEventType() == XmlPullParser.START_TAG && "element".equalsIgnoreCase(parser.getName())) {
                    final String name = parser.getAttributeValue("", "Name");
                    final int number = NumberUtils.toInt(parser.getAttributeValue("", "Number"), 1);
                    if (name != null) this.put(name, new AccurevDepot(name, number));
                }
            }
        } catch (IOException | XmlPullParserException e) {
            throw new AccurevException("Failed to get depots", e);
        }
    }
}

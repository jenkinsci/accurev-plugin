package hudson.plugins.accurev.parsers.xml;

import hudson.plugins.accurev.AccurevLauncher;
import hudson.plugins.accurev.AccurevUtils;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.util.List;

/**
 * @author raymond
 */
public class ParseUpdate implements AccurevLauncher.ICmdOutputXmlParser<Boolean, List<String>> {
    public Boolean parse(XmlPullParser parser, List<String> context) throws AccurevLauncher.UnhandledAccurevCommandOutput, IOException, XmlPullParserException {
        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            if (parser.getEventType() == XmlPullParser.START_TAG && "element".equalsIgnoreCase(parser.getName())) {
                String path = parser.getAttributeValue("", "location");
                if (path != null) {
                    context.add(AccurevUtils.cleanAccurevPath(path));
                }
            }
        }
        return !context.isEmpty();
    }

}

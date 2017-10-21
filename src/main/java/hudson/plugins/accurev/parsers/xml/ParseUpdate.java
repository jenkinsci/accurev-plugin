package hudson.plugins.accurev.parsers.xml;

import static jenkins.plugins.accurev.util.AccurevUtils.cleanAccurevPath;

import java.io.IOException;
import java.util.List;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import hudson.plugins.accurev.AccurevLauncher;

/**
 * @author raymond
 */
@Deprecated
public class ParseUpdate implements AccurevLauncher.ICmdOutputXmlParser<Boolean, List<String>> {
    public Boolean parse(XmlPullParser parser, List<String> context) throws AccurevLauncher.UnhandledAccurevCommandOutput, IOException, XmlPullParserException {
        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            if (parser.getEventType() == XmlPullParser.START_TAG && "element".equalsIgnoreCase(parser.getName())) {
                String path = parser.getAttributeValue("", "location");
                if (path != null) {
                    context.add(cleanAccurevPath(path));
                }
            }
        }
        return !context.isEmpty();
    }

}

package hudson.plugins.accurev.parsers.xml;

import hudson.plugins.accurev.AccurevLauncher;
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
            if (parser.getEventType() == XmlPullParser.START_TAG) {
                if (parser.getName().equalsIgnoreCase("element")) {
                    String path = parser.getAttributeValue("", "location");
                    if (path != null) {
                        path = path.replace("\\", "/");
                        if (path.startsWith("/./")) {
                            path = path.substring(3);
                        }
                    }
                    context.add(path);
                }
            }
        }
        return !context.isEmpty();
    }

}

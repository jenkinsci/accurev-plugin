package hudson.plugins.accurev.parsers.xml;

import static jenkins.plugins.accurev.util.AccurevUtils.cleanAccurevPath;

import java.io.IOException;
import java.util.List;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import hudson.plugins.accurev.AccurevElement;
import hudson.plugins.accurev.AccurevLauncher;

public class ParseFiles implements AccurevLauncher.ICmdOutputXmlParser<Boolean, List<AccurevElement>> {

    @Override
    public Boolean parse(XmlPullParser parser, List<AccurevElement> context) throws AccurevLauncher.UnhandledAccurevCommandOutput, IOException, XmlPullParserException {
        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            if (parser.getEventType() == XmlPullParser.START_TAG) {
                if ("element".equals(parser.getName())) {
                    String location = cleanAccurevPath(parser.getAttributeValue("", "location"));
                    String status = parser.getAttributeValue("", "status");
                    context.add(new AccurevElement(location, status));
                }
            }
        }
        return context != null;
    }
}

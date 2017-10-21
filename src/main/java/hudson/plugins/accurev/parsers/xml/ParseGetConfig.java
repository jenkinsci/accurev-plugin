package hudson.plugins.accurev.parsers.xml;

import java.io.EOFException;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import hudson.plugins.accurev.AccurevLauncher.ICmdOutputXmlParser;
import hudson.plugins.accurev.AccurevLauncher.UnhandledAccurevCommandOutput;
import hudson.plugins.accurev.GetConfigWebURL;

@Deprecated
public class ParseGetConfig implements ICmdOutputXmlParser<Map<String, GetConfigWebURL>, Void> {

    public Map<String, GetConfigWebURL> parse(XmlPullParser parser, Void context)
        throws UnhandledAccurevCommandOutput, IOException, XmlPullParserException {
        final Map<String, GetConfigWebURL> getConfig = new HashMap<>();
        getConfig.put("webuiURL", new GetConfigWebURL(""));
        try {
            while (parser.next() != XmlPullParser.END_DOCUMENT) {
                if (parser.getEventType() == XmlPullParser.START_TAG && "webui".equalsIgnoreCase(parser.getName())) {
                    final String webURL = parser.getAttributeValue("", "url");
                    if (webURL != null) getConfig.put("webuiURL", new GetConfigWebURL(webURL));
                }
            }
        } catch (EOFException ignored) {
            //file not found
        }
        return getConfig;
    }
}

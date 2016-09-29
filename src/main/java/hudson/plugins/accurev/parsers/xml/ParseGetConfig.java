package hudson.plugins.accurev.parsers.xml;

import hudson.plugins.accurev.AccurevLauncher.ICmdOutputXmlParser;
import hudson.plugins.accurev.AccurevLauncher.UnhandledAccurevCommandOutput;
import hudson.plugins.accurev.GetConfigWebURL;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class ParseGetConfig implements ICmdOutputXmlParser<Map<String, GetConfigWebURL>, Void> {

    public Map<String, GetConfigWebURL> parse(XmlPullParser parser, Void context)
            throws UnhandledAccurevCommandOutput, IOException, XmlPullParserException {
        final Map<String, GetConfigWebURL> getConfig = new HashMap<>();
        try {
            if (parser != null) {
                while (true) {
                    switch (parser.next()) {
                        case XmlPullParser.START_DOCUMENT:
                            break;
                        case XmlPullParser.END_DOCUMENT:
                            return getConfig;
                        case XmlPullParser.START_TAG:
                            final String tagName = parser.getName();
                            if ("webui".equalsIgnoreCase(tagName)) {
                                final String webURL = parser.getAttributeValue("", "url");
                                try {
                                    getConfig.put("webuiURL", new GetConfigWebURL(webURL));
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
            } else {
                getConfig.put("webuiURL", new GetConfigWebURL(""));
            }
        } catch (UnhandledAccurevCommandOutput | XmlPullParserException | IOException e) {

        }
        return getConfig;
    }
}

package hudson.plugins.accurev.parsers.xml;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import hudson.plugins.accurev.AccurevLauncher.ICmdOutputXmlParser;
import hudson.plugins.accurev.AccurevLauncher.UnhandledAccurevCommandOutput;
import hudson.plugins.accurev.RefTreeExternalFile;

@Deprecated
public class ParseRefTreeExternalFile implements ICmdOutputXmlParser<Map<String, RefTreeExternalFile>, Void> {
    public Map<String, RefTreeExternalFile> parse(XmlPullParser parser, Void context)
        throws UnhandledAccurevCommandOutput, IOException, XmlPullParserException {
        final Map<String, RefTreeExternalFile> externalFiles = new HashMap<>();
        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            if (parser.getEventType() == XmlPullParser.START_TAG && "Element".equalsIgnoreCase(parser.getName())) {
                final String location = parser.getAttributeValue("", "location");
                final String status = parser.getAttributeValue("", "status");
                try {
                    externalFiles.put(location, new RefTreeExternalFile(location, status));
                } catch (NumberFormatException e) {
                    throw new UnhandledAccurevCommandOutput(e);
                }
            }
        }
        return externalFiles;
    }
}

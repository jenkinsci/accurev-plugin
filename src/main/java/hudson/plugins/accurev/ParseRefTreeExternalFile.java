package hudson.plugins.accurev;

import hudson.plugins.accurev.AccurevLauncher.ICmdOutputXmlParser;
import hudson.plugins.accurev.AccurevLauncher.UnhandledAccurevCommandOutput;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

public class ParseRefTreeExternalFile implements ICmdOutputXmlParser<Map<String, RefTreeExternalFile>, Void> {
    public Map<String, RefTreeExternalFile> parse(XmlPullParser parser, Void context)
            throws UnhandledAccurevCommandOutput, IOException, XmlPullParserException {
    	final Map<String, RefTreeExternalFile> externalFiles = new HashMap<String, RefTreeExternalFile>();
        
        while (true) {
            switch (parser.next()) {
            case XmlPullParser.START_DOCUMENT:
                break;
            case XmlPullParser.END_DOCUMENT:
                return externalFiles;
            case XmlPullParser.START_TAG:
                final String tagName = parser.getName();
                if ("Element".equalsIgnoreCase(tagName)) {
                    final String location = parser.getAttributeValue("", "location");
                    final String status = parser.getAttributeValue("", "status");                    
                    try {                        
                        externalFiles.put(location, new RefTreeExternalFile(location, status));
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

package hudson.plugins.accurev.parsers.xml;

import hudson.plugins.accurev.AccurevLauncher.ICmdOutputXmlParser;
import hudson.plugins.accurev.AccurevLauncher.UnhandledAccurevCommandOutput;
import hudson.plugins.accurev.AccurevTransaction;
import hudson.plugins.accurev.ParseChangeLog;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.util.List;

public final class ParseHistory implements ICmdOutputXmlParser<Boolean, List<AccurevTransaction>> {
    public Boolean parse(XmlPullParser parser, List<AccurevTransaction> context) throws UnhandledAccurevCommandOutput,
        IOException, XmlPullParserException {
        AccurevTransaction resultTransaction = null;
        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            if (parser.getEventType() == XmlPullParser.START_TAG) {
                if ("transaction".equalsIgnoreCase(parser.getName())) {
                    resultTransaction = new AccurevTransaction();
                    // parse transaction-values
                    resultTransaction.setId((parser.getAttributeValue("", "id")));
                    resultTransaction.setAction(parser.getAttributeValue("", "type"));
                    resultTransaction.setDate(ParseChangeLog.convertAccurevTimestamp(parser.getAttributeValue("",
                        "time")));
                    resultTransaction.setUser(parser.getAttributeValue("", "user"));
                } else if ("comment".equalsIgnoreCase(parser.getName()) && resultTransaction != null) {
                    // parse comments
                    resultTransaction.setMsg(parser.nextText());
                } else if ("version".equalsIgnoreCase(parser.getName()) && resultTransaction != null) {
                    // parse path & convert it to standard format
                    String path = parser.getAttributeValue("", "path");
                    if (path != null) {
                        path = path.replace("\\", "/");
                        if (path.startsWith("/./")) {
                            path = path.substring(3);
                        }
                    }
                    resultTransaction.addAffectedPath(path);
                }
            }
        }
        context.add(resultTransaction);
        return resultTransaction != null;
    }

    public Boolean parseAll(XmlPullParser parser, List<AccurevTransaction> context) throws UnhandledAccurevCommandOutput, IOException, XmlPullParserException {
        AccurevTransaction resultTransaction = null;
        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            if (parser.getEventType() == XmlPullParser.START_TAG) {
                if ("transaction".equalsIgnoreCase(parser.getName())) {
                    resultTransaction = new AccurevTransaction();
                    // parse transaction-values
                    resultTransaction.setId((parser.getAttributeValue("", "id")));
                    resultTransaction.setAction(parser.getAttributeValue("", "type"));
                    resultTransaction.setDate(ParseChangeLog.convertAccurevTimestamp(parser.getAttributeValue("",
                        "time")));
                    resultTransaction.setUser(parser.getAttributeValue("", "user"));
                } else if ("comment".equalsIgnoreCase(parser.getName()) && resultTransaction != null) {
                    // parse comments
                    resultTransaction.setMsg(parser.nextText());
                } else if ("version".equalsIgnoreCase(parser.getName()) && resultTransaction != null) {
                    String path = parser.getAttributeValue("", "path");
                    if (path != null)
                        resultTransaction.addAffectedPath(path);
                }
            }
            context.add(resultTransaction);
        }
        return context != null;
    }
}

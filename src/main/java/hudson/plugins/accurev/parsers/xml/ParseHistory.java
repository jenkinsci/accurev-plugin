package hudson.plugins.accurev.parsers.xml;

import static jenkins.plugins.accurev.util.AccurevUtils.convertAccurevTimestamp;

import hudson.plugins.accurev.AccurevLauncher.ICmdOutputXmlParser;
import hudson.plugins.accurev.AccurevLauncher.UnhandledAccurevCommandOutput;
import hudson.plugins.accurev.AccurevTransaction;
import java.io.IOException;
import java.util.List;
import jenkins.plugins.accurev.util.AccurevUtils;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

public final class ParseHistory implements ICmdOutputXmlParser<Boolean, List<AccurevTransaction>> {

  public Boolean parse(XmlPullParser parser, List<AccurevTransaction> context)
      throws UnhandledAccurevCommandOutput, IOException, XmlPullParserException {
    AccurevTransaction resultTransaction = null;
    while (parser.next() != XmlPullParser.END_DOCUMENT) {
      if (parser.getEventType() == XmlPullParser.START_TAG) {
        if ("transaction".equalsIgnoreCase(parser.getName())) {
          resultTransaction = new AccurevTransaction();
          // parse transaction-values
          resultTransaction.setId((parser.getAttributeValue("", "id")));
          resultTransaction.setAction(parser.getAttributeValue("", "type"));
          resultTransaction.setDate(convertAccurevTimestamp(parser.getAttributeValue("", "time")));
          resultTransaction.setUser(parser.getAttributeValue("", "user"));
        } else if ("comment".equalsIgnoreCase(parser.getName()) && resultTransaction != null) {
          // parse comments
          resultTransaction.setMsg(parser.nextText());
        } else if ("version".equalsIgnoreCase(parser.getName()) && resultTransaction != null) {
          // parse path & convert it to standard format
          String path = parser.getAttributeValue("", "path");
          if (path != null) {
            path = AccurevUtils.cleanAccurevPath(path);
          }
          resultTransaction.addAffectedPath(path);
        }
      }
    }
    context.add(resultTransaction);
    return resultTransaction != null;
  }

  public Boolean parseAll(XmlPullParser parser, List<AccurevTransaction> context)
      throws IOException, XmlPullParserException {
    int eventType = parser.getEventType();
    while (eventType != XmlPullParser.END_DOCUMENT) {
      // check for the parent tag
      if (eventType == XmlPullParser.START_TAG && "transaction".equals(parser.getName())) {
        AccurevTransaction resultTransaction = new AccurevTransaction();
        resultTransaction.setId((parser.getAttributeValue("", "id")));
        resultTransaction.setAction(parser.getAttributeValue("", "type"));
        resultTransaction.setDate(convertAccurevTimestamp(parser.getAttributeValue("", "time")));
        resultTransaction.setUser(parser.getAttributeValue("", "user"));
        eventType = parser.nextTag();

        // loop the parent tag elements until we reach the end of the parent tag
        while (eventType == XmlPullParser.START_TAG) {
          // check the children tags
          if ("comment".equals(parser.getName())) {
            resultTransaction.setMsg(parser.nextText());
          } else if ("version".equals(parser.getName())) {
            String path = parser.getAttributeValue("", "path");
            if (path != null) {
              path = AccurevUtils.cleanAccurevPath(path);
            }
            resultTransaction.addAffectedPath(path);
            break;
          } else {
            break;
          }
          parser.nextTag();
        }
        context.add(resultTransaction);
      }
      eventType = parser.next();
    }
    return context != null;
  }
}

package hudson.plugins.accurev;

import jenkins.plugins.accurev.AccurevException;
import jenkins.plugins.accurev.util.AccurevUtils;
import jenkins.plugins.accurev.util.Parser;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.util.ArrayList;

/**
 * Created by joseph on 4/10/17.
 */
public class AccurevTransactions extends ArrayList<AccurevTransaction> {
    public AccurevTransactions(String result) {
        parse(result);
    }

    private void parse(String result) {
        try {
            XmlPullParser parser = Parser.parse(result);
            AccurevTransaction resultTransaction = null;
            while (parser.next() != XmlPullParser.END_DOCUMENT) {
                if (parser.getEventType() == XmlPullParser.START_TAG) {
                    if ("transaction".equalsIgnoreCase(parser.getName())) {
                        resultTransaction = new AccurevTransaction();
                        // parse transaction-values
                        resultTransaction.setId((parser.getAttributeValue("", "id")));
                        resultTransaction.setAction(parser.getAttributeValue("", "type"));
                        resultTransaction.setDate(AccurevUtils.convertAccurevTimestamp(parser.getAttributeValue("",
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
                this.add(resultTransaction);
            }
        } catch (XmlPullParserException | IOException e) {
            throw new AccurevException("Failed to get transactions", e);
        }
    }
}

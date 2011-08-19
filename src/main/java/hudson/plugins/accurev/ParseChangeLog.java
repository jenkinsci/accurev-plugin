package hudson.plugins.accurev;

import hudson.model.AbstractBuild;
import hudson.scm.ChangeLogParser;
import hudson.scm.ChangeLogSet;
import hudson.util.IOException2;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.logging.Logger;

import org.xml.sax.SAXException;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

/**
 * Parses a change log that was recorded by {@link ParseOutputToFile}.
 */
class ParseChangeLog extends ChangeLogParser {
    private static final Logger logger = Logger.getLogger(AccurevSCM.class.getName());
    private static final long MILLIS_PER_SECOND = 1000L;

    /**
     * {@inheritDoc}
     */
    public ChangeLogSet<AccurevTransaction> parse(AbstractBuild build, File changelogFile)//
            throws IOException, SAXException {
        List<AccurevTransaction> transactions = null;
        try {
            XmlPullParser parser = XmlParserFactory.newParser();
            FileReader fis = null;
            BufferedReader bis = null;
            try {
                fis = new FileReader(changelogFile);
                bis = new BufferedReader(fis);
                parser.setInput(bis);
                transactions = parseTransactions(parser);
            } finally {
                if (bis != null) {
                    bis.close();
                }
                if (fis != null) {
                    fis.close();
                }
                if (parser != null) {
                    parser.setInput(null);
                }
            }
        } catch (XmlPullParserException e) {
            throw new IOException2(e);
        }

        logger.info("transactions size = " + transactions.size());
        return new AccurevChangeLogSet(build, transactions);
    }

    private List<AccurevTransaction> parseTransactions(XmlPullParser parser) throws IOException, XmlPullParserException {
        List<AccurevTransaction> transactions = new ArrayList<AccurevTransaction>();
        AccurevTransaction currentTransaction = null;
        boolean inComment = false;
        while (true) {
            switch (parser.next()) {
            case XmlPullParser.START_DOCUMENT:
                break;
            case XmlPullParser.END_DOCUMENT:
                return transactions;
            case XmlPullParser.START_TAG:
                final String tagName = parser.getName();
                inComment = "comment".equalsIgnoreCase(tagName);
                if ("transaction".equalsIgnoreCase(tagName)) {
                    currentTransaction = new AccurevTransaction();
                    transactions.add(currentTransaction);
                    currentTransaction.setRevision(parser.getAttributeValue("", "id"));
                    currentTransaction.setUser(parser.getAttributeValue("", "user"));
                    currentTransaction
                            .setDate(ParseChangeLog.convertAccurevTimestamp(parser.getAttributeValue("", "time")));
                    currentTransaction.setAction(parser.getAttributeValue("", "type"));
                } else if ("version".equalsIgnoreCase(tagName) && currentTransaction != null) {
                    String path = parser.getAttributeValue("", "path");
                    if (path != null) {
                        path = path.replace("\\", "/");
                        if (path.startsWith("/./")) {
                            path = path.substring(3);
                        }
                    }
                    currentTransaction.addAffectedPath(path);
                }
                break;
            case XmlPullParser.END_TAG:
                inComment = false;
                break;
            case XmlPullParser.TEXT:
                if (inComment && currentTransaction != null) {
                    currentTransaction.setMsg(parser.getText());
                }
                break;
            }
        }
    }

    /**
     * Converts an Accurev timestamp into a {@link Date}
     *
     * @param transactionTime
     *            The accurev timestamp.
     *
     * @return A {@link Date} set to the time for the accurev timestamp.
     */
    static Date convertAccurevTimestamp(String transactionTime) {
        if (transactionTime == null) {
            return null;
        }
        try {
            final long time = Long.parseLong(transactionTime);
            final long date = time * MILLIS_PER_SECOND;
            return new Date(date);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}

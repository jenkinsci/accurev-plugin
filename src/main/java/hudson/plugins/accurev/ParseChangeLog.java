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
        List<AccurevTransaction> transactions = parse(changelogFile);
        return new AccurevChangeLogSet(build, transactions);
    }

	private List<AccurevTransaction> parse(File changelogFile) throws IOException{
        List<AccurevTransaction> transactions = null;
        try {
            XmlPullParser parser = XmlParserFactory.newParser();
            FileReader fis = null;
            BufferedReader bis = null;
            try {
                fis = new FileReader(changelogFile);
                bis = new BufferedReader(fis);
                parser.setInput(bis);
                transactions = parseTransactions(parser, changelogFile);
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
		return transactions;
    }
    private List<AccurevTransaction> parseTransactions(XmlPullParser parser, File changeLogFile) throws IOException, XmlPullParserException {
        List<AccurevTransaction> transactions = new ArrayList<AccurevTransaction>();
        AccurevTransaction currentTransaction = null;
        boolean inComment = false;
        boolean inIssueNum = false;
        boolean inVersion = false;
        String path = "";
        String realVersion = "";
        String issueNum = "";
        String affectedPathInfo = "";
	boolean inConsolidatedChangeLog = false;
        while (true) {
            switch (parser.next()) {
            case XmlPullParser.START_DOCUMENT:
                break;
            case XmlPullParser.END_DOCUMENT:
                return transactions;
            case XmlPullParser.START_TAG:
                final String tagName = parser.getName();
                inComment = "comment".equalsIgnoreCase(tagName);
		inConsolidatedChangeLog = "ChangeLog".equalsIgnoreCase(tagName);
                if ("transaction".equalsIgnoreCase(tagName)) {
                    currentTransaction = new AccurevTransaction();
                    transactions.add(currentTransaction);
                    currentTransaction.setRevision(parser.getAttributeValue("", "id"));
                    currentTransaction.setUser(parser.getAttributeValue("", "user"));
                    currentTransaction
                            .setDate(ParseChangeLog.convertAccurevTimestamp(parser.getAttributeValue("", "time")));
                    currentTransaction.setAction(parser.getAttributeValue("", "type"));
                } else if ("version".equalsIgnoreCase(tagName) && currentTransaction != null) {
                    path = parser.getAttributeValue("", "path");
                    if (path != null) {
                        path = path.replace("\\", "/");
                        if (path.startsWith("/./")) {
                            path = path.substring(3);
                        }
                    }
                    inVersion = true;
                    realVersion = parser.getAttributeValue("", "real");
                   
                }else if ("issueNum".equalsIgnoreCase(tagName) && currentTransaction != null) {
                	inIssueNum = true;
                }
                break;
            case XmlPullParser.END_TAG:
            	final String endTagName = parser.getName();
            	if ("issueNum".equalsIgnoreCase(endTagName) && inVersion && inIssueNum && currentTransaction != null){
            		affectedPathInfo = path + "<br/>" + "Version - " + realVersion + "&nbsp;&nbsp;&nbsp;&nbsp;" + "Issue Number - " + issueNum;
            		currentTransaction.addAffectedPath(affectedPathInfo);
            		inIssueNum = false;
                    inVersion=false;
            	}else if ("version".equalsIgnoreCase(endTagName) && inVersion && currentTransaction != null){
            		affectedPathInfo = path + "<br/>" + "Version - " + realVersion;
            		currentTransaction.addAffectedPath(affectedPathInfo);
            		inVersion=false;
            	}else if ("comment".equalsIgnoreCase(endTagName)){
            	    inComment = false;
            	}
                break;
            case XmlPullParser.TEXT:
                if (inComment && currentTransaction != null) {
                    currentTransaction.setMsg(parser.getText());
                }else if (inVersion && inIssueNum && currentTransaction != null) {
                	issueNum = parser.getText();                	
                }
		if (inConsolidatedChangeLog){
			File subChangeLog = new File(changeLogFile.getParent(), parser.getText());
			transactions.addAll(parse(subChangeLog));
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

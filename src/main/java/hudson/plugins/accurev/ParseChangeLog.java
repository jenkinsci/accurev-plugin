package hudson.plugins.accurev;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.model.Run;
import hudson.plugins.accurev.parsers.output.ParseOutputToFile;
import hudson.plugins.accurev.parsers.xml.ParseUpdate;
import hudson.scm.ChangeLogParser;
import hudson.scm.ChangeLogSet;
import hudson.scm.RepositoryBrowser;
import org.xml.sax.SAXException;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.logging.Logger;

/**
 * Parses a change log that was recorded by {@link ParseOutputToFile}.
 */
public class ParseChangeLog extends ChangeLogParser {

    private static final Logger logger = Logger.getLogger(AccurevSCM.class.getName());
    private static final long MILLIS_PER_SECOND = 1000L;

    /**
     * Converts an Accurev timestamp into a {@link Date}
     *
     * @param transactionTime The accurev timestamp.
     * @return A {@link Date} set to the time for the accurev timestamp.
     */
    public static Date convertAccurevTimestamp(String transactionTime) {
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

    /**
     * {@inheritDoc}
     *
     * @param build         build
     * @param browser       Repository browser
     * @param changelogFile change log file
     * @return ChangeLogSet with AccurevTransactions
     * @throws IOException  failing IO
     * @throws SAXException failing XML SAX exception
     */
    public ChangeLogSet<AccurevTransaction> parse(Run build, RepositoryBrowser<?> browser, File changelogFile) throws IOException, SAXException {
        UpdateLog updateLog = new UpdateLog();
        List<AccurevTransaction> transactions = parse(changelogFile, updateLog);
        transactions = filterTransactions(transactions, updateLog);
        return new AccurevChangeLogSet(build, transactions);
    }

    private List<AccurevTransaction> filterTransactions(List<AccurevTransaction> transactions, UpdateLog updateLog) {
        List<AccurevTransaction> filteredTransactions;
        if (updateLog.hasUpdate()) {
            filteredTransactions = new ArrayList<>();
            if (updateLog.hasChanges()) {
                List<String> changesFiles = updateLog.changedFiles;
                List<String> filteredFiles = new ArrayList<>(changesFiles);
                for (AccurevTransaction transaction : transactions) {
                    List<String> rawPaths = transaction.getAffectedRawPaths();
                    boolean includeTransaction = true;
                    for (String rawPath : rawPaths) {
                        if (!changesFiles.contains(rawPath)) {
                            includeTransaction = false;
                            break;
                        }
                    }
                    if (includeTransaction) {
                        filteredTransactions.add(transaction);
                        rawPaths.forEach(filteredFiles::remove);
                    }
                }
                if (!filteredFiles.isEmpty()) {
                    AccurevTransaction extraFiles = new AccurevTransaction();
                    extraFiles.setDate(new Date());
                    extraFiles.setAction("promote");
                    extraFiles.setId("upstream");
                    extraFiles.setMsg("Upstream changes");
                    extraFiles.setUser("upstream");
                    filteredFiles.forEach(extraFiles::addAffectedPath);
                    filteredTransactions.add(extraFiles);
                }
            }
        } else {
            // No Update log dont filter
            filteredTransactions = transactions;
        }
        filteredTransactions.removeIf(t -> t.getAction().equalsIgnoreCase("dispatch"));
        return filteredTransactions;
    }

    private List<AccurevTransaction> parse(File changelogFile, UpdateLog updateLog) throws IOException {
        List<AccurevTransaction> transactions = null;
        try {
            XmlPullParser parser = XmlParserFactory.newParser();
            try (BufferedReader br = Files.newBufferedReader(changelogFile.toPath())) {
                parser.setInput(br);
                transactions = parseTransactions(parser, changelogFile, updateLog);
            } finally {
                parser.setInput(null);
            }

        } catch (XmlPullParserException e) {
            throw new IOException(e);
        }
        return transactions;
    }

    // TODO: Reduce complexity
    @SuppressFBWarnings("SF_SWITCH_NO_DEFAULT")
    private List<AccurevTransaction> parseTransactions(XmlPullParser parser, File changeLogFile, UpdateLog updateLog) throws IOException, XmlPullParserException {
        List<AccurevTransaction> transactions = new ArrayList<>();
        AccurevTransaction currentTransaction = null;
        boolean inComment = false;
        boolean inIssueNum = false;
        boolean inVersion = false;
        String path = "";
        String realVersion = "";
        String issueNum;
        String affectedPathInfo;
        boolean inConsolidatedChangeLog = false;
        boolean inUpdateLog = false;
        boolean inDepot = false;
        boolean inWebuiURL = false;
        String depotName = "";
        String webuiURL = "";
        while (true) {
            switch (parser.next()) {
                case XmlPullParser.START_DOCUMENT:
                    break;
                case XmlPullParser.END_DOCUMENT:
                    return transactions;
                case XmlPullParser.START_TAG:
                    final String tagName = parser.getName();
                    if ("transaction".equalsIgnoreCase(tagName)) {
                        currentTransaction = new AccurevTransaction();
                        transactions.add(currentTransaction);
                        currentTransaction.setId(parser.getAttributeValue("", "id"));
                        currentTransaction.setUser(parser.getAttributeValue("", "user"));
                        currentTransaction.setDate(ParseChangeLog.convertAccurevTimestamp(parser.getAttributeValue("", "time")));
                        currentTransaction.setAction(parser.getAttributeValue("", "type"));
                        if (webuiURL != null && !webuiURL.isEmpty()) {
                            currentTransaction.setWebuiURLforTrans(webuiURL + "/WebGui.jsp?tran_number=" + parser.getAttributeValue("", "id") + "&depot=" + depotName + "&view=trans_hist");
                        }
                    } else if ("version".equalsIgnoreCase(tagName) && currentTransaction != null) {
                        path = parser.getAttributeValue("", "path");
                        if (path != null) {
                            path = path.replace("\\", "/");
                            if (path.startsWith("/./")) {
                                path = path.substring(3);
                            }
                            // currentTransaction.addAffectedPath(path);

                        }
                        inVersion = true;
                        realVersion = parser.getAttributeValue("", "real");
                        // currentTransaction.addFileRevision("Version - "+realVersion);

                    } else if ("issueNum".equalsIgnoreCase(tagName) && currentTransaction != null) {
                        inIssueNum = true;
                    } else if ("comment".equalsIgnoreCase(tagName) && currentTransaction != null) {
                        inComment = true;
                    } else if ("ChangeLog".equalsIgnoreCase(tagName)) {
                        inConsolidatedChangeLog = true;
                    } else if ("UpdateLog".equalsIgnoreCase(tagName)) {
                        inUpdateLog = true;
                    } else if ("depot".equalsIgnoreCase(tagName)) {
                        inDepot = true;
                    } else if ("webuiURL".equalsIgnoreCase(tagName)) {
                        inWebuiURL = true;
                    }
                    break;
                case XmlPullParser.END_TAG:
                    final String endTagName = parser.getName();
                    if ("issueNum".equalsIgnoreCase(endTagName) && inVersion && inIssueNum && currentTransaction != null) {
                        affectedPathInfo = path + " --- " + "Version - " + realVersion;
                        currentTransaction.addAffectedPath(affectedPathInfo);
                        currentTransaction.addAffectedRawPath(path);
                        inIssueNum = false;
                        inVersion = false;
                    } else if ("version".equalsIgnoreCase(endTagName) && inVersion && currentTransaction != null) {
                        affectedPathInfo = path + " --- " + "Version - " + realVersion;
                        currentTransaction.addAffectedPath(affectedPathInfo);
                        currentTransaction.addAffectedRawPath(path);
                        inVersion = false;
                    } else if ("comment".equalsIgnoreCase(endTagName)) {
                        inComment = false;
                    } else if ("ChangeLog".equalsIgnoreCase(endTagName)) {
                        inConsolidatedChangeLog = false;
                    } else if ("UpdateLog".equalsIgnoreCase(endTagName)) {
                        inUpdateLog = false;
                    } else if ("depot".equalsIgnoreCase(endTagName)) {
                        inDepot = false;
                    } else if ("webuiURL".equalsIgnoreCase(endTagName)) {
                        inWebuiURL = false;
                    }
                    break;
                case XmlPullParser.TEXT:
                    if (inComment && currentTransaction != null) {
                        currentTransaction.setMsg(parser.getText());
                    } else if (inVersion && inIssueNum && currentTransaction != null) {
                        issueNum = parser.getText();
                        currentTransaction.setIssueNum(issueNum);
                        if (webuiURL != null && !webuiURL.isEmpty()) {
                            currentTransaction.setWebuiURLforIssue(webuiURL + "/WebGui.jsp?depot=" + depotName + "&issueNum=" + issueNum + "&view=issue");
                        }
                    } else if (inDepot) {
                        depotName = parser.getText();
                    } else if (inWebuiURL) {
                        webuiURL = parser.getText();
                    }
                    if (inConsolidatedChangeLog) {
                        File subChangeLog = new File(changeLogFile.getParent(), parser.getText());
                        transactions.addAll(parse(subChangeLog, updateLog));
                    }
                    if (inUpdateLog) {
                        File updateLogFile = new File(changeLogFile.getParent(), parser.getText());
                        parseUpdate(updateLogFile, updateLog);
                    }
                    break;
            }
        }
    }

    private void parseUpdate(File updateLogFile, UpdateLog updateLog) throws XmlPullParserException, IOException {
        ParseUpdate parseUpdate = new ParseUpdate();
        List<String> updatedFiles = new ArrayList<>();
        updateLog.changedFiles = updatedFiles;
        try {
            try {
                XmlPullParser parser = XmlParserFactory.newParser();
                try (BufferedReader br = Files.newBufferedReader(updateLogFile.toPath())) {
                    parser.setInput(br);
                    parseUpdate.parse(parser, updatedFiles);
                } finally {
                    parser.setInput(null);
                }
            } catch (XmlPullParserException e) {
                throw new IOException(e);
            }
        } catch (AccurevLauncher.UnhandledAccurevCommandOutput ex) {
            throw new IOException(ex);
        }
    }

    private static class UpdateLog {

        private List<String> changedFiles;

        public boolean hasUpdate() {
            return changedFiles != null;
        }

        public boolean hasChanges() {
            return hasUpdate() && !changedFiles.isEmpty();
        }
    }
}

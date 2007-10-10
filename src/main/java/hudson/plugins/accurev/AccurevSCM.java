package hudson.plugins.accurev;

import hudson.EnvVars;
import hudson.FilePath;
import hudson.Launcher;
import hudson.Proc;
import hudson.Util;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.model.ModelObject;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.remoting.VirtualChannel;
import hudson.scm.ChangeLogParser;
import hudson.scm.ChangeLogSet;
import hudson.scm.SCM;
import hudson.scm.SCMDescriptor;
import hudson.util.ArgumentListBuilder;
import org.apache.commons.lang.time.DateUtils;
import org.codehaus.plexus.util.StringOutputStream;
import org.kohsuke.stapler.StaplerRequest;
import org.xml.sax.SAXException;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlPullParserFactory;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.StringReader;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Logger;

/**
 * Created by IntelliJ IDEA.
 *
 * @author connollys
 * @since 09-Oct-2007 16:17:34
 */
public class AccurevSCM extends SCM {
    private static final Logger logger = Logger.getLogger(AccurevSCM.class.getName());
    public static final SimpleDateFormat ACCUREV_DATETIME_FORMATTER = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");
    private final String server;
    private final int port;
    private final String depot;
    private final String stream;

    /**
     * @stapler-constructor
     */
    public AccurevSCM(String server, int port, String depot, String stream) {
        super();
        this.port = port == 0 ? 5050 : port;
        this.server = server;
        this.depot = depot;
        this.stream = stream;
    }

    /**
     * {@inheritDoc}
     */
    public boolean pollChanges(AbstractProject project, Launcher launcher, FilePath workspace, TaskListener listener) throws IOException, InterruptedException {
        final String accurevPath = workspace.act(new FindAccurevHome());
        ArgumentListBuilder cmd = new ArgumentListBuilder();
        cmd.add(accurevPath);
        cmd.add("hist");
        if (null != server && !"".equals(server)) {
            cmd.add("-H");
            if (port != 0) {
                cmd.add(server + ":" + port);
            } else {
                cmd.add(server);
            }
        }
        cmd.add("-fx");
        cmd.add("-p");
        cmd.addQuoted(depot);
        cmd.add("-s");
        cmd.addQuoted(stream);
        cmd.add("-t");
        cmd.add("now.1");
        StringOutputStream sos = new StringOutputStream();
        DESCRIPTOR.ACCUREV_LOCK.lock();
        try {
            Proc process = launcher
                    .launch(cmd.toCommandArray(), Collections.<String, String>emptyMap(), sos, workspace);
            int rv = process.join();
        } finally {
            DESCRIPTOR.ACCUREV_LOCK.unlock();
        }

        try {
            XmlPullParser parser = newPullParser();
            parser.setInput(new StringReader(sos.toString()));
            while (true) {
                if (parser.next() != XmlPullParser.START_TAG) {
                    continue;
                }
                if (!parser.getName().equalsIgnoreCase("transaction")) {
                    continue;
                }
                break;
            }
            String transactionId = parser.getAttributeValue("", "id");
            String transactionType = parser.getAttributeValue("", "type");
            String transactionTime = parser.getAttributeValue("", "time");
            String transactionUser = parser.getAttributeValue("", "user");
            Date transactionDate = convertAccurevTimestamp(transactionTime);

            final Run lastBuild = project.getLastBuild();
            if (lastBuild == null) {
                listener.getLogger().println("Project has never been built");
                return true;
            }
            final Date buildDate = lastBuild.getTimestamp().getTime();
            listener.getLogger().println("Last build on " + buildDate);
            listener.getLogger().println("Last change on " + transactionDate);
            listener.getLogger().println("#" + transactionId + " " + transactionUser + " " + transactionType);

            String transactionComment = null;
            boolean inComment = false;
            while (transactionComment == null) {
                switch (parser.next()) {
                    case XmlPullParser.START_TAG:
                        inComment = parser.getName().equalsIgnoreCase("comment");
                        break;
                    case XmlPullParser.END_TAG:
                        inComment = false;
                        break;
                    case XmlPullParser.TEXT:
                        if (inComment) {
                            transactionComment = parser.getText();
                        }
                        break;
                    default:
                        continue;
                }
            }
            if (transactionComment != null) {
                listener.getLogger().println(transactionComment);
            }

            return buildDate.compareTo(transactionDate) < 0;
        } catch (XmlPullParserException e) {
            e.printStackTrace(listener.getLogger());
            logger.warning(e.getMessage());
            return false;
        }
    }

    private static Date convertAccurevTimestamp(String transactionTime) {
        final long time = Long.parseLong(transactionTime);
        final long date = time * DateUtils.MILLIS_PER_SECOND;
        return new Date(date);
    }

    private static XmlPullParser newPullParser() throws XmlPullParserException {
        XmlPullParserFactory factory = XmlPullParserFactory.newInstance();
        factory.setNamespaceAware(false);
        factory.setValidating(false);
        XmlPullParser parser = factory.newPullParser();
        return parser;
    }

    /**
     * {@inheritDoc}
     */
    public boolean checkout(AbstractBuild build, Launcher launcher, FilePath workspace, BuildListener listener, File changelogFile) throws IOException, InterruptedException {
        final String accurevPath = workspace.act(new FindAccurevHome());
        workspace.act(new PurgeWorkspaceContents(listener));

        listener.getLogger().println("Populating workspace...");
        if (depot == null) {
            listener.fatalError("Must specify a depot");
            return false;
        }
        if (stream == null) {
            listener.fatalError("Must specify a stream");
            return false;
        }
        ArgumentListBuilder cmd = new ArgumentListBuilder();
        cmd.add(accurevPath);
        cmd.add("pop");
        if (null != server && !"".equals(server)) {
            cmd.add("-H");
            if (port != 0) {
                cmd.add(server + ":" + port);
            } else {
                cmd.add(server);
            }
        }
        cmd.add("-v");
        cmd.addQuoted(stream);
        cmd.add("-L");
        cmd.add(workspace.getRemote());
        cmd.add("-R");
        cmd.add(".");
        int rv;
        DESCRIPTOR.ACCUREV_LOCK.lock();
        try {
            rv = launcher
                    .launch(cmd.toCommandArray(), Collections.<String, String>emptyMap(), listener.getLogger(), workspace)
                    .join();
        } finally {
            DESCRIPTOR.ACCUREV_LOCK.unlock();
        }
        if (rv != 0) {
            listener.fatalError("Populate failed with exit code " + rv);
            return false;
        }
        listener.getLogger().println("Populate completed successfully.");

        listener.getLogger().println("Calculating changelog...");

        Calendar startTime = null;
        if (null == build.getPreviousBuild()) {
            listener.getLogger().println("Cannot find a previous build to compare against. Computing all changes.");
        } else {
            startTime = build.getPreviousBuild().getTimestamp();
        }

        cmd = new ArgumentListBuilder();
        cmd.add(accurevPath);
        cmd.add("hist");
        if (null != server && !"".equals(server)) {
            cmd.add("-H");
            if (port != 0) {
                cmd.add(server + ":" + port);
            } else {
                cmd.add(server);
            }
        }
        cmd.add("-fx");
        cmd.add("-a");
        cmd.add("-s");
        cmd.addQuoted(stream);
        cmd.add("-t");
        String dateRange = ACCUREV_DATETIME_FORMATTER.format(build.getTimestamp().getTime());
        if (startTime != null) {
            dateRange += "-" + ACCUREV_DATETIME_FORMATTER.format(startTime.getTime());
        } else {
            dateRange += ".100";
        }
        cmd.addQuoted(dateRange);
        FileOutputStream os = new FileOutputStream(changelogFile);
        try {
            BufferedOutputStream bos = new BufferedOutputStream(os);
            try {
                DESCRIPTOR.ACCUREV_LOCK.lock();
                try {
                    rv = launcher.launch(cmd.toCommandArray(), EnvVars.masterEnvVars, bos, workspace).join();
                } finally {
                    DESCRIPTOR.ACCUREV_LOCK.unlock();
                }
                if (rv != 0) {
                    listener.fatalError("Changelog failed with exit code " + rv);
                    return false;
                }
            } finally {
                bos.close();
            }
        } finally {
            os.close();
        }

        listener.getLogger().println("Changelog calculated successfully.");

        return true;
    }

    /**
     * {@inheritDoc}
     */
    public ChangeLogParser createChangeLogParser() {
        return new AccurevChangeLogParser();
    }

    /**
     * Getter for property 'server'.
     *
     * @return Value for property 'server'.
     */
    public String getServer() {
        return server;
    }

    /**
     * Getter for property 'port'.
     *
     * @return Value for property 'port'.
     */
    public int getPort() {
        return port;
    }

    /**
     * Getter for property 'depot'.
     *
     * @return Value for property 'depot'.
     */
    public String getDepot() {
        return depot;
    }

    /**
     * Getter for property 'stream'.
     *
     * @return Value for property 'stream'.
     */
    public String getStream() {
        return stream;
    }

    /**
     * {@inheritDoc}
     */
    public SCMDescriptor<?> getDescriptor() {
        return DESCRIPTOR;
    }

    public static final AccurevSCMDescriptor DESCRIPTOR = new AccurevSCMDescriptor();

    public static final class AccurevSCMDescriptor extends SCMDescriptor<AccurevSCM> implements ModelObject {
        /**
         * The accurev server has been known to crash if more than one copy of the accurev has been run concurrently
         * on the local machine.
         */
        transient static final Lock ACCUREV_LOCK = new ReentrantLock();

        /**
         * Constructs a new AccurevSCMDescriptor.
         */
        protected AccurevSCMDescriptor() {
            super(AccurevSCM.class, null);
            load();
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public String getDisplayName() {
            return "Accurev";
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public boolean configure(StaplerRequest req) throws FormException {
            req.bindParameters(this, "accurev.");
            save();
            return true;
        }


        /**
         * {@inheritDoc}
         */
        @Override
        public SCM newInstance(StaplerRequest req) throws FormException {
            return req.bindParameters(AccurevSCM.class, "accurev.");
        }
    }

    private static final class PurgeWorkspaceContents implements FilePath.FileCallable<Boolean> {
        private final TaskListener listener;

        public PurgeWorkspaceContents(TaskListener listener) {
            this.listener = listener;
        }

        public Boolean invoke(File ws, VirtualChannel channel) throws IOException {
            listener.getLogger().println("Purging workspace...");
            Util.deleteContentsRecursive(ws);
            listener.getLogger().println("Workspace purged.");
            return Boolean.TRUE;
        }
    }

    private static final class FindAccurevHome implements FilePath.FileCallable<String> {

        private String[] nonWindowsPaths = {
                "/usr/local/bin/accurev",
                "/usr/bin/accurev",
                "/bin/accurev",
                "/local/bin/accurev",
        };
        private String[] windowsPaths = {
                "C:\\Program Files\\AccuRev\\bin\\accurev.exe",
                "C:\\Program Files (x86)\\AccuRev\\bin\\accurev.exe"
        };

        private static String getExistingPath(String[] paths) {
            for (int i = 0; i < paths.length; i++) {
                if (new File(paths[i]).exists()) {
                    return paths[i];
                }
            }
            return paths[0];
        }

        public String invoke(File f, VirtualChannel channel) throws IOException {
            if (System.getProperty("os.name").toLowerCase().startsWith("windows")) {
                // we are running on windows
                return getExistingPath(windowsPaths);
            } else {
                // we are running on *nix
                return getExistingPath(nonWindowsPaths);
            }
        }
    }

    private static final class AccurevChangeLogParser extends ChangeLogParser {
        public ChangeLogSet<AccurevTransaction> parse(AbstractBuild build, File changelogFile) throws IOException, SAXException {
            List<AccurevTransaction> transactions = null;
            try {
                XmlPullParser parser = newPullParser();
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
                }
            } catch (XmlPullParserException e) {
                throw new IOException(e);
            }

            logger.info("transations size = " + transactions.size());
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
                            currentTransaction.setDate(convertAccurevTimestamp(parser.getAttributeValue("", "time")));
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
    }

}

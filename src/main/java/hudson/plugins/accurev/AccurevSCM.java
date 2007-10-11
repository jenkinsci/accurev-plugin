package hudson.plugins.accurev;

import hudson.FilePath;
import hudson.Launcher;
import hudson.Util;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.model.ModelObject;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.plugins.jetty.security.Password;
import hudson.remoting.VirtualChannel;
import hudson.scm.ChangeLogParser;
import hudson.scm.ChangeLogSet;
import hudson.scm.SCM;
import hudson.scm.SCMDescriptor;
import hudson.util.ArgumentListBuilder;
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
import java.io.InputStream;
import java.io.OutputStream;
import java.io.StringReader;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
    private static final long MILLIS_PER_SECOND = 1000L;
    private final String serverName;
    private final String depot;
    private final String stream;
    private final boolean useWorkspace;
    private final String workspace;

    /**
     * @stapler-constructor
     */
    public AccurevSCM(String serverName, String depot, String stream, boolean useWorkspace, String workspace) {
        super();
        this.serverName = serverName;
        this.depot = depot;
        this.stream = stream;
        this.useWorkspace = useWorkspace;
        this.workspace = workspace;
    }

    /**
     * {@inheritDoc}
     */
    public boolean pollChanges(AbstractProject project, Launcher launcher, FilePath workspace, TaskListener listener) throws IOException, InterruptedException {
        final String accurevPath = workspace.act(new FindAccurevHome());

        AccurevServer server = DESCRIPTOR.getServer(serverName);

        Map<String, String> accurevEnv = new HashMap<String, String>();

        if (!accurevLogin(server, accurevEnv, workspace, listener, accurevPath, launcher)) {
            return false;
        }

        ArgumentListBuilder cmd = new ArgumentListBuilder();
        cmd.add(accurevPath);
        cmd.add("hist");
        addServer(cmd, server);
        cmd.add("-fx");
        cmd.add("-p");
        cmd.addQuoted(depot);
        cmd.add("-s");
        cmd.addQuoted(stream);
        cmd.add("-t");
        cmd.add("now.1");
        StringOutputStream sos = new StringOutputStream();
        int rv;
        if (0 != (rv = launchAccurev(launcher, cmd, accurevEnv, null, sos, workspace))) {
            listener.fatalError("History command failed with exit code " + rv);
            return false;
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

    /**
     * {@inheritDoc}
     */
    public boolean checkout(AbstractBuild build, Launcher launcher, FilePath workspace, BuildListener listener, File changelogFile) throws IOException, InterruptedException {
        final String accurevPath = workspace.act(new FindAccurevHome());
        workspace.act(new PurgeWorkspaceContents(listener));

        AccurevServer server = DESCRIPTOR.getServer(serverName);

        Map<String, String> accurevEnv = new HashMap<String, String>();

        if (!accurevLogin(server, accurevEnv, workspace, listener, accurevPath, launcher)) {
            return false;
        }

        listener.getLogger().println("Populating workspace...");
        if (depot == null || "".equals(depot)) {
            listener.fatalError("Must specify a depot");
            return false;
        }
        if (stream == null || "".equals(stream)) {
            listener.fatalError("Must specify a stream");
            return false;
        }
        if (useWorkspace && (workspace == null || "".equals(workspace))) {
            listener.fatalError("Must specify a workspace");
            return false;
        }
        if (useWorkspace) {
            listener.fatalError("Sorry, workspace based checkout code has not been written yet");
            return false;
            // TODO accurev show -H hostname:port -fx wspaces
            // TODO Parse the xml file to find the workspace Element (xxx -> text, ### -> number)
            //   <Element
            //     Name = "xxxxx"
            //     Storage = "xxxx"
            //     Host = "xxx.xxx.xxx"
            //     Stream = "####"
            //     depot = "xxxx"
            //     Target_trans = "####"
            //     Trans = "####"
            //     Type = "1"
            //     EOL = "0"
            //     user_id = "####"
            //     user_name = "xxxx" / >
            // TODO verify that the depot is correct
            // TODO verify that the Storage corresponds with the workspace folder
            // TODO verify that the Host corresponds to the slave that this is running on
            // TODO accurev show -H hostname:port -fx streams
            // TODO parse the xml file to find the stream
            /*
              <stream
                name="xxxx"
                basis="xxxxx"
                basisStreamNumber="####"
                depotName="xxxxx"
                streamNumber="####"
                isDynamic="false"
                type="snapshot"
                time="####"
                startTime="####"/>
            */
            // TODO verify that the stream number matches the stream number of the workspace
            // TODO If the workspace is not based on the stream, change it to be based on the stream
            // TODO If the workspace is not on the correct hosts and at the correct storage location, move it

            // Now we have the workspace ready to update

            // Update has been known to fail with refactoring of code in the workspace.... Must use a purged workspace

            // Thankfully, we delete everything up at the top anyway

            // TODO accurev update -H host:port

            // Since we needed an empty workspace to ensure that update works, now populate everything
            // TODO accurev pop -H host:port -R .

            // Done
        } else {
            ArgumentListBuilder cmd = new ArgumentListBuilder();
            cmd.add(accurevPath);
            cmd.add("pop");
            addServer(cmd, server);
            cmd.add("-v");
            cmd.addQuoted(stream);
            cmd.add("-L");
            cmd.add(workspace.getRemote());
            cmd.add("-R");
            cmd.add(".");
            int rv;
            rv = launchAccurev(launcher, cmd, accurevEnv, null, listener.getLogger(), workspace);
            if (rv != 0) {
                listener.fatalError("Populate failed with exit code " + rv);
                return false;
            }
            listener.getLogger().println("Populate completed successfully.");
        }

        listener.getLogger().println("Calculating changelog...");

        Calendar startTime = null;
        if (null == build.getPreviousBuild()) {
            listener.getLogger().println("Cannot find a previous build to compare against. Computing all changes.");
        } else {
            startTime = build.getPreviousBuild().getTimestamp();
        }

        ArgumentListBuilder cmd = new ArgumentListBuilder();
        cmd.add(accurevPath);
        cmd.add("hist");
        addServer(cmd, server);
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
                int rv = launchAccurev(launcher, cmd, accurevEnv, null, bos, workspace);
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

    private boolean accurevLogin(AccurevServer server, Map<String, String> accurevEnv, FilePath workspace, TaskListener listener, String accurevPath, Launcher launcher) throws IOException, InterruptedException {
        ArgumentListBuilder cmd;
        if (server != null) {
            accurevEnv.put("ACCUREV_HOME", workspace.getParent().getRemote());
            listener.getLogger().println("Authenticating with Accurev server...");
            cmd = new ArgumentListBuilder();
            cmd.add(accurevPath);
            cmd.add("login");
            addServer(cmd, server);
            cmd.add(server.getUsername());
            if (server.getPassword() == null || "".equals(server.getPassword())) {
                cmd.addQuoted("");
            } else {
                cmd.add(server.getPassword());
            }
            String resp = null;
            DESCRIPTOR.ACCUREV_LOCK.lock();
            try {
                resp = workspace.act(new AccurevLoginCallable(cmd.toCommandArray(), Util.mapToEnv(accurevEnv)));
            } finally {
                DESCRIPTOR.ACCUREV_LOCK.unlock();
            }
            if (null == resp || "".equals(resp)) {
                listener.getLogger().println("Authentication completed successfully.");
                return true;
            } else {
                listener.fatalError("Authentication failed: " + resp);
                return false;

            }
        }
        return true;
    }

    private int launchAccurev(Launcher launcher,
                              ArgumentListBuilder cmd,
                              Map<String, String> env,
                              InputStream in,
                              OutputStream os,
                              FilePath workspace) throws IOException, InterruptedException {
        int rv;
        DESCRIPTOR.ACCUREV_LOCK.lock();
        try {
            rv = launcher.launch(cmd.toCommandArray(), Util.mapToEnv(env), in, os, workspace).join();
        } finally {
            DESCRIPTOR.ACCUREV_LOCK.unlock();
        }
        return rv;
    }

    private void addServer(ArgumentListBuilder cmd, AccurevServer server) {
        if (null != server && null != server.getHost() && !"".equals(server.getHost())) {
            cmd.add("-H");
            if (server.getPort() != 0) {
                cmd.add(server.getHost() + ":" + server.getPort());
            } else {
                cmd.add(server.getHost());
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    public ChangeLogParser createChangeLogParser() {
        return new AccurevChangeLogParser();
    }

    /**
     * Getter for property 'useWorkspace'.
     *
     * @return Value for property 'useWorkspace'.
     */
    public boolean isUseWorkspace() {
        return useWorkspace;
    }

    /**
     * Getter for property 'workspace'.
     *
     * @return Value for property 'workspace'.
     */
    public String getWorkspace() {
        return workspace;
    }

    /**
     * Getter for property 'serverName'.
     *
     * @return Value for property 'serverName'.
     */
    public String getServerName() {
        return serverName;
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

    private static Date convertAccurevTimestamp(String transactionTime) {
        final long time = Long.parseLong(transactionTime);
        final long date = time * MILLIS_PER_SECOND;
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
        private List<AccurevServer> servers;

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
            servers = req.bindParametersToList(AccurevServer.class, "accurev.server.");
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

        /**
         * Getter for property 'servers'.
         *
         * @return Value for property 'servers'.
         */
        public List<AccurevServer> getServers() {
            if (servers == null) {
                servers = new ArrayList<AccurevServer>();
            }
            return servers;
        }

        /**
         * Setter for property 'servers'.
         *
         * @param servers Value to set for property 'servers'.
         */
        public void setServers(List<AccurevServer> servers) {
            this.servers = servers;
        }

        public AccurevServer getServer(String name) {
            if (name == null) {
                return null;
            }
            for (AccurevServer server : servers) {
                if (name.equals(server.getName())) {
                    return server;
                }
            }
            return null;
        }

        /**
         * Getter for property 'serverNames'.
         *
         * @return Value for property 'serverNames'.
         */
        public String[] getServerNames() {
            String[] result = new String[servers.size()];
            for (int i = 0; i < result.length; i++) {
                result[i] = servers.get(i).getName();
            }
            return result;
        }

    }

    public static final class AccurevServer {
        private String name;
        private String host;
        private int port;
        private String username;
        private String password;

        /**
         * Constructs a new AccurevServer.
         */
        public AccurevServer() {
        }

        public AccurevServer(String name, String host, int port, String username, String password) {
            this.name = name;
            this.host = host;
            this.port = port;
            this.username = username;
            this.password = password;
        }

        /**
         * Getter for property 'name'.
         *
         * @return Value for property 'name'.
         */
        public String getName() {
            return name;
        }

        /**
         * Setter for property 'name'.
         *
         * @param name Value to set for property 'name'.
         */
        public void setName(String name) {
            this.name = name;
        }

        /**
         * Getter for property 'host'.
         *
         * @return Value for property 'host'.
         */
        public String getHost() {
            return host;
        }

        /**
         * Setter for property 'host'.
         *
         * @param host Value to set for property 'host'.
         */
        public void setHost(String host) {
            this.host = host;
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
         * Setter for property 'port'.
         *
         * @param port Value to set for property 'port'.
         */
        public void setPort(int port) {
            this.port = port;
        }

        /**
         * Getter for property 'username'.
         *
         * @return Value for property 'username'.
         */
        public String getUsername() {
            return username;
        }

        /**
         * Setter for property 'username'.
         *
         * @param username Value to set for property 'username'.
         */
        public void setUsername(String username) {
            this.username = username;
        }

        /**
         * Getter for property 'password'.
         *
         * @return Value for property 'password'.
         */
        public String getPassword() {
            return Password.deobfuscate(password);
        }

        /**
         * Setter for property 'password'.
         *
         * @param password Value to set for property 'password'.
         */
        public void setPassword(String password) {
            this.password = Password.obfuscate(password);
        }

    }

    private static final class PurgeWorkspaceContents implements FilePath.FileCallable<Boolean> {
        private final TaskListener listener;

        public PurgeWorkspaceContents(TaskListener listener) {
            this.listener = listener;
        }

        /**
         * {@inheritDoc}
         */
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

        /**
         * {@inheritDoc}
         */
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

    /**
     * Necessary to hide the password until there is a better launcher.
     */
    private static final class AccurevLoginCallable implements FilePath.FileCallable<String> {
        private final String[] cmd;
        private final String[] env;

        public AccurevLoginCallable(String[] cmd, String[] env) {
            this.cmd = cmd;
            this.env = env;
        }

        /**
         * {@inheritDoc}
         */
        public String invoke(File f, VirtualChannel channel) throws IOException {
            Process p = Runtime.getRuntime().exec(cmd, env, f);
            try {
                int rv = p.waitFor();
                if (rv == 0) {
                    return null;
                }
                StringOutputStream sos = new StringOutputStream();
                Util.copyStream(p.getInputStream(), sos);
                return sos.toString();
            } catch (InterruptedException e) {
                return "Process interrupted.";
            }
        }
    }

    private static final class AccurevChangeLogParser extends ChangeLogParser {
        /**
         * {@inheritDoc}
         */
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

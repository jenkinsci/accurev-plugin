package hudson.plugins.accurev.cmd;

import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;
import hudson.EnvVars;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.TaskListener;
import hudson.plugins.accurev.AccurevLauncher;
import hudson.plugins.accurev.AccurevSCM;
import hudson.plugins.accurev.AccurevSCM.AccurevSCMDescriptor;
import hudson.plugins.accurev.AccurevSCM.AccurevServer;
import hudson.plugins.accurev.parsers.output.ParseInfoToLoginName;
import hudson.util.ArgumentListBuilder;
import hudson.util.Secret;
import jenkins.model.Jenkins;
import org.apache.commons.lang.StringUtils;

import java.io.IOException;
import java.util.logging.Logger;

public class Login extends Command {
    private static final Logger logger = Logger.getLogger(Login.class.getName());

    /**
     * @return The currently logged in user "Principal" name, which may be
     * "(not logged in)" if not logged in.<br>
     * Returns null on failure.
     */
    private static String getLoggedInUsername(//
                                              String accurevTool, final AccurevServer server, //
                                              final EnvVars accurevEnv, //
                                              final FilePath workspace, //
                                              final TaskListener listener, //
                                              final Launcher launcher) throws IOException {
        final String commandDescription = "info command";
        final ArgumentListBuilder cmd = new ArgumentListBuilder();
        cmd.add("info");
        addServer(cmd, server);
        // returns username
        return AccurevLauncher.runCommand(commandDescription, accurevTool, launcher, cmd, null,
                accurevEnv, workspace, listener, logger, new ParseInfoToLoginName(), null);
    }

    public static boolean ensureLoggedInToAccurev(AccurevSCM scm, AccurevServer server, EnvVars accurevEnv, FilePath pathToRunCommandsIn, TaskListener listener,
                                                  Launcher launcher) throws IOException {
        String accurevTool = scm == null ? null : scm.getAccurevTool();
        if (server == null) {
            listener.getLogger().println("Authentication failure - Server is empty");
            return false;
        }
        final String requiredUsername = server.getUsername();
        if (StringUtils.isBlank(requiredUsername)) {
            listener.getLogger().println("Authentication failure - Username blank");
            return false;
        }
        AccurevSCMDescriptor.lock();
        try {
            final boolean loginRequired;
            if (server.isMinimiseLogins()) {
                final String currentUsername = getLoggedInUsername(accurevTool, server, accurevEnv, pathToRunCommandsIn, listener, launcher);
                if (StringUtils.isEmpty(currentUsername)) {
                    loginRequired = true;
                    listener.getLogger().println("Not currently authenticated with Accurev server");
                } else {
                    loginRequired = !currentUsername.equals(requiredUsername);
                    listener.getLogger().println(
                            "Currently authenticated with Accurev server as '" + currentUsername + (loginRequired ? "', login required" : "', not logging in again."));
                }
            } else {
                loginRequired = true;
            }
            if (loginRequired) {
                return accurevLogin(accurevTool, server, accurevEnv, pathToRunCommandsIn, listener, launcher);
            }
        } finally {
            AccurevSCMDescriptor.unlock();
        }
        return true;
    }

    private static boolean accurevLogin(String accurevTool, final AccurevServer server, //
                                        final EnvVars accurevEnv, //
                                        final FilePath workspace, //
                                        final TaskListener listener, //
                                        final Launcher launcher) throws IOException {
        StandardUsernamePasswordCredentials credentials = server.getCredentials();
        if (credentials == null) {
            listener.getLogger().println("Credentials not found");
            return false;
        }
        if (StringUtils.isBlank(credentials.getUsername())) {
            listener.getLogger().println("Credentials username cannot be blank");
            return false;
        }
        listener.getLogger().println("Authenticating with Accurev server...");
        final ArgumentListBuilder cmd = new ArgumentListBuilder();
        cmd.add("login");
        addServer(cmd, server);
        if (server.isUseNonexpiringLogin()) {
            cmd.add("-n");
        }
        cmd.add(credentials.getUsername());
        if (StringUtils.isEmpty(Secret.toString(credentials.getPassword()))) {
            if (launcher.isUnix()) {
                cmd.add("", true);
            } else {
                cmd.addQuoted("", true);
            }
        } else {
            cmd.add(server.getPassword(), true);
        }
        final boolean success = AccurevLauncher.runCommand("login", accurevTool, launcher, cmd, null, accurevEnv, workspace, listener, logger);
        if (success) {
            listener.getLogger().println("Authentication completed successfully.");
            return true;
        } else {
            return false;
        }
    }


    /**
     * @param server Accurev Server
     * @return boolean whether am successful
     * @throws IOException          failing IO
     * @throws InterruptedException failing interrupt
     *                              This method is called from dofillstreams and dofilldepots while configuring the job
     */
    public static boolean accurevLoginFromGlobalConfig(//
                                                       final AccurevServer server) throws IOException, InterruptedException {

        Jenkins jenkins = Jenkins.getInstance();
        TaskListener listener = TaskListener.NULL;
        Launcher launcher = jenkins.createLauncher(listener);
        EnvVars accurevEnv = new EnvVars();

        return ensureLoggedInToAccurev(null, server, accurevEnv, jenkins.getRootPath(), listener, launcher);
    }
}

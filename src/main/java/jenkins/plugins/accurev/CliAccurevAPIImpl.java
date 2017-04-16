package jenkins.plugins.accurev;

import hudson.EnvVars;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.TaskListener;
import hudson.plugins.accurev.*;
import hudson.util.ArgumentListBuilder;
import hudson.util.Secret;
import jenkins.plugins.accurev.util.AccurevUtils;
import jenkins.plugins.accurev.util.Parser;
import org.apache.commons.lang.StringUtils;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import javax.annotation.CheckForNull;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Initialized by josep on 04-03-2017.
 */
public class CliAccurevAPIImpl implements AccurevClient {
    public static final int TIMEOUT = Integer.getInteger(Accurev.class.getName() + ".timeOut", 10);
    transient Launcher launcher;
    TaskListener listener;
    String accurevExe;
    EnvVars environment;
    FilePath workspace;
    String url;

    public CliAccurevAPIImpl(String accurevExe, FilePath workspace, TaskListener listener, EnvVars environment, String url) {
        this.accurevExe = accurevExe;
        this.workspace = workspace;
        this.listener = listener;
        this.environment = environment;
        this.url = url;

        launcher = new Launcher.LocalLauncher(AccurevClient.verbose ? listener : TaskListener.NULL);
    }

    public UpdateCommand update() {
        return new UpdateCommand() {
            String stream;
            int latestTransaction;
            int previousTransaction;
            boolean preview;
            List<String> output;

            @Override
            public UpdateCommand stream(String stream) {
                this.stream = stream;
                return this;
            }

            @Override
            public UpdateCommand range(int latestTransaction, int previousTransaction) {
                this.latestTransaction = latestTransaction;
                this.previousTransaction = previousTransaction;
                return this;
            }

            @Override
            public UpdateCommand preview(List<String> output) {
                this.output = output;
                this.preview = true;
                return this;
            }

            void parse(String result) throws XmlPullParserException, IOException {
                XmlPullParser parser = Parser.parse(result);
                while (parser.next() != XmlPullParser.END_DOCUMENT) {
                    if (parser.getEventType() == XmlPullParser.START_TAG && "element".equalsIgnoreCase(parser.getName())) {
                        String path = parser.getAttributeValue("", "location");
                        if (path != null) {
                            output.add(AccurevUtils.cleanAccurevPath(path));
                        }
                    }
                }
            }

            @Override
            public void execute() throws AccurevException, InterruptedException {
                if (stream == null) {
                    throw new AccurevException("Cannot execute command without stream specified");
                }
                ArgumentListBuilder args = new ArgumentListBuilder();
                args.add("update", "-H", url, "-fx");
                if (latestTransaction != 0 || previousTransaction != 0) {
                    args.add(
                        "-s", stream,
                        "-t", latestTransaction + "-" + previousTransaction
                    );
                } else {
                    throw new AccurevException("Cannot execute command without transaction numbers specified");
                }
                if (preview) args.add("-i");

                try {
                    String result = launchCommand(args);
                    if (output != null) parse(result);
                } catch (AccurevException e) {
                    throw new AccurevException("Update command failed", e);
                } catch (IOException | XmlPullParserException e) {
                    throw new AccurevException("Failed to parse update command", e);
                }
            }
        };
    }

    public LoginCommand login() {
        return new LoginCommand() {
            String username;
            Secret password;

            @Override
            public LoginCommand username(String username) {
                this.username = username;
                return this;
            }

            @Override
            public LoginCommand password(Secret password) {
                this.password = password;
                return this;
            }

            @Override
            public void execute() throws AccurevException, InterruptedException {
                ArgumentListBuilder args = new ArgumentListBuilder();
                args.add("login", "-H", url, username);
                if (StringUtils.isBlank(Secret.toString(password))) {
                    // JENKINS-39066: empty quotes behave differently on OS.
                    if (launcher.isUnix()) args.add("", true);
                    else args.addQuoted("", true);
                } else {
                    args.addMasked(password);
                }

                try {
                    launchCommand(args);
                } catch (AccurevException e) {
                    throw new AccurevException("Login failed", e);
                }
            }
        };
    }

    @Override
    public HistCommand hist() {
        return null;
    }

    @Override
    public AccurevDepots getDepots() throws InterruptedException {
        String result = launchCommand("show", "-H", url, "-fx", "depots");
        return new AccurevDepots(result);
    }

    @Override
    @CheckForNull
    public AccurevStreams getStream(String stream) throws InterruptedException {
        String result = launchCommand("show", "-H", url, "-fx", "-s", stream, "streams");
        return new AccurevStreams(result);
    }

    @Override
    @CheckForNull
    public AccurevStreams getStreams() throws InterruptedException {
        String result = launchCommand("show", "-H", url, "-fx", "streams");
        return new AccurevStreams(result);
    }

    @Override
    @CheckForNull
    public AccurevStreams getStreams(String depot) throws InterruptedException {
        String result = launchCommand("show", "-H", url, "-fx", "-p", depot, "streams");
        return new AccurevStreams(result);
    }

    public String getVersion() throws InterruptedException {
        return launchCommand().split(" ")[1];
    }

    @Override
    public void syncTime() throws InterruptedException {
        launchCommand("synctime", "-H", url);
    }

    @Override
    public AccurevTransaction getLatestTransaction(String depot) throws InterruptedException {
        String result = launchCommand("accurev", "hist", "-fx", "-H", url, "-p", depot, "-t", "now.1");
        return (new AccurevTransactions(result)).get(0);
    }

    private String launchCommand(String... args) throws AccurevException, InterruptedException {
        return launchCommand(new ArgumentListBuilder(args));
    }

    private String launchCommand(ArgumentListBuilder args) throws AccurevException, InterruptedException {
        return launchCommandIn(args, workspace);
    }

    private String launchCommandIn(ArgumentListBuilder args, FilePath workspace) throws AccurevException, InterruptedException {
        return LaunchCommandIn(args, workspace, environment);
    }

    private String LaunchCommandIn(ArgumentListBuilder args, FilePath workspace, EnvVars environment) throws AccurevException, InterruptedException {
        return LaunchCommandIn(args, workspace, environment, TIMEOUT);
    }

    private String LaunchCommandIn(ArgumentListBuilder args, FilePath workspace, EnvVars env, int timeout) throws AccurevException, InterruptedException {
        ByteArrayOutputStream fos = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();

        EnvVars environment = new EnvVars(env);
        if (!env.containsKey("ACCUREV_HOME")) {
            String path = AccurevUtils.getRootPath(workspace);
            if (StringUtils.isNotBlank(path))
                environment.put("ACCUREV_HOME", path);
        }
        String command = accurevExe + " " + args.toString();
        try {
            args.prepend(accurevExe);
            Launcher.ProcStarter p = launcher.launch().cmds(args).envs(environment).stdout(fos).stderr(err);
            if (workspace != null) p.pwd(workspace);
            int status = p.start().joinWithTimeout(timeout, TimeUnit.MINUTES, listener);
            String result = fos.toString(Charset.defaultCharset().toString());
            if (status != 0) {
                throw new AccurevException("Command \"" + command + "\""
                    + "\nreturned status code: " + status
                    + "\nstdout: " + result
                    + "\nstderr: " + err.toString(Charset.defaultCharset().toString()));
            }
            return result;
        } catch (AccurevException | InterruptedException e) {
            throw e;
        } catch (IOException e) {
            throw new AccurevException("Error performing command: " + command, e);
        } catch (Throwable t) {
            throw new AccurevException("Error performing accurev command", t);
        }
    }
}

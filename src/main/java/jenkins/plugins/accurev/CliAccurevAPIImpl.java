package jenkins.plugins.accurev;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.List;
import java.util.concurrent.TimeUnit;

import javax.annotation.CheckForNull;

import org.apache.commons.lang.StringUtils;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import hudson.EnvVars;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.TaskListener;
import hudson.util.ArgumentListBuilder;
import hudson.util.Secret;

import jenkins.plugins.accurev.util.AccurevUtils;
import jenkins.plugins.accurev.util.Parser;
import hudson.plugins.accurev.AccurevDepots;
import hudson.plugins.accurev.AccurevStreams;
import hudson.plugins.accurev.AccurevTransactions;

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
        this.url = url;

        if (!environment.containsKey("ACCUREV_HOME")) {
            String path = AccurevUtils.getRootPath(workspace);
            if (StringUtils.isNotBlank(path))
                environment.put("ACCUREV_HOME", path);
        }

        this.environment = environment;
        launcher = new Launcher.LocalLauncher(AccurevClient.verbose ? listener : TaskListener.NULL);
    }

    public ArgumentListBuilder command(String cmd, String... args) {
        return new ArgumentListBuilder(cmd, "-H", url).add(args);
    }

    public ArgumentListBuilder commandXML(String cmd, String... args) {
        return command(cmd).add("-fx").add(args);
    }

    public UpdateCommand update() {
        return new UpdateCommand() {
            String referenceTree;
            String stream;
            long latestTransaction;
            long previousTransaction;
            boolean preview;
            List<String> output;

            @Override
            public UpdateCommand referenceTree(String referenceTree) {
                this.referenceTree = referenceTree;
                return this;
            }

            @Override
            public UpdateCommand stream(String stream) {
                this.stream = stream;
                return this;
            }

            @Override
            public UpdateCommand range(long latestTransaction, long previousTransaction) {
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
                ArgumentListBuilder args = commandXML("update");
                if (stream != null) {
                    args.add("-s", stream);
                } else if (referenceTree != null) {
                    args.add("-r", referenceTree);
                } else {
                    throw new AccurevException("Cannot execute command without stream or reference tree specified");
                }
                if (latestTransaction != 0L && previousTransaction != 0L) {
                    args.add("-t", latestTransaction + "-" + previousTransaction);
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

    public PopulateCommand populate() {
        return new PopulateCommand() {
            Set<String> elements;
            String timespec;
            boolean overwrite;
            String stream;

            @Override
            public PopulateCommand stream(String stream) {
                this.stream = stream;
                return this;
            }

            @Override
            public PopulateCommand overwrite(boolean overwrite) {
                this.overwrite = overwrite;
                return this;
            }

            @Override
            public PopulateCommand timespec(String timespec) {
                this.timespec = timespec;
                return this;
            }

            @Override
            public PopulateCommand elements(Set<String> elements) {
                this.elements = elements;
                return this;
            }

            @Override
            public void execute() throws AccurevException, InterruptedException {
                ArgumentListBuilder args = command("pop");
                if (stream != null) {
                    args.add("-v", stream);
                }

                args.add("-L", workspace.getRemote());

                if (overwrite) args.add("-O");

                args.add("-R");
                if (elements == null) {
                    args.add(".");
                } else {
                    elements.forEach(args::add);
                }
                launchCommand(args);
            }
        };
    }

    @Override
    public HistCommand hist() {
        return new HistCommand() {
            String depot;
            String stream;
            String timespec;
            int count;
            AccurevTransactions transactions;

            @Override
            public HistCommand depot(String depot) {
                this.depot = depot;
                return this;
            }

            @Override
            public HistCommand stream(String stream) {
                this.stream = stream;
                return this;
            }

            @Override
            public HistCommand timespec(String timespec) {
                this.timespec = timespec;
                return this;
            }

            @Override
            public HistCommand count(int count) {
                this.count = count;
                return this;
            }

            public HistCommand toTransactions(AccurevTransactions transactions) {
                this.transactions = transactions;
                return this;
            }

            @Override
            public void execute() throws AccurevException, InterruptedException {
                ArgumentListBuilder args = commandXML("hist");
                if (depot != null) {
                    args.add("-p", depot);
                }
                if (stream != null) {
                    args.add("-s", stream);
                }
                if (timespec != null) {
                    if (count != 0)
                        timespec = timespec + "." + count;
                    args.add("-t", timespec);
                }
                String result = launchCommand(args);
                if (transactions != null) {
                    transactions.addAll(new AccurevTransactions(result));
                }
            }
        };
    }

    @Override
    public StreamsCommand streams() {
        return new StreamsCommand() {
            boolean restricted;
            String depot;
            String stream;
            AccurevStreams streams;

            @Override
            public StreamsCommand depot(String depot) {
                this.depot = depot;
                return this;
            }

            @Override
            public StreamsCommand stream(String stream) {
                this.stream = stream;
                return this;
            }

            @Override
            public StreamsCommand restricted() {
                restricted = !restricted;
                return this;
            }

            @Override
            public StreamsCommand toStreams(AccurevStreams streams) {
                this.streams = streams;
                return this;
            }

            ArgumentListBuilder builder() {
                ArgumentListBuilder args = commandXML("show");
                if (depot != null)
                    args.add("-p", depot);
                if (stream != null)
                    args.add("-s", stream);
                args.add("streams");
                return args;
            }

            @Override
            public void execute() throws AccurevException, InterruptedException {
                String result;
                if (restricted && streams != null) {
                    while (stream != null) {
                        streams.putAll(new AccurevStreams(launchCommand(builder())));
                        stream = streams.get(stream).getBasisName();
                    }
                } else {
                    result = launchCommand(builder());
                    if (streams != null) {
                        streams.putAll(new AccurevStreams(result));
                    }
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
                ArgumentListBuilder args = command("login", username);
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
    public AccurevDepots getDepots() throws InterruptedException {
        String result = launchCommand(commandXML("show", "depots"));
        return new AccurevDepots(result);
    }

    @Override
    @CheckForNull
    public AccurevStreams getStream(String stream) throws InterruptedException {
        String result = launchCommand(commandXML("show", "-s", stream, "streams"));
        return new AccurevStreams(result);
    }

    @Override
    @CheckForNull
    public AccurevStreams getStreams() throws InterruptedException {
        String result = launchCommand(commandXML("show", "streams"));
        return new AccurevStreams(result);
    }

    @Override
    @CheckForNull
    public AccurevStreams getStreams(String depot) throws InterruptedException {
        String result = launchCommand(commandXML("show", "-p", depot, "streams"));
        return new AccurevStreams(result);
    }

    public String getVersion() throws InterruptedException {
        return launchCommand().split("\\s+")[1];
    }

    @Override
    public void syncTime() throws InterruptedException {
        launchCommand(command("synctime"));
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

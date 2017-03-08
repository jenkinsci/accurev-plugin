package hudson.plugins.accurev.cmd;

import hudson.EnvVars;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.TaskListener;
import hudson.plugins.accurev.AccurevLauncher;
import hudson.plugins.accurev.AccurevSCM;
import hudson.plugins.accurev.AccurevSCM.AccurevServer;
import hudson.plugins.accurev.AccurevStream;
import hudson.plugins.accurev.XmlParserFactory;
import hudson.plugins.accurev.parsers.xml.ParseShowStreams;
import hudson.util.ArgumentListBuilder;
import hudson.util.ComboBoxModel;
import jenkins.model.Jenkins;
import org.xmlpull.v1.XmlPullParserFactory;

import javax.annotation.CheckForNull;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.Lock;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class ShowStreams extends Command {
    private static final Logger LOGGER = Logger.getLogger(ShowStreams.class.getName());

    @CheckForNull
    public static Map<String, AccurevStream> getStreams(//
                                                        final AccurevSCM scm, //
                                                        final String nameOfStreamRequired, //
                                                        final AccurevServer server, //
                                                        final EnvVars accurevEnv, //
                                                        final FilePath workspace, //
                                                        final TaskListener listener, //
                                                        final Launcher launcher) throws IOException {
        final Map<String, AccurevStream> streams;
        if (scm.isIgnoreStreamParent()) {
            streams = getOneStream(nameOfStreamRequired, accurevEnv, workspace, listener, launcher, scm);
        } else if (server.isUseRestrictedShowStreams()) {
            streams = getAllAncestorStreams(nameOfStreamRequired, scm, accurevEnv, workspace, listener, launcher);
        } else {
            streams = getAllStreams(scm, server, scm.getDepot(), scm.getOptionalLock(), accurevEnv, workspace, listener, launcher);
        }
        return streams;
    }

    public static Map<String, AccurevStream> getAllStreams(//
                                                           AccurevSCM scm, final AccurevServer server, //
                                                           final String depot, //
                                                           final Lock lock, //
                                                           final EnvVars accurevEnv, //
                                                           final FilePath workspace, //
                                                           final TaskListener listener, //
                                                           final Launcher launcher) throws IOException {
        final ArgumentListBuilder cmd = new ArgumentListBuilder();
        String accurevTool = scm == null ? null : scm.getAccurevTool();
        cmd.add("show");
        addServer(cmd, server);
        cmd.add("-fx");
        cmd.add("-p");
        cmd.add(depot);
        cmd.add("streams");
        XmlPullParserFactory parser = XmlParserFactory.getFactory();
        if (parser == null) throw new IOException("No XML Parser");
        final Map<String, AccurevStream> streams = AccurevLauncher.runCommand("Show streams command", accurevTool, launcher, cmd, lock, accurevEnv,
                workspace, listener, LOGGER, parser, new ParseShowStreams(), depot);
        setParents(streams);
        return streams;
    }

    private static Map<String, AccurevStream> getAllAncestorStreams(//
                                                                    final String nameOfStreamRequired, //
                                                                    final AccurevSCM scm,
                                                                    final EnvVars accurevEnv, //
                                                                    final FilePath workspace, //
                                                                    final TaskListener listener, //
                                                                    final Launcher launcher) throws IOException {
        final Map<String, AccurevStream> streams = new HashMap<>();
        String streamName = nameOfStreamRequired;
        while (streamName != null && !streamName.isEmpty()) {
            final Map<String, AccurevStream> oneStream = getOneStream(streamName, accurevEnv, workspace, listener, launcher, scm);
            final AccurevStream theStream = oneStream == null ? null : oneStream.get(streamName);
            streamName = null;
            if (theStream != null) {
                if (theStream.getBasisName() != null) {
                    streamName = theStream.getBasisName();
                } else if (theStream.getBasisNumber() != null) {
                    streamName = theStream.getBasisNumber().toString();
                }
                streams.putAll(oneStream);
            }
        }
        setParents(streams);
        return streams;
    }

    private static Map<String, AccurevStream> getOneStream(//
                                                           final String streamName, //
                                                           final EnvVars accurevEnv, //
                                                           final FilePath workspace, //
                                                           final TaskListener listener, //
                                                           final Launcher launcher, AccurevSCM scm) throws IOException {
        final ArgumentListBuilder cmd = new ArgumentListBuilder();
        cmd.add("show");
        addServer(cmd, scm.getServer());
        cmd.add("-fx");
        cmd.add("-p");
        cmd.add(scm.getDepot());
        cmd.add("-s");
        cmd.add(streamName);
        cmd.add("streams");
        XmlPullParserFactory parser = XmlParserFactory.getFactory();
        if (parser == null) throw new IOException("No XML Parser");
        return AccurevLauncher.runCommand("Restricted show streams command", scm.getAccurevTool(), launcher, cmd, scm.getOptionalLock(),
                accurevEnv, workspace, listener, LOGGER, parser, new ParseShowStreams(), scm.getDepot());
    }

    private static void setParents(Map<String, AccurevStream> streams) {
        //build the tree
        if (streams != null && streams.size() > 1)
            streams.values()
                    .stream()
                    .filter(stream -> stream.getBasisName() != null)
                    .forEach(stream -> stream.setParent(streams.get(stream.getBasisName())));
    }

    //Populating streams dynamically in the global config page

    public static ComboBoxModel getStreamsForGlobalConfig(//
                                                          final AccurevServer server,
                                                          final String depot,
                                                          final ComboBoxModel cbm
    ) throws IOException {
        Jenkins jenkins = Jenkins.getInstance();
        TaskListener listener = TaskListener.NULL;
        Launcher launcher = jenkins.createLauncher(listener);
        EnvVars accurevEnv = new EnvVars();
        Map<String, AccurevStream> allStreams = getAllStreams(null, server, depot, null, accurevEnv, jenkins.getRootPath(), listener, launcher);
        if (allStreams == null) return cbm;
        if (allStreams.isEmpty()) return cbm;
        List<String> streamNames = allStreams
                .values()
                .stream()
                .filter(stream -> stream.getType() != AccurevStream.StreamType.WORKSPACE)
                .map(AccurevStream::getName)
                .collect(Collectors.toList());
        cbm.addAll(streamNames);
        Collections.sort(cbm);
        return cbm;
    }
}

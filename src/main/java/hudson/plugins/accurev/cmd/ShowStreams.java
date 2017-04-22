package hudson.plugins.accurev.cmd;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.Lock;
import java.util.logging.Logger;

import javax.annotation.CheckForNull;

import org.xmlpull.v1.XmlPullParserFactory;

import hudson.EnvVars;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.TaskListener;
import hudson.util.ArgumentListBuilder;

import hudson.plugins.accurev.AccurevLauncher;
import hudson.plugins.accurev.AccurevSCM;
import hudson.plugins.accurev.AccurevSCM.AccurevServer;
import hudson.plugins.accurev.AccurevStream;
import hudson.plugins.accurev.XmlParserFactory;
import hudson.plugins.accurev.parsers.xml.ParseShowStreams;

@Deprecated
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
        cmd.add("show");
        addServer(cmd, server);
        cmd.add("-fx");
        cmd.add("-p");
        cmd.add(depot);
        cmd.add("streams");
        XmlPullParserFactory parser = XmlParserFactory.getFactory();
        if (parser == null) throw new IOException("No XML Parser");
        final Map<String, AccurevStream> streams = AccurevLauncher.runCommand("Show streams command", scm.getAccurevTool(), launcher, cmd, lock, accurevEnv,
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

}

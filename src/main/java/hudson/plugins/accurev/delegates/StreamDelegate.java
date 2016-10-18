package hudson.plugins.accurev.delegates;

import hudson.model.Job;
import hudson.model.Run;
import hudson.plugins.accurev.AccurevSCM;
import hudson.plugins.accurev.AccurevStream;
import hudson.plugins.accurev.CheckForChanges;
import hudson.plugins.accurev.cmd.ShowStreams;
import hudson.scm.PollingResult;

import java.io.File;
import java.io.IOException;
import java.util.Date;
import java.util.Map;
import java.util.logging.Logger;

/**
 * @author raymond
 */
public class StreamDelegate extends AbstractModeDelegate {
    private static final Logger logger = Logger.getLogger(StreamDelegate.class.getName());

    public StreamDelegate(AccurevSCM scm) {
        super(scm);
    }

    @Override
    protected boolean checkout(Run<?, ?> build, File changeLogFile) throws IOException, InterruptedException {
        return true;
    }

    @Override
    protected String getPopulateFromMessage() {
        return "from stream";
    }

    @Override
    protected String getPopulateStream() {
        return localStream;
    }

    @Override
    protected boolean isSteamColorEnabled() {
        return true;
    }

    @Override
    protected String getStreamColorStream() {
        return localStream;
    }

    @Override
    protected String getStreamColor() {
        return "#FFFFFF";
    }

    @Override
    protected PollingResult checkForChanges(Job<?, ?> project) throws IOException, InterruptedException {
        final Run<?, ?> lastBuild = project.getLastBuild();
        if (lastBuild == null) {
            listener.getLogger().println("Project has never been built");
            return PollingResult.BUILD_NOW;
        }
        final Date buildDate = lastBuild.getTimestamp().getTime();
        try {
            localStream = getPollingStream(project);
        } catch (IllegalArgumentException ex) {
            listener.getLogger().println(ex.getMessage());
            return PollingResult.NO_CHANGES;
        }
        final Map<String, AccurevStream> streams = scm.isIgnoreStreamParent() ? null : ShowStreams.getStreams(scm, localStream, server,
                accurevEnv, jenkinsWorkspace, listener, launcher);
        AccurevStream stream = streams == null ? null : streams.get(localStream);

        if (stream == null) {
            listener.getLogger().println("Stream not found or Accurev login failed.");
            return PollingResult.NO_CHANGES;
        }
        // There may be changes in a parent stream that we need to factor in.
        do {
            if (CheckForChanges.checkStreamForChanges(server, accurevEnv, jenkinsWorkspace, listener, launcher, stream,
                    buildDate, logger, scm)) {
                return PollingResult.BUILD_NOW;
            }
            stream = stream.getParent();
        } while (stream != null && stream.isReceivingChangesFromParent());
        return PollingResult.NO_CHANGES;

    }
}

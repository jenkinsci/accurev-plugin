package hudson.plugins.accurev.delegates;

import hudson.EnvVars;
import hudson.model.Run;
import hudson.plugins.accurev.AccurevLauncher;
import hudson.plugins.accurev.AccurevSCM;
import hudson.plugins.accurev.cmd.Command;
import hudson.util.ArgumentListBuilder;
import org.apache.commons.lang.StringUtils;

import java.io.File;
import java.io.IOException;
import java.util.logging.Logger;

/**
 * @author raymond
 */
public class SnapshotDelegate extends StreamDelegate {

    private static final Logger logger = Logger.getLogger(SnapshotDelegate.class.getName());
    private static final String DEFAULT_SNAPSHOT_NAME_FORMAT = "${JOB_NAME}_${BUILD_NUMBER}";
    private String snapshotName;

    public SnapshotDelegate(AccurevSCM scm) {
        super(scm);
    }

    private String calculateSnapshotName(final Run<?, ?> build) throws IOException, InterruptedException {
        String snapshotNameFormat = scm.getSnapshotNameFormat();
        final String actualFormat = StringUtils.isBlank(snapshotNameFormat) ? DEFAULT_SNAPSHOT_NAME_FORMAT : snapshotNameFormat.trim();
        final EnvVars environment = build.getEnvironment(listener);
        return environment.expand(actualFormat);
    }

    @Override
    protected boolean checkout(Run<?, ?> build, File changeLogFile) throws IOException, InterruptedException {
        snapshotName = calculateSnapshotName(build);
        listener.getLogger().println("Creating snapshot: " + snapshotName + "...");
        build.getEnvironment(listener).put("ACCUREV_SNAPSHOT", snapshotName);
        // snapshot command: accurev mksnap -H <server> -s <snapshotName> -b <backing_stream> -t now
        final ArgumentListBuilder mksnapcmd = new ArgumentListBuilder();
        mksnapcmd.add(accurevPath);
        mksnapcmd.add("mksnap");
        Command.addServer(mksnapcmd, server);
        mksnapcmd.add("-s");
        mksnapcmd.add(snapshotName);
        mksnapcmd.add("-b");
        mksnapcmd.add(localStream);
        mksnapcmd.add("-t");
        mksnapcmd.add("now");
        if (!AccurevLauncher.runCommand("Create snapshot command", launcher, mksnapcmd, null, scm.getOptionalLock(),
                accurevEnv, jenkinsWorkspace, listener, logger, true)) {
            return false;
        }
        listener.getLogger().println("Snapshot created successfully.");
        return true;
    }

    @Override
    protected boolean isSteamColorEnabled() {
        return false;
    }

    @Override
    protected String getPopulateFromMessage() {
        return "from snapshot";
    }

    @Override
    protected String getPopulateStream() {
        return snapshotName;
    }

}

package hudson.plugins.accurev;

import hudson.FilePath;
import hudson.Util;
import hudson.model.TaskListener;
import hudson.remoting.VirtualChannel;
import org.jenkinsci.remoting.RoleChecker;

import java.io.File;
import java.io.IOException;
import java.util.List;

final class PurgeWorkspaceOverlaps implements FilePath.FileCallable<Boolean> {

    private final List<String> filelist;
    private final TaskListener listener;

    public PurgeWorkspaceOverlaps(TaskListener listener, List<String> filelist) {
        this.filelist = filelist;
        this.listener = listener;
    }

    /**
     * {@inheritDoc}
     */
    public Boolean invoke(File ws, VirtualChannel channel) throws IOException {
        listener.getLogger().println("Purging overlaps...");
        for (String filename : filelist) {
            File toPurge = new File(ws, filename);
            Util.deleteFile(toPurge);
            listener.getLogger().println("... " + toPurge.getAbsolutePath());
        }
        listener.getLogger().println("Overlaps purged.");
        return Boolean.TRUE;
    }

    @Override
    public void checkRoles(RoleChecker roleChecker) throws SecurityException {
        //TODO: Implement Role check
    }
}

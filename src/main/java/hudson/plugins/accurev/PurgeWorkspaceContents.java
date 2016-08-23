package hudson.plugins.accurev;

import hudson.FilePath;
import hudson.Util;
import hudson.model.TaskListener;
import hudson.remoting.VirtualChannel;
import org.jenkinsci.remoting.RoleChecker;

import java.io.File;
import java.io.IOException;

final class PurgeWorkspaceContents implements FilePath.FileCallable<Boolean> {

    private final TaskListener listener;

    public PurgeWorkspaceContents(TaskListener listener) {
        this.listener = listener;
    }

    /**
     * {@inheritDoc}
     */
    public Boolean invoke(File ws, VirtualChannel channel) throws IOException {
        listener.getLogger().println("Purging workspace...");
        System.runFinalization();
        System.gc();
        System.runFinalization();
        // TODO: Retry delete - on Windows, it can fail claiming that the file is in use.
        Util.deleteContentsRecursive(ws);
        listener.getLogger().println("Workspace purged.");
        return Boolean.TRUE;
    }

    @Override
    public void checkRoles(RoleChecker roleChecker) throws SecurityException {
        //TODO: Implement Role check
    }
}

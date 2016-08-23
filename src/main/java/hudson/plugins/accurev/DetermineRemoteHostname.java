package hudson.plugins.accurev;

import hudson.remoting.Callable;
import org.jenkinsci.remoting.RoleChecker;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;

public class DetermineRemoteHostname implements Callable<RemoteWorkspaceDetails, UnknownHostException> {

    private final String path;

    public DetermineRemoteHostname(String path) {
        this.path = path;
    }

    /**
     * {@inheritDoc}
     */
    public RemoteWorkspaceDetails call() throws UnknownHostException {
        InetAddress addr = InetAddress.getLocalHost();
        File f = new File(path);
        String path;
        try {
            path = f.getCanonicalPath();
        } catch (IOException e) {
            path = f.getAbsolutePath();
        }

        return new RemoteWorkspaceDetails(addr.getCanonicalHostName(), path, File.separator);
    }

    @Override
    public void checkRoles(RoleChecker roleChecker) throws SecurityException {
        //TODO: Implement Role check
    }
}

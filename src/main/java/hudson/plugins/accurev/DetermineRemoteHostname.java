package hudson.plugins.accurev;

import hudson.remoting.Callable;
import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import org.jenkinsci.remoting.RoleChecker;

public class DetermineRemoteHostname
    implements Callable<RemoteWorkspaceDetails, UnknownHostException> {

  private final String path;
  private static final long serialVersionUID = 1L;

  public DetermineRemoteHostname(String path) {
    this.path = path;
  }

  /** {@inheritDoc} */
  public RemoteWorkspaceDetails call() throws UnknownHostException {
    InetAddress addr = InetAddress.getLocalHost();
    File f = new File(path);
    String path;
    try {
      path = f.getCanonicalPath();
    } catch (IOException e) {
      path = f.getAbsolutePath();
    }
    String ipPattern =
        "^(([01]?[0-9]?[0-9]|2([0-4][0-9]|5[0-5]))\\.){3}([01]?[0-9]?[0-9]|2([0-4][0-9]|5[0-5]))$";
    String hostName = addr.getCanonicalHostName(); // try full hostname
    if (hostName.matches(ipPattern)) {
      hostName = addr.getHostName(); // try hostname
    }
    // Accurev does not accept IP addresses so we are going to throw an error.
    if (hostName.matches(ipPattern)) {
      throw new UnknownHostException("Found IP, but need HostName, ensure proper FQDN.");
    }
    return new RemoteWorkspaceDetails(hostName, path);
  }

  @Override
  public void checkRoles(RoleChecker roleChecker) throws SecurityException {
    // TODO: Implement Role check
  }
}

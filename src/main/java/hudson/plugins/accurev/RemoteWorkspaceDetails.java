package hudson.plugins.accurev;

import java.io.File;
import java.io.Serializable;

public class RemoteWorkspaceDetails implements Serializable {

  private final String hostName;
  private final String path;
  private final String fileSeparator;
  private static final long serialVersionUID = 1L;

  public RemoteWorkspaceDetails(String hostName, String path) {
    this.hostName = hostName;
    this.path = path;
    this.fileSeparator = File.separator;
  }

  /**
   * Getter for property 'hostName'.
   *
   * @return Value for property 'hostName'.
   */
  public String getHostName() {
    return hostName;
  }

  /**
   * Getter for property 'path'.
   *
   * @return Value for property 'path'.
   */
  public String getPath() {
    return path;
  }

  /**
   * Getter for property 'fileSeparator'.
   *
   * @return Value for property 'fileSeparator'.
   */
  public String getFileSeparator() {
    return fileSeparator;
  }
}

package hudson.plugins.accurev;

import java.io.Serializable;

public class RefTreeExternalFile implements Serializable {

  private final String location;
  private final String status;
  private static final long serialVersionUID = 1L;

  public RefTreeExternalFile(String location, String status) {
    this.location = location;
    this.status = status;
  }

  /**
   * Getter for property 'location'.
   *
   * @return Value for property 'location'.
   */
  public String getLocation() {
    return location;
  }

  /**
   * Getter for property 'status'.
   *
   * @return Value for property 'status'.
   */
  public String getStatus() {
    return status;
  }
}

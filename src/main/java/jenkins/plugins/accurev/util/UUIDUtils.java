package jenkins.plugins.accurev.util;

import java.util.UUID;
import org.apache.commons.lang.StringUtils;

/** Initialized by josp on 21/09/16. */
public class UUIDUtils {

  public static boolean isValid(String uuid) {
    if (StringUtils.isEmpty(uuid)) {
      return false;
    }
    try {
      UUID fromStringUUID = UUID.fromString(uuid);
      String toStringUUID = fromStringUUID.toString();
      return toStringUUID.equals(uuid);
    } catch (IllegalArgumentException e) {
      return false;
    }
  }

  public static boolean isNotValid(String uuid) {
    return !isValid(uuid);
  }
}

package hudson.plugins.accurev;

import org.apache.commons.lang.StringUtils;

import java.util.UUID;

/**
 * Created by josp on 21/09/16.
 */
public class UUIDUtils {
    public static boolean isValid(String uuid) {
        if (StringUtils.isEmpty(uuid)) return false;
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

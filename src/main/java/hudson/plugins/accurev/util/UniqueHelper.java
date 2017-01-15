package hudson.plugins.accurev.util;

import org.apache.commons.lang.StringUtils;

import java.util.UUID;

/**
 * @author josp
 */
public class UniqueHelper {
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

    public static String randomUUID() {
        return UUID.randomUUID().toString();
    }
}

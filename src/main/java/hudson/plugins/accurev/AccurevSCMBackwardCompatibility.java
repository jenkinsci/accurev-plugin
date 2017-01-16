package hudson.plugins.accurev;

import hudson.EnvVars;
import hudson.model.*;
import hudson.plugins.accurev.AccurevSCM.AccurevSCMDescriptor;
import hudson.plugins.accurev.extensions.AccurevSCMExtension;
import hudson.plugins.accurev.extensions.AccurevSCMExtensionDescriptor;
import hudson.scm.ChangeLogParser;
import hudson.scm.SCM;
import hudson.util.DescribableList;
import org.apache.commons.lang.StringUtils;

import java.io.IOException;
import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.locks.Lock;

/**
 * This is meant to keep backwards compatibility for anyone before version 1.0.0
 *
 * @author Joseph 'Casz' Petersen
 * @since 1.0.0
 */
public abstract class AccurevSCMBackwardCompatibility extends SCM implements Serializable {

// ------------------------------ FIELDS ------------------------------

    @Deprecated
    transient String serverName;
    @Deprecated
    transient String depot;
    @Deprecated
    transient String stream;
    @Deprecated
    transient boolean ignoreStreamParent;
    @Deprecated
    transient String wspaceORreftree;
    @Deprecated
    transient boolean cleanreftree;
    @Deprecated
    transient String workspace;
    @Deprecated
    transient boolean useSnapshot;
    @Deprecated
    transient boolean dontPopContent;
    @Deprecated
    transient String snapshotNameFormat;
    @Deprecated
    transient boolean synctime;
    @Deprecated
    transient String reftree;
    @Deprecated
    transient String subPath;
    @Deprecated
    transient String filterForPollSCM;
    @Deprecated
    transient String directoryOffset;
    @Deprecated
    transient boolean useReftree;
    @Deprecated
    transient boolean useWorkspace;
    @Deprecated
    transient boolean noWspaceNoReftree;
    @Deprecated
    transient String serverUUID;

    @Deprecated
    protected static final List<String> DEFAULT_VALID_STREAM_TRANSACTION_TYPES = Collections
            .unmodifiableList(Arrays.asList("chstream", "defcomp", "mkstream", "promote"));
    @Deprecated
    protected static final List<String> DEFAULT_VALID_WORKSPACE_TRANSACTION_TYPES = Collections
            .unmodifiableList(Arrays.asList("add", "chstream", "co", "defcomp", "defunct", "keep",
                    "mkstream", "move", "promote", "purge", "dispatch"));

    abstract DescribableList<AccurevSCMExtension, AccurevSCMExtensionDescriptor> getExtensions();

    void migrateThyLegacy() {

    }

    private void addIfMissing(AccurevSCMExtension ext) throws IOException {
        if (getExtensions().get(ext.getClass()) == null)
            getExtensions().add(ext);
    }

    /**
     * {@inheritDoc}
     *
     * @return ChangeLogParser
     */
    public ChangeLogParser createChangeLogParser() {
        return new AccurevChangeLogParser();
    }

    @Deprecated
    public boolean isUseSnapshot() {
        return useSnapshot;
    }

    @Deprecated
    public String getFilterForPollSCM() {
        return filterForPollSCM;
    }

    @Deprecated
    public String getSubPath() {
        return subPath;
    }

    @Deprecated
    public String getWspaceORreftree() {
        return wspaceORreftree;
    }

    @Deprecated
    public Lock getOptionalLock() {
        return null;
    }

    @Deprecated
    public String getStream() {
        return stream;
    }

    @Deprecated
    public void setServerUUID(String serverUUID) {
        this.serverUUID = serverUUID;
    }

    @Deprecated
    public boolean isIgnoreStreamParent() {
        return ignoreStreamParent;
    }

    @Deprecated
    public boolean hasStringVariableReference(final String str) {
        return StringUtils.isNotEmpty(str) && str.startsWith("$");
    }

    @Deprecated
    public String getPollingStream(Job<?, ?> project, TaskListener listener) {
        String parsedLocalStream;
        if (hasStringVariableReference(getStream())) {
            ParametersDefinitionProperty paramDefProp = project
                    .getProperty(ParametersDefinitionProperty.class);

            if (paramDefProp == null) {
                throw new IllegalArgumentException(
                        "Polling is not supported when stream name has a variable reference '" + getStream() + "'.");
            }

            Map<String, String> keyValues = new TreeMap<>();

            /* Scan for all parameter with an associated default values */
            for (ParameterDefinition paramDefinition : paramDefProp.getParameterDefinitions()) {

                ParameterValue defaultValue = paramDefinition.getDefaultParameterValue();

                if (defaultValue instanceof StringParameterValue) {
                    StringParameterValue strdefvalue = (StringParameterValue) defaultValue;
                    keyValues.put(defaultValue.getName(), strdefvalue.value);
                }
            }

            final EnvVars environment = new EnvVars(keyValues);
            parsedLocalStream = environment.expand(getStream());
            listener.getLogger().println("... expanded '" + getStream() + "' to '" + parsedLocalStream + "'.");
        } else {
            parsedLocalStream = getStream();
        }

        if (hasStringVariableReference(parsedLocalStream)) {
            throw new IllegalArgumentException(
                    "Polling is not supported when stream name has a variable reference '" + getStream() + "'.");
        }
        return parsedLocalStream;
    }

    public String getWorkspace() {
        return workspace;
    }

    public String getSnapshotNameFormat() {
        return snapshotNameFormat;
    }

    public String getReftree() {
        return reftree;
    }

    public String getDirectoryOffset() {
        return directoryOffset;
    }

    public boolean isSynctime() {
        return synctime;
    }

    public boolean isDontPopContent() {
        return dontPopContent;
    }

    public static String deobfuscate(String s) {
        final String __OBFUSCATE = "OBF:";
        if (s.startsWith(__OBFUSCATE))
            s = s.substring(__OBFUSCATE.length());
        if (StringUtils.isEmpty(s)) return "";
        byte[] b = new byte[s.length() / 2];
        int l = 0;
        for (int i = 0; i < s.length(); i += 4) {
            String x = s.substring(i, i + 4);
            int i0 = Integer.parseInt(x, 36);
            int i1 = (i0 / 256);
            int i2 = (i0 % 256);
            b[l++] = (byte) ((i1 + i2 - 254) / 2);
        }
        return new String(b, 0, l, StandardCharsets.UTF_8);
    }

    @Override
    public AccurevSCMDescriptor getDescriptor() {
        return (AccurevSCMDescriptor) super.getDescriptor();
    }

    // -------------------------- INNER CLASSES --------------------------

    /**
     * Class responsible for parsing change-logs recorded by the builds. If this
     * is renamed or moved it'll break data-compatibility with old builds.
     */
    private static final class AccurevChangeLogParser extends ParseChangeLog {
    }
}
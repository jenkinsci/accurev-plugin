package hudson.plugins.accurev.extensions.impl;

import hudson.plugins.accurev.config.AccurevServerConfig;
import hudson.plugins.accurev.extensions.AccurevSCMExtension;
import hudson.plugins.accurev.extensions.AccurevSCMExtensionDescriptor;

/**
 * @author josp
 */
public class AccurevDepot extends AccurevSCMExtension {

    private final int number;
    private final String name;
    private final boolean caseSensitive;
    private AccurevServerConfig config;

    public AccurevDepot(int number, String name, boolean caseSensitive, AccurevServerConfig config) {
        this.number = number;
        this.name = name;
        this.caseSensitive = caseSensitive;
        this.config = config;
    }

    public boolean isCaseSensitive() {
        return caseSensitive;
    }

    public String getName() {
        return name;
    }

    public int getNumber() {
        return number;
    }

    public AccurevServerConfig getConfig() {
        return config;
    }

    public String getHostPortDepot() {
        return config.getHostPort() + "/" + name;
    }

    public static class DescriptorImpl extends AccurevSCMExtensionDescriptor {
        @Override
        public String getDisplayName() {
            return "Accurev Depot";
        }
    }
}

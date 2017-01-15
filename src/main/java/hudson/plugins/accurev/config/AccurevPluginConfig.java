package hudson.plugins.accurev.config;

import jenkins.model.GlobalConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author josp
 */
public class AccurevPluginConfig extends GlobalConfiguration {
    private static final Logger LOGGER = LoggerFactory.getLogger(AccurevPluginConfig.class);
    public static final String ACCUREV_PLUGIN_CONFIGURATION_ID = "accurev-plugin-configuration";


    public static final AccurevPluginConfig EMPTY_CONFIG =
            new AccurevPluginConfig(Collections.<AccurevServerConfig>emptyList());

    private List<AccurevServerConfig> configs = new ArrayList<AccurevServerConfig>();

    public AccurevPluginConfig() {
        load();
    }

    public AccurevPluginConfig(List<AccurevServerConfig> configs) {
        this.configs = configs;
    }

    public List<AccurevServerConfig> getConfigs() {
        return configs;
    }

    public void setConfigs(List<AccurevServerConfig> configs) {
        this.configs = configs;
    }
}
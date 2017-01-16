package hudson.plugins.accurev.config;

import hudson.Extension;
import jenkins.model.GlobalConfiguration;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.StaplerRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author josp
 */
@Extension
public class AccurevPluginConfig extends GlobalConfiguration {
    private static final Logger LOGGER = LoggerFactory.getLogger(AccurevPluginConfig.class);


    public static final AccurevPluginConfig EMPTY_CONFIG =
            new AccurevPluginConfig(Collections.<AccurevServerConfig>emptyList());

    private List<AccurevServerConfig> configs = new ArrayList<>();

    public AccurevPluginConfig() {
        load();
    }

    public AccurevPluginConfig(List<AccurevServerConfig> configs) {
        this.configs = configs;
    }

    @Nonnull
    public List<AccurevServerConfig> getConfigs() {
        return configs;
    }

    public void setConfigs(List<AccurevServerConfig> configs) {
        this.configs = configs;
    }

    @Override
    public boolean configure(StaplerRequest req, JSONObject json) throws FormException {
        try {
            req.bindJSON(this, json);
        } catch (Exception e) {
            LOGGER.debug("Problem while submitting form for AccuRev plugin ({})", e.getMessage(), e);
            LOGGER.trace("AccuRev form data: {}", json.toString());
            throw new FormException(String.format("Malformed AccuRev Plugin configuration (%s)", e.getMessage()), e, "accurev-configuration");
        }
        save();
        return true;
    }
}
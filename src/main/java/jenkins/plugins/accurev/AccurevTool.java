package jenkins.plugins.accurev;

import static hudson.init.InitMilestone.EXTENSIONS_AUGMENTED;

import hudson.EnvVars;
import hudson.Extension;
import hudson.init.Initializer;
import hudson.model.EnvironmentSpecific;
import hudson.model.Node;
import hudson.model.TaskListener;
import hudson.slaves.NodeSpecific;
import hudson.tools.ToolDescriptor;
import hudson.tools.ToolInstallation;
import hudson.tools.ToolProperty;
import hudson.util.FormValidation;
import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.logging.Logger;
import javax.annotation.Nonnull;
import jenkins.model.Jenkins;
import net.sf.json.JSONObject;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;

/** Initialized by josep on 21-01-2017. */
public class AccurevTool extends ToolInstallation
    implements NodeSpecific<AccurevTool>, EnvironmentSpecific<AccurevTool> {

  public static final transient String DEFAULT = "Default";
  private static final long serialVersionUID = 1;
  private static final Logger LOGGER = Logger.getLogger(AccurevTool.class.getName());

  /**
   * Constructor for AccurevTool
   *
   * @param name Tool name
   * @param home Tool location (usually "accurev")
   * @param properties {@link java.util.List} of properties for this tool
   */
  @DataBoundConstructor
  public AccurevTool(String name, String home, List<? extends ToolProperty<?>> properties) {
    super(name, home, properties);
  }

  private static AccurevTool[] getInstallations(DescriptorImpl descriptor) {
    AccurevTool[] installations;
    try {
      installations = descriptor.getInstallations();
    } catch (NullPointerException e) {
      installations = new AccurevTool[0];
    }
    return installations;
  }

  /**
   * Returns the default installation.
   *
   * @return default installation
   */
  public static AccurevTool getDefaultInstallation() {
    Jenkins jenkinsInstance = Jenkins.get();
    DescriptorImpl AccurevTools =
        jenkinsInstance.getDescriptorByType(AccurevTool.DescriptorImpl.class);
    AccurevTool tool = AccurevTools.getInstallation(AccurevTool.DEFAULT);
    if (tool != null) {
      return tool;
    } else {
      AccurevTool[] installations = AccurevTools.getInstallations();
      if (installations.length > 0) {
        return installations[0];
      } else {
        onLoaded();
        return AccurevTools.getInstallations()[0];
      }
    }
  }

  @Initializer(after = EXTENSIONS_AUGMENTED)
  public static void onLoaded() {
    // Creates default tool installation if needed. Uses "accurev" or migrates data from previous
    // versions

    Jenkins jenkinsInstance = Jenkins.get();

    DescriptorImpl descriptor = (DescriptorImpl) jenkinsInstance.getDescriptor(AccurevTool.class);
    AccurevTool[] installations = getInstallations(descriptor);

    if (installations != null && installations.length > 0) {
      // No need to initialize if there's already something
      return;
    }

    String defaultAccurevExe = "accurev";
    AccurevTool tool = new AccurevTool(DEFAULT, defaultAccurevExe, Collections.emptyList());
    if (descriptor != null) {
      descriptor.setInstallations(tool);
      descriptor.save();
    }
  }

  public AccurevTool forNode(@Nonnull Node node, TaskListener log)
      throws IOException, InterruptedException {
    return new AccurevTool(getName(), translateFor(node, log), Collections.emptyList());
  }

  public AccurevTool forEnvironment(EnvVars environment) {
    return new AccurevTool(getName(), environment.expand(getHome()), Collections.emptyList());
  }

  @Override
  public DescriptorImpl getDescriptor() {
    Jenkins jenkinsInstance = Jenkins.get();
    return (DescriptorImpl) jenkinsInstance.getDescriptorOrDie(getClass());
  }

  @Extension
  @Symbol("accurev")
  public static class DescriptorImpl extends ToolDescriptor<AccurevTool> {

    public DescriptorImpl() {
      super();
      load();
    }

    @Override
    @Nonnull
    public String getDisplayName() {
      return "AccuRev";
    }

    @SuppressWarnings("SuspiciousToArrayCall")
    @Override
    public boolean configure(StaplerRequest req, JSONObject json) throws FormException {
      setInstallations(req.bindJSONToList(clazz, json.get("tool")).toArray(new AccurevTool[0]));
      save();
      return true;
    }

    public FormValidation doCheckHome(@QueryParameter File value) {
      Jenkins.get().checkPermission(Jenkins.ADMINISTER);
      String path = value.getPath();

      return FormValidation.validateExecutable(path);
    }

    public AccurevTool getInstallation(String name) {
      for (AccurevTool i : getInstallations()) {
        if (i.getName().equals(name)) {
          return i;
        }
      }
      return null;
    }
  }
}

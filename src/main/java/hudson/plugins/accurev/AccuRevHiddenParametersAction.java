package hudson.plugins.accurev;

import hudson.EnvVars;
import hudson.model.EnvironmentContributingAction;
import hudson.model.InvisibleAction;
import hudson.model.Run;
import javax.annotation.Nonnull;

public class AccuRevHiddenParametersAction extends InvisibleAction
    implements EnvironmentContributingAction {

  private final EnvVars values;

  public AccuRevHiddenParametersAction(EnvVars values) {
    this.values = values;
  }

  @Override
  public void buildEnvironment(@Nonnull Run<?, ?> run, @Nonnull EnvVars env) {
    env.putAll(values);
  }
}

package hudson.plugins.accurev;

import hudson.EnvVars;
import hudson.model.EnvironmentContributingAction;
import hudson.model.InvisibleAction;
import hudson.model.Run;

public class AccuRevHiddenParametersAction extends InvisibleAction
    implements EnvironmentContributingAction {

  private final EnvVars values;

  public AccuRevHiddenParametersAction(EnvVars values) {
    this.values = values;
  }

  @Override
  public void buildEnvironment(
      @edu.umd.cs.findbugs.annotations.NonNull Run<?, ?> run,
      @edu.umd.cs.findbugs.annotations.NonNull EnvVars env) {
    env.putAll(values);
  }
}

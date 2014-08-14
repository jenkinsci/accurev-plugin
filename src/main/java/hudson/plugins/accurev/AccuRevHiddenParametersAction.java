package hudson.plugins.accurev;

import hudson.EnvVars;
import hudson.model.AbstractBuild;
import hudson.model.InvisibleAction;
import hudson.model.EnvironmentContributingAction;

public class AccuRevHiddenParametersAction extends InvisibleAction implements EnvironmentContributingAction {

   private EnvVars values;

   public AccuRevHiddenParametersAction(EnvVars values){
     this.values = values;
   }

   /* from EnvironmentContributingAction */
   public void buildEnvVars(AbstractBuild<?,?> build, EnvVars env) {
     //env.put("MYPLUGIN_NAME", value);
      env.putAll(values);
   }
}

package jenkins.plugins.accurev;

import hudson.util.Secret;

/**
 * Initialized by josep on 07-03-2017.
 */
public interface LoginCommand extends AccurevCommand {
    LoginCommand username(String username);

    LoginCommand password(Secret password);
}

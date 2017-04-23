package jenkins.plugins.accurev;

import hudson.util.Secret;

public interface LoginCommand extends AccurevCommand {
    LoginCommand username(String username);

    LoginCommand password(Secret password);
}

package hudson.plugins.accurev;

import hudson.plugins.accurev.AccurevLauncher.ICmdOutputParser;

import java.io.InputStream;

final class ParseIgnoreOutput implements ICmdOutputParser<Boolean, Void> {
    public Boolean parse(InputStream cmdOutput, Void context) {
        return Boolean.TRUE;
    }
}
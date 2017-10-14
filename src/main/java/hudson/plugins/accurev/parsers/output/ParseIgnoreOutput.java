package hudson.plugins.accurev.parsers.output;

import java.io.InputStream;

import hudson.plugins.accurev.AccurevLauncher.ICmdOutputParser;

public final class ParseIgnoreOutput implements ICmdOutputParser<Boolean, Void> {
    public Boolean parse(InputStream cmdOutput, Void context) {
        return Boolean.TRUE;
    }
}

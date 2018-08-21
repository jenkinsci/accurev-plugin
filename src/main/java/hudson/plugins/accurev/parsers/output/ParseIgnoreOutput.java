package hudson.plugins.accurev.parsers.output;

import hudson.plugins.accurev.AccurevLauncher.ICmdOutputParser;
import java.io.InputStream;

public final class ParseIgnoreOutput implements ICmdOutputParser<Boolean, Void> {

  public Boolean parse(InputStream cmdOutput, Void context) {
    return Boolean.TRUE;
  }
}

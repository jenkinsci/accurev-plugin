package hudson.plugins.accurev.parsers.output;

import hudson.plugins.accurev.AccurevLauncher.ICmdOutputParser;
import hudson.plugins.accurev.AccurevLauncher.UnhandledAccurevCommandOutput;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.Charset;

public final class ParseAccuRevVersion implements ICmdOutputParser<String, Void> {

  @Override
  public String parse(InputStream cmdOutput, Void context)
      throws UnhandledAccurevCommandOutput, IOException {
    final Reader stringReader = new InputStreamReader(cmdOutput, Charset.defaultCharset());
    try (BufferedReader reader = new BufferedReader(stringReader)) {
      String line;
      while ((line = reader.readLine()) != null) {
        if (line.contains("AccuRev")) {
          return line.split("\\s+")[1];
        }
      }
    }
    // TODO Auto-generated method stub
    return null;
  }
}

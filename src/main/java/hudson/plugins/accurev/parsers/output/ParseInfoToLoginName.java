package hudson.plugins.accurev.parsers.output;

import hudson.plugins.accurev.AccurevLauncher.ICmdOutputParser;
import hudson.plugins.accurev.AccurevLauncher.UnhandledAccurevCommandOutput;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.Charset;

public final class ParseInfoToLoginName implements ICmdOutputParser<String, Void> {

  public String parse(InputStream cmdOutput, Void context)
      throws UnhandledAccurevCommandOutput, IOException {
    final String usernameHeading = "Principal:";
    final String controlCharsOrSpaceRegex = "[ \\x00-\\x1F\\x7F]+";
    final Reader stringReader = new InputStreamReader(cmdOutput, Charset.defaultCharset());
    try (BufferedReader reader = new BufferedReader(stringReader)) {
      String line;
      while ((line = reader.readLine()) != null) {
        if (line.contains("not logged in")) {
          return null;
        }
        final String[] parts = line.split(controlCharsOrSpaceRegex);
        for (int i = 0; i < parts.length; i++) {
          final String part = parts[i];
          if (usernameHeading.equals(part)) {
            if ((i + 1) < parts.length) {
              return parts[i + 1]; // returns username
            }
          }
        }
      }
    }
    throw new UnhandledAccurevCommandOutput(
        "Output did not contain "
            + usernameHeading
            + " "
            + controlCharsOrSpaceRegex
            + " <username>");
  }
}

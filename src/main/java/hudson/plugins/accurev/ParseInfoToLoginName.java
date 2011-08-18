package hudson.plugins.accurev;

import hudson.plugins.accurev.AccurevLauncher.ICmdOutputParser;
import hudson.plugins.accurev.AccurevLauncher.UnhandledAccurevCommandOutput;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;

final class ParseInfoToLoginName implements ICmdOutputParser<String, Void> {
    public String parse(InputStream cmdOutput, Void context) throws UnhandledAccurevCommandOutput, IOException {
        final String usernameHeading = "Principal:";
        final String controlCharsOrSpaceRegex = "[ \\x00-\\x1F\\x7F]+";
        final Reader stringReader = new InputStreamReader(cmdOutput);
        final BufferedReader lineReader = new BufferedReader(stringReader);
        String line;
        try {
            line = lineReader.readLine();
            while (line != null) {
                final String[] parts = line.split(controlCharsOrSpaceRegex);
                for (int i = 0; i < parts.length; i++) {
                    final String part = parts[i];
                    if (usernameHeading.equals(part)) {
                        if ((i + 1) < parts.length) {
                            final String username = parts[i + 1];
                            return username;
                        }
                    }
                }
                line = lineReader.readLine();
            }
        } finally {
            lineReader.close();
        }
        throw new UnhandledAccurevCommandOutput("Output did not contain " + usernameHeading + " "
                + controlCharsOrSpaceRegex + " <username>");
    }
}

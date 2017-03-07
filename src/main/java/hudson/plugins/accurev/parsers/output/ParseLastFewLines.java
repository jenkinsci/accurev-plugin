package hudson.plugins.accurev.parsers.output;

import hudson.plugins.accurev.AccurevLauncher.ICmdOutputParser;

import java.io.*;
import java.nio.charset.Charset;
import java.util.LinkedList;
import java.util.List;

/**
 * Filters the output of any command and just returns the last few lines.
 */
public final class ParseLastFewLines implements ICmdOutputParser<List<String>, Integer> {
    public List<String> parse(InputStream cmdOutput, Integer numberOfLines) throws
        IOException {
        final LinkedList<String> result = new LinkedList<>();
        final Reader stringReader = new InputStreamReader(cmdOutput, Charset.defaultCharset());
        int linesRemainingBeforeWeAreFull = numberOfLines;

        try (BufferedReader reader = new BufferedReader(stringReader)) {
            String line;
            while ((line = reader.readLine()) != null) {
                result.add(line);
                if (linesRemainingBeforeWeAreFull > 0) {
                    linesRemainingBeforeWeAreFull--;
                } else {
                    result.removeFirst();
                }
            }
        }
        return result;
    }
}

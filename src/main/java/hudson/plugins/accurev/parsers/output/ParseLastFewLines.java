package hudson.plugins.accurev.parsers.output;

import hudson.plugins.accurev.AccurevLauncher.ICmdOutputParser;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.LinkedList;
import java.util.List;

/**
 * Filters the output of any command and just returns the last few lines.
 */
public final class ParseLastFewLines implements ICmdOutputParser<List<String>, Integer> {
    public List<String> parse(InputStream cmdOutput, Integer numberOfLines) throws 
            IOException {
        final LinkedList<String> result = new LinkedList<String>();
        final Reader stringReader = new InputStreamReader(cmdOutput);
        final BufferedReader lineReader = new BufferedReader(stringReader);
        int linesRemainingBeforeWeAreFull = numberOfLines;
        String line;
        try {
            line = lineReader.readLine();
            while (line != null) {
                result.add(line);
                if (linesRemainingBeforeWeAreFull > 0) {
                    linesRemainingBeforeWeAreFull--;
                } else {
                    result.removeFirst();
                }
                line = lineReader.readLine();
            }
        } finally {
            lineReader.close();
        }
        return result;
    }
}

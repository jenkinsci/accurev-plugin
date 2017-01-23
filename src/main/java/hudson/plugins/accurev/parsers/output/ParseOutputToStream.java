package hudson.plugins.accurev.parsers.output;

import hudson.plugins.accurev.AccurevLauncher;
import hudson.plugins.accurev.AccurevLauncher.UnhandledAccurevCommandOutput;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public final class ParseOutputToStream implements AccurevLauncher.ICmdOutputParser<Boolean, OutputStream> {
    public Boolean parse(InputStream cmdOutput, OutputStream streamToCopyOutputTo)
            throws UnhandledAccurevCommandOutput, IOException {
        final byte[] buffer = new byte[4096];
        int bytesRead = cmdOutput.read(buffer);
        while (bytesRead > 0) {
            streamToCopyOutputTo.write(buffer, 0, bytesRead);
            bytesRead = cmdOutput.read(buffer);
        }
        return Boolean.TRUE;
    }
}

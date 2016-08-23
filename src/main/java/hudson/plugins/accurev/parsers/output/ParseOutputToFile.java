package hudson.plugins.accurev.parsers.output;

import hudson.plugins.accurev.AccurevLauncher.ICmdOutputParser;
import hudson.plugins.accurev.AccurevLauncher.UnhandledAccurevCommandOutput;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

public final class ParseOutputToFile implements ICmdOutputParser<Boolean, File> {
    public Boolean parse(InputStream cmdOutput, File fileToWriteTo) throws UnhandledAccurevCommandOutput, IOException {
        final byte[] buffer = new byte[4096];
        try (FileOutputStream os = new FileOutputStream(fileToWriteTo)) {
            int bytesRead = cmdOutput.read(buffer);
            while (bytesRead > 0) {
                os.write(buffer, 0, bytesRead);
                bytesRead = cmdOutput.read(buffer);
            }
        }
        return Boolean.TRUE;
    }
}

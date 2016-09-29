package hudson.plugins.accurev;

import java.io.*;

/**
 * Simple class to capture the output of something, and then allow that output
 * to be read.
 */
public class ByteArrayStream implements Closeable {
    private final Output mOutputStream = new Output();

    /**
     * Gets the {@link ByteArrayOutputStream} to which data can be written.
     *
     * @return See above.
     */
    ByteArrayOutputStream getOutput() {
        return mOutputStream;
    }

    /**
     * Gets an {@link InputStream} that'll contain all the data that was written
     * to {@link #getOutput()} thus far.
     * <p>
     * Note that it does NOT read any data written after this method completes
     * and it is NOT thread-safe (if data is being written to when this method
     * gets called, behaviour is undefined).
     *
     * @return See above.
     */
    InputStream getInput() {
        return mOutputStream.toInputStream();
    }

    public void close() throws IOException {
        mOutputStream.reset();
        mOutputStream.close();
    }

    private static final class Output extends ByteArrayOutputStream {
        /**
         * Returns an {@link InputStream} of our data without copying the data.
         */
        InputStream toInputStream() {
            return new ByteArrayInputStream(super.buf, 0, super.count);
        }
    }
}

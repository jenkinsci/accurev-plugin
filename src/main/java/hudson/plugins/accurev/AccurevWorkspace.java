package hudson.plugins.accurev;

import java.io.Serializable;

/**
 * Created by IntelliJ IDEA.
 *
 * @author connollys
 * @since 03-Dec-2007 13:17:39
 */
public class AccurevWorkspace implements Serializable {
    private final String depot;
    private final Long streamNumber;
    private final String name;
    private final String host;
    private final String storage;
    private AccurevStream stream = null;

    public AccurevWorkspace(String depot, Long streamNumber, String name, String host, String storage) {
        this.depot = depot;
        this.streamNumber = streamNumber;
        this.name = name;
        this.host = host;
        this.storage = storage;
    }

    /**
     * Setter for property 'stream'.
     *
     * @param stream Value to set for property 'stream'.
     */
    void setStream(AccurevStream stream) {
        this.stream = stream;
    }

    /**
     * Getter for property 'depot'.
     *
     * @return Value for property 'depot'.
     */
    public String getDepot() {
        return depot;
    }

    /**
     * Getter for property 'streamNumber'.
     *
     * @return Value for property 'streamNumber'.
     */
    public Long getStreamNumber() {
        return streamNumber;
    }

    /**
     * Getter for property 'name'.
     *
     * @return Value for property 'name'.
     */
    public String getName() {
        return name;
    }

    /**
     * Getter for property 'host'.
     *
     * @return Value for property 'host'.
     */
    public String getHost() {
        return host;
    }

    /**
     * Getter for property 'storage'.
     *
     * @return Value for property 'storage'.
     */
    public String getStorage() {
        return storage;
    }

    /**
     * Getter for property 'stream'.
     *
     * @return Value for property 'stream'.
     */
    public AccurevStream getStream() {
        return stream;
    }
}

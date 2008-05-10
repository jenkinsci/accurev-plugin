package hudson.plugins.accurev;

import java.io.Serializable;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;

/**
 * Created by IntelliJ IDEA.
 *
 * @author connollys
 * @since 03-Dec-2007 10:58:32
 */
public class AccurevStream implements Serializable {
    private final String name;
    private final Long number;
    private final String depot;
    private final String basisName;
    private final Long basisNumber;
    private final boolean dynamic;
    private final StreamType type;
    private final Date time;
    private final Date startTime;
    private transient AccurevStream parent;
    private final transient Set<AccurevStream> children = new HashSet<AccurevStream>();

    public AccurevStream(String name, Long number, String depot, String basisName, Long basisNumber, boolean dynamic, StreamType type, Date time, Date startTime) {
        this.name = name;
        this.number = number;
        this.depot = depot;
        this.basisName = basisName;
        this.basisNumber = basisNumber;
        this.dynamic = dynamic;
        this.type = type;
        this.time = time;
        this.startTime = startTime;
    }

    /**
     * Setter for property 'parent'.
     *
     * @param parent Value to set for property 'parent'.
     */
    void setParent(AccurevStream parent) {
        if (this.parent != parent) {
            if (this.parent != null) {
                this.parent.getChildren().remove(this);
            }
            this.parent = parent;
            if (this.parent != null) {
                this.parent.getChildren().add(this);
            }
        }
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
     * Getter for property 'number'.
     *
     * @return Value for property 'number'.
     */
    public Long getNumber() {
        return number;
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
     * Getter for property 'basisName'.
     *
     * @return Value for property 'basisName'.
     */
    public String getBasisName() {
        return basisName;
    }

    /**
     * Getter for property 'basisNumber'.
     *
     * @return Value for property 'basisNumber'.
     */
    public Long getBasisNumber() {
        return basisNumber;
    }

    /**
     * Getter for property 'dynamic'.
     *
     * @return Value for property 'dynamic'.
     */
    public boolean isDynamic() {
        return dynamic;
    }

    /**
     * Getter for property 'type'.
     *
     * @return Value for property 'type'.
     */
    public StreamType getType() {
        return type;
    }

    /**
     * Getter for property 'time'.
     *
     * @return Value for property 'time'.
     */
    public Date getTime() {
        return time;
    }

    /**
     * Getter for property 'startTime'.
     *
     * @return Value for property 'startTime'.
     */
    public Date getStartTime() {
        return startTime;
    }

    /**
     * Getter for property 'parent'.
     *
     * @return Value for property 'parent'.
     */
    public AccurevStream getParent() {
        return parent;
    }

    /**
     * Getter for property 'children'.
     *
     * @return Value for property 'children'.
     */
    public Set<AccurevStream> getChildren() {
        return children;
    }

    /**
     * Returns <code>true</code> if and only if the stream propagates changes from it's parent.
     *
     * @return <code>true</code> if and only if the stream propagates changes from it's parent.
     */
    public boolean isReceivingChangesFromParent() {
        switch (type) {
            case WORKSPACE:
                return true;
            case PASSTHROUGH:
                return true;
            case SNAPSHOT:
                return false;
            case NORMAL:
                return time == null;
            default:
                return false;
        }
    }

    public static enum StreamType {
        NORMAL("normal"),
        SNAPSHOT("snapshot"),
        WORKSPACE("workspace"),
        PASSTHROUGH("passthrough"),;
        private final String type;

        StreamType(String type) {
            this.type = type;
        }

        public static StreamType parseStreamType(String streamType) {
            for (StreamType value : values()) {
                if (value.type.equalsIgnoreCase(streamType)) {
                    return value;
                }
            }
            throw new NumberFormatException("Unknown stream type: " + streamType);
        }
    }
}

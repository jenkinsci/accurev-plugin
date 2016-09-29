package hudson.plugins.accurev;

import org.apache.commons.lang.builder.EqualsBuilder;
import org.apache.commons.lang.builder.HashCodeBuilder;

import java.io.Serializable;

public class PopulateStreams implements Serializable, Comparable<PopulateStreams> {
    private final String name;
    private final String number;


    public PopulateStreams(String name, String number) {
        this.name = name;
        this.number = number;

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
    public String getNumber() {
        return number;
    }

    @Override
    public int compareTo(PopulateStreams o) {
        return name.compareTo(o.name);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;

        if (o == null || getClass() != o.getClass()) return false;

        PopulateStreams that = (PopulateStreams) o;

        return new EqualsBuilder()
                .append(name, that.name)
                .append(number, that.number)
                .isEquals();
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder(17, 37)
                .append(name)
                .append(number)
                .toHashCode();
    }
}

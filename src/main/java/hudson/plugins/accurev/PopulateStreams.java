package hudson.plugins.accurev;

import java.io.Serializable;

public class PopulateStreams implements Serializable,Comparable<PopulateStreams> {
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

	public int compareTo(PopulateStreams o) {
		// TODO Auto-generated method stub
		return name.compareTo(o.name);
	}
 
}

package hudson.plugins.accurev;

/**
 * Initialized by josep on 09-03-2017.
 */
public class AccurevDepot {
    private final String name;
    private final int number;

    public AccurevDepot(String name, int number) {
        this.name = name;
        this.number = number;
    }

    public String getName() {
        return name;
    }

    public int getNumber() {
        return number;
    }
}

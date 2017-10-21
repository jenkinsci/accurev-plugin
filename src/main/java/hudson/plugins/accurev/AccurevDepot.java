package hudson.plugins.accurev;

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

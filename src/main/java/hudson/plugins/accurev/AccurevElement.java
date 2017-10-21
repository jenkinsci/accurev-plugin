package hudson.plugins.accurev;

public class AccurevElement {
    private String location;
    private String status;

    public AccurevElement(String location, String status) {
        this.location = location;
        this.status = status;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}

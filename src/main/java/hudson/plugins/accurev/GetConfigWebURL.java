package hudson.plugins.accurev;

import java.io.Serializable;

@Deprecated
public class GetConfigWebURL implements Serializable {
    private final String webURL;

    public GetConfigWebURL(String webURL) {
        this.webURL = webURL;
    }

    /**
     * Getter for property 'webURL'.
     *
     * @return Value for property 'webURL'.
     */
    public String getWebURL() {
        return webURL;
    }

}

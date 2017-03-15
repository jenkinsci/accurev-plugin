package jenkins.plugins.accurev;

/**
 * Records exception information related to accurev operations. This exception is
 * used to encapsulate command line accurev errors and other errors
 * related to accurev operations.
 */
public class AccurevException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    /**
     * Constructor for AccurevException.
     */
    public AccurevException() {
        super();
    }

    /**
     * Constructor for AccurevException.
     *
     * @param message {@link java.lang.String} description to associate with this exception
     */
    public AccurevException(String message) {
        super(message);
    }

    /**
     * Constructor for AccurevException.
     *
     * @param cause {@link java.lang.Throwable} which caused this exception
     */
    public AccurevException(Throwable cause) {
        super(cause);
    }

    /**
     * Constructor for AccurevException.
     *
     * @param message {@link java.lang.String} description to associate with this exception
     * @param cause   {@link java.lang.Throwable} which caused this exception
     */
    public AccurevException(String message, Throwable cause) {
        super(message, cause);
    }
}

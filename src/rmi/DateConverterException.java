package rmi;

import java.io.Serializable;

/**
 * Checked exception for date validation errors that need to cross the RMI wire.
 * Using a plain RuntimeException (IllegalArgumentException) as a remote method's
 * declared exception is unreliable — it may not be serialised correctly by all
 * RMI implementations.  This class is Serializable and declared in the remote
 * interface so it travels cleanly from server to client.
 */
public class DateConverterException extends Exception implements Serializable {

    private static final long serialVersionUID = 1L;

    public DateConverterException(String message) {
        super(message);
    }

    public DateConverterException(String message, Throwable cause) {
        super(message, cause);
    }
}

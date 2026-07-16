package com.relationdetector.core.scan;

/** Signals that opening the configured JDBC connection failed before scanning could begin. */
public final class DatabaseConnectionException extends RuntimeException {
    public DatabaseConnectionException(Throwable cause) {
        super("Unable to connect to the configured database", cause);
    }
}

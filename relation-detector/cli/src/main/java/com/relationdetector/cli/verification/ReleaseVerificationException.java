package com.relationdetector.cli.verification;

final class ReleaseVerificationException extends IllegalArgumentException {
    ReleaseVerificationException(String message) {
        super(message);
    }

    ReleaseVerificationException(String message, Throwable cause) {
        super(message, cause);
    }
}

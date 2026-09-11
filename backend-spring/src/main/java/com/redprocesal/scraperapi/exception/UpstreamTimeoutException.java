package com.redprocesal.scraperapi.exception;

public class UpstreamTimeoutException extends ExternalServiceException {
    public UpstreamTimeoutException(String message) {
        super(message);
    }

    public UpstreamTimeoutException(String message, Throwable cause) {
        super(message, cause);
    }
}

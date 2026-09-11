package com.redprocesal.scraperapi.exception;

public class ServiceOverloadedException extends ExternalServiceException {
    public ServiceOverloadedException(String message) {
        super(message);
    }

    public ServiceOverloadedException(String message, Throwable cause) {
        super(message, cause);
    }
}

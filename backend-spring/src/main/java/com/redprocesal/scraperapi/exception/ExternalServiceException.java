package com.redprocesal.scraperapi.exception;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ExternalServiceException extends RuntimeException {
    public ExternalServiceException(String message) {
        super(message);
    }

    public ExternalServiceException(String message, Throwable cause) {
        super(message, cause);
    }
}

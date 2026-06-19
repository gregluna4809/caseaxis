package com.caseaxis.security;

public class LoginRateLimitExceededException extends RuntimeException {

    public LoginRateLimitExceededException(String message) {
        super(message);
    }
}

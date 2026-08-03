package com.example.laive.platform.core;

/** Refresh Token 등 저장 credential을 공급자가 거부했음을 나타낸다. */
public class PlatformCredentialRejectedException extends RuntimeException {

    public PlatformCredentialRejectedException(String message, Throwable cause) {
        super(message, cause);
    }
}

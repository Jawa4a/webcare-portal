package com.webcare.portal.website;

public class UrlNotAllowedException extends RuntimeException {

    private final String code;

    public UrlNotAllowedException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}

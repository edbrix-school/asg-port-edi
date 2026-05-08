package com.asg.portediintegration.exceptions;

import lombok.Getter;

@Getter
public class CustomException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final int code;

    public int getCode() {
        return code;
    }

    public CustomException(String message) {
        super(message);
        this.code = 500;
    }

    public CustomException(String message, int code) {
        super(message);
        this.code = code;
    }

    public CustomException(String message, Throwable cause) {
        super(message, cause);
        this.code = 500;
    }

    public CustomException(String message, int code, Throwable cause) {
        super(message, cause);
        this.code = code;
    }
}

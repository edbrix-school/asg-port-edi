package com.asg.portediintegration.exceptions;

import com.asg.portediintegration.common.ValidationError;
import lombok.Getter;

import java.util.Collections;
import java.util.List;

/**
 * Exception thrown when validation errors occur
 */
@Getter
public class ValidationException extends RuntimeException {

    private final List<ValidationError> fieldErrors;

    public ValidationException(String message, List<ValidationError> fieldErrors) {
        super(message);
        this.fieldErrors = fieldErrors;
    }

    public ValidationException(String message, List<ValidationError> fieldErrors, Throwable cause) {
        super(message, cause);
        this.fieldErrors = fieldErrors;
    }

    public ValidationException(String message) {
        super(message);
        this.fieldErrors = Collections.emptyList();
    }
}


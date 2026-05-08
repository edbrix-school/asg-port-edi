package com.asg.portediintegration.common;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class ValidationError {
    private Integer recordIndex; // For array validation errors
    private String field;
    private String message;
}

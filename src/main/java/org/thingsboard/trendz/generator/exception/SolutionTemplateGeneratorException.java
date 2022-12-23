package org.thingsboard.trendz.generator.exception;

import lombok.EqualsAndHashCode;
import lombok.Getter;

@Getter
@EqualsAndHashCode(callSuper = true)
public abstract class SolutionTemplateGeneratorException extends RuntimeException {

    protected static final String DEFAULT_MESSAGE = "The exception occurred in the internal logic";


    public SolutionTemplateGeneratorException() {
        super(DEFAULT_MESSAGE);
    }

    public SolutionTemplateGeneratorException(String message) {
        super(message);
    }

    public SolutionTemplateGeneratorException(String message, Throwable cause) {
        super(message, cause);
    }
}
package org.thingsboard.trendz.generator.exception;

import lombok.EqualsAndHashCode;
import lombok.Getter;

@Getter
@EqualsAndHashCode(callSuper = true)
public class SolutionValidationException extends SolutionTemplateGeneratorException {

    private final String solutionName;

    public SolutionValidationException(String solutionName, Exception e) {
        super("Validation of solution template is failed, solution = " + solutionName, e);
        this.solutionName = solutionName;
    }
}

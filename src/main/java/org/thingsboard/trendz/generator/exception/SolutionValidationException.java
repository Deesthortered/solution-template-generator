package org.thingsboard.trendz.generator.exception;

public class SolutionValidationException extends SolutionTemplateGeneratorException {

    public SolutionValidationException(String solutionName, Exception e) {
        super("Validation of solution template is failed, solution = " + solutionName, e);
    }
}

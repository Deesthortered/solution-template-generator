package org.thingsboard.trendz.generator.solution;

import org.thingsboard.trendz.generator.exception.SolutionValidationException;

import java.time.ZonedDateTime;

public interface SolutionTemplateGenerator {

    String getSolutionName();

    void validate() throws SolutionValidationException;

    void generate(boolean skipTelemetry, ZonedDateTime startYear);

    void remove();
}

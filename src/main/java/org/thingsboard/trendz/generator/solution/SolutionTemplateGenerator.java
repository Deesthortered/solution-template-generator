package org.thingsboard.trendz.generator.solution;

import org.thingsboard.trendz.generator.exception.SolutionValidationException;

import java.time.ZonedDateTime;

public interface SolutionTemplateGenerator {

    String getSolutionName();

    void validate() throws SolutionValidationException;

    void generate(boolean skipTelemetry, ZonedDateTime startYear, boolean strictGeneration, boolean fullTelemetryGeneration,
                  long startGenerationTime, long endGenerationTime);

    void remove();
}

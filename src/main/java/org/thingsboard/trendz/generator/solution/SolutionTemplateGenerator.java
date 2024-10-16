package org.thingsboard.trendz.generator.solution;

import org.thingsboard.trendz.generator.exception.SolutionValidationException;

public interface SolutionTemplateGenerator {

    String getSolutionName();

    void validate() throws SolutionValidationException;

    void generate(
            boolean skipTelemetry, boolean strictGeneration, boolean fullTelemetryGeneration,
            long startGenerationTime, long endGenerationTime
    );

    void remove();
}

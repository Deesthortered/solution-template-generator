package org.thingsboard.trendz.generator.solution;

import java.time.ZonedDateTime;

public interface SolutionTemplateGenerator {

    String getSolutionName();

    void validate();

    void generate(boolean skipTelemetry, ZonedDateTime startYear);

    void remove();
}

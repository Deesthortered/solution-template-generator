package org.thingsboard.trendz.generator.solution;

public interface SolutionTemplateGenerator {

    String getSolutionName();

    void validate();
    void generate();

    void remove();
}

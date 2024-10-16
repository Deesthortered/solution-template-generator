package org.thingsboard.trendz.generator;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.thingsboard.trendz.generator.exception.SolutionValidationException;
import org.thingsboard.trendz.generator.solution.SolutionTemplateGenerator;
import org.thingsboard.trendz.generator.utils.DateTimeUtils;
import org.thingsboard.trendz.generator.utils.RandomUtils;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

@Slf4j
@SpringBootApplication
public class SolutionTemplateGeneratorApplication implements CommandLineRunner {

    private final static String MODE_GENERATE = "generate";
    private final static String MODE_REMOVE = "remove";

    private final String mode;
    private final List<String> currentSolutions;
    private final boolean skipTelemetry;
    private final ZonedDateTime startYear;
    private final Long startGenerationTimeMs;
    private final Long endGenerationTimeMs;
    private final boolean strictGeneration;
    private final boolean fullTelemetryGeneration;
    private final SolutionTemplateDispatcher solutionTemplateDispatcher;

    public SolutionTemplateGeneratorApplication(
            SolutionTemplateDispatcher solutionTemplateDispatcher,
            @Value("${generator.mode}") String mode,
            @Value("${generator.solutions}") List<String> currentSolutions,
            @Value("${generator.skipTelemetry}") boolean skipTelemetry,
            @Value("${generator.startGenerationTime}") Long startGenerationTime,
            @Value("${generator.endGenerationTime}") Long endGenerationTime,
            @Value("${generator.strictGeneration}") boolean strictGeneration,
            @Value("${generator.fullTelemetryGeneration}") boolean fullTelemetryGeneration
    ) {
        this.solutionTemplateDispatcher = solutionTemplateDispatcher;
        this.mode = mode;
        this.currentSolutions = currentSolutions;
        this.skipTelemetry = skipTelemetry;
        this.startYear = DateTimeUtils.fromTs(startGenerationTime);
        this.startGenerationTimeMs = startGenerationTime;
        this.endGenerationTimeMs = endGenerationTime;
        this.strictGeneration = strictGeneration;
        this.fullTelemetryGeneration = fullTelemetryGeneration;
    }

    @Override
    public void run(String... args) {
        setDefaultTimezone();
        boolean modeGenerate = MODE_GENERATE.equals(this.mode);
        boolean modeRemove = MODE_REMOVE.equals(this.mode);

        log.info("There is/are {} solutions found {}", this.currentSolutions.size(), this.currentSolutions);
        for (String solutionName : this.currentSolutions) {
            log.info("Starting current generator: {}", solutionName);
            SolutionTemplateGenerator solutionGenerator = this.solutionTemplateDispatcher.getSolutionGenerator(solutionName);
            if (solutionGenerator == null) {
                log.error("Solution with name {} does not exist, skipping...", solutionName);
            } else {
                if (modeGenerate) {
                    RandomUtils.refreshRandom();
                    try {
                        if (this.strictGeneration) {
                            solutionGenerator.validate();
                        }
                        solutionGenerator.generate(this.skipTelemetry, this.startYear, this.strictGeneration, fullTelemetryGeneration, startGenerationTimeMs, endGenerationTimeMs);
                        log.info("Current generator is finished: {}", solutionName);
                    } catch (SolutionValidationException e) {
                        log.error("Validation solution error: " + solutionGenerator.getSolutionName(), e.getCause());
                    }
                } else if (modeRemove) {
                    solutionGenerator.remove();
                } else {
                    throw new IllegalArgumentException("Unsupported mode: " + this.mode);
                }
            }
        }
    }


    private static void setDefaultTimezone() {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
        Locale.setDefault(Locale.US);
    }

    public static void main(String[] args) {
        SpringApplication.run(SolutionTemplateGeneratorApplication.class, args);
    }
}

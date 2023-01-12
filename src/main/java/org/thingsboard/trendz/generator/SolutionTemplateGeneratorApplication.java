package org.thingsboard.trendz.generator;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.thingsboard.trendz.generator.solution.SolutionTemplateGenerator;

import java.util.List;
import java.util.TimeZone;

@Slf4j
@SpringBootApplication
public class SolutionTemplateGeneratorApplication implements CommandLineRunner {

	private final static String MODE_GENERATE = "generate";
	private final static String MODE_REMOVE = "remove";

	private final String mode;
	private final List<String> currentSolutions;

	@Autowired
	private SolutionTemplateDispatcher solutionTemplateDispatcher;


	public SolutionTemplateGeneratorApplication(
			@Value("${generator.mode}") String mode,
			@Value("${generator.solutions}") List<String> currentSolutions
	) {
		this.mode = mode;
		this.currentSolutions = currentSolutions;
	}

	@Override
	public void run(String... args) {
		setDefaultTimezone();
		boolean modeRemove = MODE_REMOVE.equals(this.mode);

		log.info("There is/are {} solutions found {}", this.currentSolutions.size(), this.currentSolutions);
		for (String solutionName : this.currentSolutions) {
			log.info("Starting current generator: {}", solutionName);
			SolutionTemplateGenerator solutionGenerator = this.solutionTemplateDispatcher.getSolutionGenerator(solutionName);
			if (solutionGenerator == null) {
				log.error("Solution with name {} does not exist, skipping...", solutionName);
			} else {
				if (modeRemove) {
					solutionGenerator.remove();
				} else {
					solutionGenerator.validate();
					solutionGenerator.generate();
				}
			}
			log.info("Current generator is finished: {}", solutionName);
		}
		System.exit(0);
	}

	private static void setDefaultTimezone() {
		TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
	}

	public static void main(String[] args) {
		SpringApplication.run(SolutionTemplateGeneratorApplication.class, args);
	}
}

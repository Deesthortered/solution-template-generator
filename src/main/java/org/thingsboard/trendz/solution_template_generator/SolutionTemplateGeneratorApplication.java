package org.thingsboard.trendz.solution_template_generator;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.thingsboard.trendz.solution_template_generator.solution.SolutionTemplateGenerator;
import org.thingsboard.trendz.solution_template_generator.tool.SolutionTemplateDispatcher;

import java.util.List;
import java.util.Map;

@Slf4j
@SpringBootApplication
public class SolutionTemplateGeneratorApplication implements CommandLineRunner {

	@Value("${generator.solutions}")
	private List<String> currentSolutions;

	@Autowired
	private SolutionTemplateDispatcher solutionTemplateDispatcher;

	public static void main(String[] args) {
		SpringApplication.run(SolutionTemplateGeneratorApplication.class, args);
	}


	@Override
	public void run(String... args) throws Exception {
		log.info("There is/are {} solutions found {}", this.currentSolutions.size(), this.currentSolutions);
		for (String solutionName : this.currentSolutions) {
			log.info("Starting current generator: {}", solutionName);
			SolutionTemplateGenerator solutionGenerator = this.solutionTemplateDispatcher.getSolutionGenerator(solutionName);
			if (solutionGenerator == null) {
				log.error("Solution with name {} does not exist, skipping...", solutionName);
			} else {
				solutionGenerator.generate();
			}
			log.info("Current generator is finished: {}", solutionName);
		}
		System.exit(0);
	}
}

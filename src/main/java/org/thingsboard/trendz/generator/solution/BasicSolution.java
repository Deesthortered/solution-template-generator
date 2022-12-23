package org.thingsboard.trendz.generator.solution;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class BasicSolution implements SolutionTemplateGenerator {

    @Override
    public String getSolutionName() {
        return "Basic";
    }

    @Override
    public void generate() {
        log.info("WaterMeteringSolution - generate");

    }

    @Override
    public void remove() {
        log.info("WaterMeteringSolution - remove");
    }
}

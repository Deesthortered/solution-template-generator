package org.thingsboard.trendz.solution_template_generator.solution;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class WaterMeteringSolution implements SolutionTemplateGenerator {

    @Override
    public String getSolutionName() {
        return "WaterMetering";
    }

    @Override
    public void generate() {
        log.info("WaterMeteringSolution - generate");
    }
}

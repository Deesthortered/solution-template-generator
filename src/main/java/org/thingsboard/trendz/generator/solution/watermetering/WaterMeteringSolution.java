package org.thingsboard.trendz.generator.solution.watermetering;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.thingsboard.trendz.generator.solution.SolutionTemplateGenerator;

@Slf4j
@Service
public class WaterMeteringSolution implements SolutionTemplateGenerator {

    @Override
    public String getSolutionName() {
        return "WaterMetering";
    }

    @Override
    public void validate() {
        log.info("WaterMeteringSolution - validate");
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

package org.thingsboard.trendz.generator.solution;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class EnergyMeteringSolution implements SolutionTemplateGenerator {

    @Override
    public String getSolutionName() {
        return "EnergyMetering";
    }

    @Override
    public void generate() {
        log.info("EnergyMeteringSolution - generate");
    }

    @Override
    public void remove() {
        log.info("EnergyMeteringSolution - remove");
    }
}

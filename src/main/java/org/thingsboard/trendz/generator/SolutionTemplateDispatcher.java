package org.thingsboard.trendz.generator;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.thingsboard.trendz.generator.solution.SolutionTemplateGenerator;

import javax.annotation.PostConstruct;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class SolutionTemplateDispatcher {

    private final Map<String, SolutionTemplateGenerator> beanNameToGeneratorMap;
    private final Map<String, SolutionTemplateGenerator> generatorMap = new HashMap<>();

    @PostConstruct
    public void init() {
        for (String beanName : beanNameToGeneratorMap.keySet()) {
            SolutionTemplateGenerator bean = this.beanNameToGeneratorMap.get(beanName);
            this.generatorMap.put(bean.getSolutionName(), bean);
        }
    }

    public SolutionTemplateGenerator getSolutionGenerator(String solutionTemplateName) {
        return this.generatorMap.get(solutionTemplateName);
    }
}

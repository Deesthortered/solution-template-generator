package org.thingsboard.trendz.generator.exception;

import org.thingsboard.server.common.data.rule.RuleChain;

public class RuleChainAlreadyExistException extends SolutionTemplateGeneratorException {

    private final RuleChain ruleChain;

    public RuleChainAlreadyExistException(RuleChain ruleChain) {
        this.ruleChain = ruleChain;
    }
}

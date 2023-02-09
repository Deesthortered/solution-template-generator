package org.thingsboard.trendz.generator.exception;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import org.thingsboard.server.common.data.rule.RuleChain;

@Getter
@EqualsAndHashCode(callSuper = true)
public class RuleChainAlreadyExistException extends SolutionTemplateGeneratorException {

    private final RuleChain ruleChain;

    public RuleChainAlreadyExistException(RuleChain ruleChain) {
        super("Rule chain is already exists: " + ruleChain.getName());
        this.ruleChain = ruleChain;
    }
}

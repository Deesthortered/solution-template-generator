package org.thingsboard.trendz.generator.solution;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.thingsboard.server.common.data.Customer;
import org.thingsboard.server.common.data.rule.RuleChain;
import org.thingsboard.trendz.generator.exception.CustomerAlreadyExistException;
import org.thingsboard.trendz.generator.exception.RuleChainAlreadyExistException;
import org.thingsboard.trendz.generator.service.TbRestClient;

@Slf4j
@Service
public class BasicSolution implements SolutionTemplateGenerator {

    private static final String CUSTOMER_TITLE = "Basic Customer";
    private static final String RULE_CHAIN_NAME = "Basic Rule Chain";
    private final TbRestClient tbRestClient;

    @Autowired
    public BasicSolution(
            TbRestClient tbRestClient
    ) {
        this.tbRestClient = tbRestClient;
    }

    @Override
    public String getSolutionName() {
        return "Basic";
    }

    @Override
    public void generate() {
        log.info("Basic Solution - start generation");
        try {
            tbRestClient.getCustomerByTitle(CUSTOMER_TITLE)
                    .ifPresent(customer -> {
                        throw new CustomerAlreadyExistException(customer);
                    });

            tbRestClient.getAllRuleChains()
                    .stream()
                    .filter(ruleChain -> ruleChain.getName().equals(RULE_CHAIN_NAME))
                    .findAny()
                    .ifPresent(ruleChain -> {
                        throw new RuleChainAlreadyExistException(ruleChain);
                    });

            Customer customer = tbRestClient.createCustomer(CUSTOMER_TITLE);
            RuleChain ruleChain = tbRestClient.createRuleChain(RULE_CHAIN_NAME);

            log.info("Basic Solution - generation is completed!");
        } catch (Exception e) {
            log.error("Basic Solution generate was failed, skipping...", e);
        }
    }

    @Override
    public void remove() {
        log.info("Basic Solution - start removal");
        try {
            tbRestClient.getCustomerByTitle(CUSTOMER_TITLE)
                    .ifPresent(customer -> tbRestClient.deleteCustomer(customer.getUuidId()));

            tbRestClient.getAllRuleChains()
                    .stream()
                    .filter(ruleChain -> ruleChain.getName().equals(RULE_CHAIN_NAME))
                    .findAny()
                    .ifPresent(ruleChain -> {
                        tbRestClient.deleteRuleChain(ruleChain.getUuidId());
                    });

            log.info("Basic Solution - removal is completed!");
        } catch (Exception e) {
            log.error("WaterMeteringSolution removal was failed, skipping...", e);
        }
    }
}

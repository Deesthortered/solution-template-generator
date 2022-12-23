package org.thingsboard.trendz.generator.solution;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.thingsboard.server.common.data.Customer;
import org.thingsboard.trendz.generator.service.TbRestClient;


@Slf4j
@Service
public class BasicSolution implements SolutionTemplateGenerator {

    private static final String CUSTOMER_TITLE = "Basic Customer";
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
        log.info("WaterMeteringSolution - generate");
        try {
            Customer customer = tbRestClient.createCustomer(CUSTOMER_TITLE);

        } catch (Exception e) {
            log.error("WaterMeteringSolution generate was failed, skipping...");
        }
    }

    @Override
    public void remove() {
        log.info("WaterMeteringSolution - remove");
        try {
            Customer customer = tbRestClient.getCustomerByTitle(CUSTOMER_TITLE)
                    .orElseThrow();
            tbRestClient.deleteCustomer(customer.getUuidId());
        } catch (Exception e) {
            log.error("WaterMeteringSolution removal was failed, skipping...");
        }
    }
}

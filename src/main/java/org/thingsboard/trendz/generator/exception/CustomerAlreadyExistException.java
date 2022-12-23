package org.thingsboard.trendz.generator.exception;

import org.thingsboard.server.common.data.Customer;

public class CustomerAlreadyExistException extends SolutionTemplateGeneratorException {

    private final Customer customer;

    public CustomerAlreadyExistException(Customer customer) {
        this.customer = customer;
    }
}

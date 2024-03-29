package org.thingsboard.trendz.generator.exception;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import org.thingsboard.server.common.data.Customer;

@Getter
@EqualsAndHashCode(callSuper = true)
public class CustomerAlreadyExistException extends SolutionTemplateGeneratorException {

    private final Customer customer;

    public CustomerAlreadyExistException(Customer customer) {
        super("Customer is already exists: " + customer.getName());
        this.customer = customer;
    }
}

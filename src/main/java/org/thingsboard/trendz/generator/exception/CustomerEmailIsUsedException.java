package org.thingsboard.trendz.generator.exception;

import lombok.EqualsAndHashCode;
import lombok.Getter;

@Getter
@EqualsAndHashCode(callSuper = true)
public class CustomerEmailIsUsedException extends SolutionTemplateGeneratorException {

    private final String email;

    public CustomerEmailIsUsedException(String email) {
        super("Customer is email is already used in system (probably another tenant): " + email);
        this.email = email;
    }
}

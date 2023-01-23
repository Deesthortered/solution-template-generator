package org.thingsboard.trendz.generator.model.tb;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.thingsboard.server.common.data.Customer;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CustomerData {

    private Customer customer;
    private CustomerUser user;
}

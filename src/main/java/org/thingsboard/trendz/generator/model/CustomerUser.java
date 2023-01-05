package org.thingsboard.trendz.generator.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.thingsboard.server.common.data.id.CustomerId;
import org.thingsboard.server.common.data.id.TenantId;
import org.thingsboard.server.common.data.id.UserId;
import org.thingsboard.server.common.data.security.Authority;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CustomerUser {

    private UserId id;
    private TenantId tenantId;
    private CustomerId customerId;
    private Authority authority;
    private String name;
    private String email;
    private String firstName;
    private String lastName;
    private long createdTime;
    private CustomerUserAdditionalInfo additionalInfo;
}

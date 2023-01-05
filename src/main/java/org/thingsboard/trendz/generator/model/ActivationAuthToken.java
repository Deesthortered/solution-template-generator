package org.thingsboard.trendz.generator.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ActivationAuthToken {

    private String token;
    private String refreshToken;
    private String scope;
}

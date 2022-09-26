package org.thingsboard.trendz.solution_template_generator.rest;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AuthToken {

    private String token;
    private String refreshToken;
}

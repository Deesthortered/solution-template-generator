package org.thingsboard.trendz.generator.service.jwt;

import com.auth0.jwt.JWT;
import com.auth0.jwt.interfaces.Claim;
import com.auth0.jwt.interfaces.DecodedJWT;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.thingsboard.trendz.generator.model.rest.AuthToken;

import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
public class TokenExtractor {

    private final String KEY_TENANT_ID = "tenantId";


    public UUID getTenantId(AuthToken authToken) {
        DecodedJWT jwt = JWT.decode(authToken.getToken());
        Map<String, Claim> claims = jwt.getClaims();
        Claim tenantIdClaim = claims.get(KEY_TENANT_ID);
        return UUID.fromString(tenantIdClaim.asString());
    }
}

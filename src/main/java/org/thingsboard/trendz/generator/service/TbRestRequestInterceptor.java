package org.thingsboard.trendz.generator.service;

import com.auth0.jwt.JWT;
import com.auth0.jwt.interfaces.DecodedJWT;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.client.support.HttpRequestWrapper;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.thingsboard.trendz.generator.model.AuthToken;
import org.thingsboard.trendz.generator.model.LoginRequest;

import java.io.IOException;
import java.util.Collections;
import java.util.Date;


@Slf4j
@Service
public class TbRestRequestInterceptor implements ClientHttpRequestInterceptor {

    private static final String JWT_TOKEN_HEADER_PARAM = "X-Authorization";
    private final String tbApiUser;
    private final String tbApiPass;
    private final TbRestClient tbRestClient;

    private AuthToken authToken;


    public TbRestRequestInterceptor(
            @Value("${tb.api.username}") String tbApiUser,
            @Value("${tb.api.password}") String tbApiPass,
            @Autowired TbRestClient tbRestClient,
            @Autowired RestTemplate restTemplate
    ) {
        this.tbApiUser = tbApiUser;
        this.tbApiPass = tbApiPass;
        this.tbRestClient = tbRestClient;
        restTemplate.setInterceptors(Collections.singletonList(this));
    }

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] bytes, ClientHttpRequestExecution execution) throws IOException {
        if (TbRestClient.LOGIN_PATH.equals(request.getURI().getPath())) {
            return execution.execute(request, bytes);
        }
        String token = "Bearer " + gainToken();

        HttpRequest wrapper = new HttpRequestWrapper(request);
        wrapper.getHeaders().set(JWT_TOKEN_HEADER_PARAM, token);
        return execution.execute(wrapper, bytes);
    }


    private String gainToken() {
        if (this.authToken == null) {
            this.authToken = tbRestClient.login(new LoginRequest(this.tbApiUser, this.tbApiPass));
        } else if (isTokenExpired(this.authToken)) {
            this.authToken = tbRestClient.refreshToken(this.authToken.getRefreshToken());
        }
        return this.authToken.getToken();
    }

    private boolean isTokenExpired(AuthToken authToken) {
        DecodedJWT jwt = JWT.decode(authToken.getToken());
        return jwt.getExpiresAt().before(new Date());
    }
}

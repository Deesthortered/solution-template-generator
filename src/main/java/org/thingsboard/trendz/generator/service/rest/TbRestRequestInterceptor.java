package org.thingsboard.trendz.generator.service.rest;

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
import org.springframework.util.StreamUtils;
import org.springframework.web.client.RestTemplate;
import org.thingsboard.trendz.generator.model.rest.AuthToken;
import org.thingsboard.trendz.generator.model.rest.LoginRequest;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
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
    public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution) throws IOException {
        if (TbRestClient.LOGIN_PATH.equals(request.getURI().getPath())) {
            return execution.execute(request, body);
        }
        var token = "Bearer " + gainToken();

        HttpRequest wrapper = new HttpRequestWrapper(request);
        wrapper.getHeaders().set(JWT_TOKEN_HEADER_PARAM, token);

        logRequestDetails(request, body);
        var response = execution.execute(wrapper, body);
        logResponseDetails(response);
        return response;
    }


    private String gainToken() {
        if (this.authToken == null) {
            this.authToken = tbRestClient.login(new LoginRequest(this.tbApiUser, this.tbApiPass));
        } else if (isTokenExpired(this.authToken)) {
            this.authToken = tbRestClient.login(new LoginRequest(this.tbApiUser, this.tbApiPass));
        }
        return this.authToken.getToken();
    }

    private boolean isTokenExpired(AuthToken authToken) {
        DecodedJWT jwt = JWT.decode(authToken.getToken());
        return jwt.getExpiresAt().before(new Date());
    }

    private void logRequestDetails(HttpRequest request, byte[] body) {
        log.debug("Request URI     : {}", request.getURI());
        log.debug("Request Method  : {}", request.getMethod());
        log.debug("Request Headers : {}", request.getHeaders());
        log.debug("Request body    : {}", new String(body, StandardCharsets.UTF_8));
    }

    private void logResponseDetails(ClientHttpResponse response) throws IOException {
        log.debug("Response Status Code  : {}", response.getStatusCode());
        log.debug("Response Headers      : {}", response.getHeaders());
        log.debug("Response Body         : {}", StreamUtils.copyToString(response.getBody(), StandardCharsets.UTF_8));
    }
}

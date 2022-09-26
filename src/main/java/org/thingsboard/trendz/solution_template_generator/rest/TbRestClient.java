package org.thingsboard.trendz.solution_template_generator.rest;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.thingsboard.server.common.data.Customer;
import org.thingsboard.server.common.data.Device;
import org.thingsboard.server.common.data.asset.Asset;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
public class TbRestClient {

    private final String baseURL;
    private final RestTemplate restTemplate;

    public TbRestClient(
            @Value("${tb.api.host}") String tbApiHost,
            @Autowired RestTemplate restTemplate
    ) {
        this.baseURL = tbApiHost;
        this.restTemplate = restTemplate;
    }


    public AuthToken login(LoginRequest request) {
        ResponseEntity<AuthToken> response = restTemplate.postForEntity(baseURL + "/api/auth/login", request, AuthToken.class);
        AuthToken authToken = response.getBody();
        if (authToken == null) {
            throw new IllegalStateException("Login request is failed!");
        }
        return authToken;
    }

    public AuthToken refreshToken(String refreshToken) {
        Map<String, String> request = new HashMap<>();
        request.put("refreshToken", refreshToken);

        ResponseEntity<AuthToken> response = restTemplate.postForEntity("/api/auth/token", request, AuthToken.class);
        AuthToken authToken = response.getBody();
        if (authToken == null) {
            throw new IllegalStateException("Refresh token is failed!");
        }
        return authToken;
    }


    public Customer getCustomerByTitle(String title) {
        return restTemplate.getForEntity(baseURL + "/api/tenant/customers?customerTitle=" + title, Customer.class).getBody();
    }

    public Asset getAssetByTitle(String name) {
        return restTemplate.getForEntity(baseURL + "/api/tenant/assets?assetName=" + name, Asset.class).getBody();
    }

    public Device getDeviceByTitle(String name) {
        return restTemplate.getForEntity(baseURL + "/api/tenant/devices?deviceName=" + name, Device.class).getBody();
    }


    public Customer createCustomer(String name) {
        Customer customer = new Customer();
        customer.setTitle(name);
        return restTemplate.postForEntity(baseURL + "/api/customer", customer, Customer.class).getBody();
    }

    public Asset createAsset(String name, String type) {
        Asset asset = new Asset();
        asset.setName(name);
        asset.setType(type);
        return restTemplate.postForEntity(baseURL + "/api/asset", asset, Asset.class).getBody();
    }

    public Device createDevice(String name, String type) {
        Device device = new Device();
        device.setName(name);
        device.setType(type);
        return restTemplate.postForEntity(baseURL + "/api/device", device, Device.class).getBody();
    }
}

package org.thingsboard.trendz.generator.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import org.thingsboard.server.common.data.Customer;
import org.thingsboard.server.common.data.Device;
import org.thingsboard.server.common.data.EntityType;
import org.thingsboard.server.common.data.asset.Asset;
import org.thingsboard.server.common.data.group.EntityGroup;
import org.thingsboard.server.common.data.id.CustomerId;
import org.thingsboard.server.common.data.id.TenantId;
import org.thingsboard.trendz.generator.model.AuthToken;
import org.thingsboard.trendz.generator.model.LoginRequest;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

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


    public Optional<Customer> getCustomerByTitle(String title) {
        try {
            Customer customer = restTemplate.getForEntity(baseURL + "/api/tenant/customers?customerTitle=" + title, Customer.class).getBody();
            return Optional.ofNullable(customer);
        } catch (HttpClientErrorException.NotFound e) {
            return Optional.empty();
        }
    }

    public Optional<Asset> getAssetByTitle(String name) {
        try {
            Asset asset = restTemplate.getForEntity(baseURL + "/api/tenant/assets?assetName=" + name, Asset.class).getBody();
            return Optional.ofNullable(asset);
        } catch (HttpClientErrorException.NotFound e) {
            return Optional.empty();
        }
    }

    public Optional<Device> getDeviceByTitle(String name) {
        try {
            Device device = restTemplate.getForEntity(baseURL + "/api/tenant/devices?deviceName=" + name, Device.class).getBody();
            return Optional.ofNullable(device);
        } catch (HttpClientErrorException.NotFound e) {
            return Optional.empty();
        }
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


    public void deleteCustomer(UUID customerId) {
        restTemplate.delete(baseURL + "/api/customer/" + customerId);
    }

    public void deleteAsset(UUID assetId) {
        restTemplate.delete(baseURL + "/api/asset/" + assetId);
    }

    public void deleteDevice(UUID deviceId) {
        restTemplate.delete(baseURL + "/api/device/" + deviceId);
    }


    public Optional<EntityGroup> getEntityGroup(String name, EntityType entityType, UUID ownerId, boolean isCustomerOwner) {
        try {
            String ownerType = (isCustomerOwner ? "CUSTOMER" : "TENANT");
            EntityGroup entityGroup = restTemplate.getForEntity(baseURL + "/api/entityGroup/" + ownerType + "/" + ownerId + "/" + entityType + "/" + name, EntityGroup.class).getBody();
            return Optional.ofNullable(entityGroup);
        } catch (HttpClientErrorException.NotFound e) {
            return Optional.empty();
        }
    }

    public EntityGroup createEntityGroup(String name, EntityType entityType, UUID ownerId, boolean isCustomerOwner) {
        EntityGroup entityGroup = new EntityGroup();
        entityGroup.setName(name);
        entityGroup.setType(entityType);
        entityGroup.setOwnerId(
                isCustomerOwner ? new CustomerId(ownerId) : new TenantId(ownerId)
        );

        return restTemplate.postForEntity(baseURL + "/api/entityGroup", entityGroup, EntityGroup.class).getBody();
    }


}

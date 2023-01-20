package org.thingsboard.trendz.generator.service;

import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import org.thingsboard.server.common.data.Customer;
import org.thingsboard.server.common.data.Device;
import org.thingsboard.server.common.data.EntityType;
import org.thingsboard.server.common.data.asset.Asset;
import org.thingsboard.server.common.data.id.EntityId;
import org.thingsboard.server.common.data.relation.EntityRelation;
import org.thingsboard.server.common.data.rule.RuleChain;
import org.thingsboard.server.common.data.rule.RuleChainMetaData;
import org.thingsboard.server.common.data.security.Authority;
import org.thingsboard.server.common.data.security.DeviceCredentials;
import org.thingsboard.trendz.generator.exception.PushTelemetryException;
import org.thingsboard.trendz.generator.model.*;
import org.thingsboard.trendz.generator.model.rest.*;
import org.thingsboard.trendz.generator.utils.JsonUtils;

import java.util.*;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class TbRestClient {

    public static final String LOGIN_PATH = "/api/auth/login";
    private static final int PUSH_TELEMETRY_DELAY = 100;
    private static final int PUSH_TELEMETRY_BATCH_SIZE = 100;
    private static final int PUSH_TELEMETRY_SUPPRESS_ERROR_COUNT = 10;

    private final String baseURL;
    private final boolean pe;
    private final boolean cloud;
    private final RestTemplate restTemplate;

    @Autowired
    public TbRestClient(
            @Value("${tb.api.host}") String tbApiHost,
            @Value("${tb.api.pe}") boolean pe,
            @Value("${tb.api.cloud}") boolean cloud,
            RestTemplate restTemplate
    ) {
        this.baseURL = tbApiHost;
        this.pe = pe;
        this.cloud = cloud;
        this.restTemplate = restTemplate;
    }


    public AuthToken login(LoginRequest request) {
        ResponseEntity<AuthToken> response = restTemplate.postForEntity(baseURL + LOGIN_PATH, request, AuthToken.class);
        AuthToken authToken = response.getBody();
        if (authToken == null) {
            throw new IllegalStateException("Login request is failed!");
        }
        return authToken;
    }

    public AuthToken refreshToken(String refreshToken) {
        Map<String, String> request = new HashMap<>();
        request.put("refreshToken", refreshToken);

        ResponseEntity<AuthToken> response = restTemplate.postForEntity(LOGIN_PATH, request, AuthToken.class);
        AuthToken authToken = response.getBody();
        if (authToken == null) {
            throw new IllegalStateException("Refresh token is failed!");
        }
        return authToken;
    }

    public CustomerUser createCustomerUser(Customer customer, String email, String password, String firstName, String lastName) {
        CustomerUser user = CustomerUser.builder()
                .additionalInfo(CustomerUserAdditionalInfo.defaultInfo())
                .authority(Authority.CUSTOMER_USER)
                .customerId(customer.getId())
                .tenantId(customer.getTenantId())
                .email(email)
                .firstName(firstName)
                .lastName(lastName)
                .build();

        CustomerUser savedUser = restTemplate.postForEntity(baseURL + "/api/user?sendActivationMail=false", user, CustomerUser.class).getBody();
        Objects.requireNonNull(savedUser);

        String activationLink = restTemplate.getForEntity(baseURL + "/api/user/" + savedUser.getId().getId() + "/activationLink", String.class).getBody();
        Objects.requireNonNull(activationLink);

        String token = activationLink.substring(activationLink.lastIndexOf('=') + 1);
        ActivationRequest activationRequest = new ActivationRequest(token, password);
        ActivationAuthToken activationAuthToken = restTemplate.postForEntity(baseURL + "/api/noauth/activate?sendActivationMail=true", activationRequest, ActivationAuthToken.class).getBody();

        return savedUser;
    }


    public Optional<Customer> getCustomerById(UUID customerId) {
        try {
            Customer customer = restTemplate.getForEntity(baseURL + "/api/customer/" + customerId.toString(), Customer.class).getBody();
            return Optional.ofNullable(customer);
        } catch (HttpClientErrorException.NotFound e) {
            return Optional.empty();
        }
    }

    public Optional<Asset> getAssetById(UUID assetId) {
        try {
            Asset asset = restTemplate.getForEntity(baseURL + "/api/asset/" + assetId.toString(), Asset.class).getBody();
            return Optional.ofNullable(asset);
        } catch (HttpClientErrorException.NotFound e) {
            return Optional.empty();
        }
    }

    public Optional<Device> getDeviceById(UUID deviceId) {
        try {
            Device device = restTemplate.getForEntity(baseURL + "/api/device/" + deviceId.toString(), Device.class).getBody();
            return Optional.ofNullable(device);
        } catch (HttpClientErrorException.NotFound e) {
            return Optional.empty();
        }
    }


    public Optional<Customer> getCustomerByTitle(String title) {
        try {
            Customer customer = restTemplate.getForEntity(baseURL + "/api/tenant/customers?customerTitle=" + title, Customer.class).getBody();
            return Optional.ofNullable(customer);
        } catch (HttpClientErrorException.NotFound e) {
            return Optional.empty();
        }
    }

    public Optional<Asset> getAssetByName(String name) {
        try {
            Asset asset = restTemplate.getForEntity(baseURL + "/api/tenant/assets?assetName=" + name, Asset.class).getBody();
            return Optional.ofNullable(asset);
        } catch (HttpClientErrorException.NotFound e) {
            return Optional.empty();
        }
    }

    public Optional<Device> getDeviceByName(String name) {
        try {
            Device device = restTemplate.getForEntity(baseURL + "/api/tenant/devices?deviceName=" + name, Device.class).getBody();
            return Optional.ofNullable(device);
        } catch (HttpClientErrorException.NotFound e) {
            return Optional.empty();
        }
    }


    public Set<Customer> getAllCustomers() {
        ParameterizedTypeReference<PageData<Customer>> reference = new ParameterizedTypeReference<>() {
        };

        return getAllEntities("/api/customers", reference);
    }

    public Set<Asset> getAllAssets() {
        ParameterizedTypeReference<PageData<Asset>> reference = new ParameterizedTypeReference<>() {
        };

        return getAllEntities("/api/tenant/assets", reference);
    }

    public Set<Device> getAllDevices() {
        ParameterizedTypeReference<PageData<Device>> reference = new ParameterizedTypeReference<>() {
        };

        return getAllEntities("/api/tenant/devices", reference);
    }

    private <T> Set<T> getAllEntities(String request, ParameterizedTypeReference<PageData<T>> type) {
        Set<T> result = new HashSet<>();
        boolean hasNextPage = true;
        PageData<T> page;
        int pageIndex = 0;
        int pageSize = 100;
        while (hasNextPage) {
            page = restTemplate.exchange(
                    baseURL + request + "?page={page}&pageSize={pageSize}",
                    HttpMethod.GET,
                    null,
                    type,
                    pageIndex, pageSize
            ).getBody();
            hasNextPage = page.hasNext();
            result.addAll(page.getData());
        }
        return result;
    }


    public Customer createCustomer(String name) {
        Customer customer = new Customer();
        customer.setTitle(name);
        return restTemplate.postForEntity(baseURL + "/api/customer", customer, Customer.class).getBody();
    }

    public Asset createAsset(String name, String type) {
        try {
            Asset asset = new Asset();
            asset.setName(name);
            asset.setType(type);
            return restTemplate.postForEntity(baseURL + "/api/asset", asset, Asset.class).getBody();
        } catch (Exception e) {
            throw new RuntimeException("", e);
        }
    }

    public Device createDevice(String name, String type) {
        try {
            Device device = new Device();
            device.setName(name);
            device.setType(type);
            return restTemplate.postForEntity(baseURL + "/api/device", device, Device.class).getBody();
        } catch (Exception e) {
            throw new RuntimeException("", e);
        }
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


    public Optional<EntityRelation> getRelation(UUID fromId, EntityType fromType, UUID toId, EntityType toType, RelationType type) {
        try {
            EntityRelation entityRelation = restTemplate.getForEntity(
                    baseURL + "/api/relation/?fromId={fromId}&fromType={fromType}&relationType={type}&toId={toId}&toType={toType}",
                    EntityRelation.class,
                    fromId, fromType, type.getType(), toId, toType
            ).getBody();
            return Optional.ofNullable(entityRelation);
        } catch (HttpClientErrorException.NotFound e) {
            return Optional.empty();
        }
    }

    public EntityRelation createRelation(String relationType, EntityId idFrom, EntityId idTo) {
        EntityRelation relation = new EntityRelation();
        relation.setFrom(idFrom);
        relation.setTo(idTo);
        relation.setType(relationType);
        return restTemplate.postForEntity(baseURL + "/api/relation", relation, EntityRelation.class).getBody();
    }

    public void deleteRelation(UUID fromId, EntityType fromType, UUID toId, EntityType toType, RelationType type) {
        restTemplate.delete(
                baseURL + "/api/relation/?fromId={fromId}&fromType={fromType}&relationType={type}&toId={toId}&toType={toType}",
                fromId, fromType, type.getType(), toId, toType
        );
    }


    public void assignDeviceToCustomer(UUID customerId, UUID deviceId) {
        Map<String, Object> params = new HashMap<>();
        params.put("customerId", customerId);
        params.put("deviceId", deviceId);
        restTemplate.postForEntity(baseURL + "/api/customer/{customerId}/device/{deviceId}", "", Device.class, params).getBody();
    }

    public void assignAssetToCustomer(UUID customerId, UUID assetId) {
        Map<String, Object> params = new HashMap<>();
        params.put("customerId", customerId);
        params.put("assetId", assetId);
        restTemplate.postForEntity(baseURL + "/api/customer/{customerId}/asset/{assetId}", "", Asset.class, params).getBody();
    }

    public void unassignDeviceToCustomer(UUID deviceId) {
        restTemplate.delete(baseURL + "/api/customer/device/{deviceId}", deviceId);
    }

    public void unassignAssetToCustomer(UUID assetId) {
        restTemplate.delete(baseURL + "/api/customer/asset/{assetId}", assetId);
    }


    public DeviceCredentials getDeviceCredentials(UUID id) {
        return restTemplate.getForEntity(baseURL + "/api/device/" + id.toString() + "/credentials", DeviceCredentials.class).getBody();
    }

    public <T> void pushTelemetry(String accessToken, Telemetry<T> telemetry) {
        if (telemetry.getPoints().isEmpty()) {
            return;
        }
        if (this.cloud) {
            log.info("Pushing telemetry '{}' to the cloud, batch size = {}, send delay = {}, suppress error count = {}",
                    telemetry.getName(),
                    PUSH_TELEMETRY_BATCH_SIZE,
                    PUSH_TELEMETRY_DELAY,
                    PUSH_TELEMETRY_SUPPRESS_ERROR_COUNT
            );

            List<Telemetry<T>> partitions = telemetry.partition(PUSH_TELEMETRY_BATCH_SIZE);
            int errorCount = 0;

            for (int i = 0; i < partitions.size(); i++) {
                try {
                    if (PUSH_TELEMETRY_SUPPRESS_ERROR_COUNT <= errorCount) {
                        break;
                    }

                    TimeUnit.MILLISECONDS.sleep(PUSH_TELEMETRY_DELAY);
                    pushTelemetry0(accessToken, partitions.get(i));

                    errorCount = 0;
                    log.info("Batch is sent ({}/{})", i, partitions.size());
                } catch (Exception e) {
                    log.error("Error during pushing telemetry to the cloud, error count = " + errorCount + ", retry...", e);
                    errorCount++;
                    i--;
                }
            }
            if (PUSH_TELEMETRY_SUPPRESS_ERROR_COUNT <= errorCount) {
                throw new PushTelemetryException(telemetry);
            }

        } else {
            pushTelemetry0(accessToken, telemetry);
        }
    }

    private void pushTelemetry0(String accessToken, Telemetry<?> telemetry) {
        String json = telemetry.toJson();
        restTemplate.postForEntity(baseURL + "/api/v1/" + accessToken + "/telemetry", json, String.class).getBody();
    }


    public void setEntityAttributes(UUID entityId, EntityType entityType, Attribute.Scope scope, Set<Attribute<?>> attributes) {
        Map<String, Object> params = new HashMap<>();
        params.put("entityId", entityId);
        params.put("entityType", entityType);
        params.put("scope", scope);

        ObjectNode node = JsonUtils.getObjectMapper().createObjectNode();
        for (Attribute<?> attribute : attributes) {
            Object value = attribute.getValue();
            if (value instanceof Integer) {
                node.put(attribute.getKey(), ((Integer) value));
            } else if (value instanceof Byte) {
                node.put(attribute.getKey(), ((Byte) value));
            } else if (value instanceof Short) {
                node.put(attribute.getKey(), ((Short) value));
            } else if (value instanceof Long) {
                node.put(attribute.getKey(), ((Long) value));
            } else if (value instanceof Float) {
                node.put(attribute.getKey(), ((Float) value));
            } else if (value instanceof Double) {
                node.put(attribute.getKey(), ((Double) value));
            } else {
                node.put(attribute.getKey(), value.toString());
            }
        }

        restTemplate.postForEntity(baseURL + "/api/plugins/telemetry/{entityType}/{entityId}/{scope}", node, Object.class, params).getBody();
    }


    public Set<RuleChain> getAllRuleChains() {
        ParameterizedTypeReference<PageData<RuleChain>> reference = new ParameterizedTypeReference<>() {
        };

        return getAllEntities("/api/ruleChains", reference);
    }

    public Optional<RuleChain> getRuleChainById(UUID ruleChainId) {
        try {
            RuleChain ruleChain = restTemplate.getForEntity(baseURL + "/api/ruleChain/" + ruleChainId.toString(), RuleChain.class).getBody();
            return Optional.ofNullable(ruleChain);
        } catch (HttpClientErrorException.NotFound e) {
            return Optional.empty();
        }
    }

    public RuleChain createRuleChain(String name) {
        RuleChain ruleChain = new RuleChain();
        ruleChain.setName(name);
        return restTemplate.postForEntity(baseURL + "/api/ruleChain", ruleChain, RuleChain.class).getBody();
    }

    public void deleteRuleChain(UUID ruleChainId) {
        restTemplate.delete(baseURL + "/api/ruleChain/" + ruleChainId);
    }


    public Optional<RuleChainMetaData> getRuleChainMetadataByRuleChainId(UUID ruleChainId) {
        try {
            RuleChainMetaData metadata = restTemplate.getForEntity(baseURL + "/api/ruleChain/" + ruleChainId.toString() + "/metadata", RuleChainMetaData.class).getBody();
            return Optional.ofNullable(metadata);
        } catch (HttpClientErrorException.NotFound e) {
            return Optional.empty();
        }
    }

    public RuleChainMetaData saveRuleChainMetadata(RuleChainMetaData metaData) {
        return restTemplate.postForEntity(baseURL + "/api/ruleChain/metadata", metaData, RuleChainMetaData.class).getBody();
    }

//    // PE functions
//
//    public Optional<EntityGroup> getEntityGroup(String name, EntityType entityType, UUID ownerId, boolean isCustomerOwner) {
//        try {
//            String ownerType = (isCustomerOwner ? "CUSTOMER" : "TENANT");
//            EntityGroup entityGroup = restTemplate.getForEntity(baseURL + "/api/entityGroup/" + ownerType + "/" + ownerId + "/" + entityType + "/" + name, EntityGroup.class).getBody();
//            return Optional.ofNullable(entityGroup);
//        } catch (HttpClientErrorException.NotFound e) {
//            return Optional.empty();
//        }
//    }
//
//    public EntityGroup createEntityGroup(String name, EntityType entityType, UUID ownerId, boolean isCustomerOwner) {
//        EntityGroup entityGroup = new EntityGroup();
//        entityGroup.setName(name);
//        entityGroup.setType(entityType);
//        entityGroup.setOwnerId(
//                isCustomerOwner ? new CustomerId(ownerId) : new TenantId(ownerId)
//        );
//
//        return restTemplate.postForEntity(baseURL + "/api/entityGroup", entityGroup, EntityGroup.class).getBody();
//    }
}

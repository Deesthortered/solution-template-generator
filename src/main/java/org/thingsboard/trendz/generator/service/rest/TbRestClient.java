package org.thingsboard.trendz.generator.service.rest;

import com.fasterxml.jackson.databind.node.ArrayNode;
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
import org.thingsboard.server.common.data.Dashboard;
import org.thingsboard.server.common.data.Device;
import org.thingsboard.server.common.data.EntityType;
import org.thingsboard.server.common.data.asset.Asset;
import org.thingsboard.server.common.data.group.EntityGroup;
import org.thingsboard.server.common.data.id.CustomerId;
import org.thingsboard.server.common.data.id.EntityId;
import org.thingsboard.server.common.data.id.TenantId;
import org.thingsboard.server.common.data.relation.EntityRelation;
import org.thingsboard.server.common.data.rule.RuleChain;
import org.thingsboard.server.common.data.rule.RuleChainMetaData;
import org.thingsboard.server.common.data.security.Authority;
import org.thingsboard.server.common.data.security.DeviceCredentials;
import org.thingsboard.trendz.generator.exception.PushTelemetryException;
import org.thingsboard.trendz.generator.model.rest.ActivationAuthToken;
import org.thingsboard.trendz.generator.model.rest.ActivationRequest;
import org.thingsboard.trendz.generator.model.rest.AuthToken;
import org.thingsboard.trendz.generator.model.rest.LoginRequest;
import org.thingsboard.trendz.generator.model.rest.PageData;
import org.thingsboard.trendz.generator.model.tb.Attribute;
import org.thingsboard.trendz.generator.model.tb.CustomerUser;
import org.thingsboard.trendz.generator.model.tb.CustomerUserAdditionalInfo;
import org.thingsboard.trendz.generator.model.tb.RelationType;
import org.thingsboard.trendz.generator.model.tb.Telemetry;
import org.thingsboard.trendz.generator.service.jwt.TokenExtractor;
import org.thingsboard.trendz.generator.utils.JsonUtils;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.StringJoiner;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;

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
    private final TokenExtractor tokenExtractor;

    private UUID tenantId;

    @Autowired
    public TbRestClient(
            @Value("${tb.api.host}") String tbApiHost,
            @Value("${tb.api.pe}") boolean pe,
            @Value("${tb.api.cloud}") boolean cloud,
            RestTemplate restTemplate,
            TokenExtractor tokenExtractor
    ) {
        this.baseURL = tbApiHost;
        this.pe = pe;
        this.cloud = cloud;
        this.restTemplate = restTemplate;
        this.tokenExtractor = tokenExtractor;
    }


    public boolean isPe() {
        return this.pe;
    }

    public boolean isCloud() {
        return this.cloud;
    }


    public AuthToken login(LoginRequest request) {
        ResponseEntity<AuthToken> response = restTemplate.postForEntity(baseURL + LOGIN_PATH, request, AuthToken.class);
        AuthToken authToken = response.getBody();
        if (authToken == null) {
            throw new IllegalStateException("Login request is failed!");
        }
        this.tenantId = tokenExtractor.getTenantId(authToken);
        return authToken;
    }

    public UUID getTenantId() {
        return this.tenantId;
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

        try {

            CustomerUser savedUser = restTemplate.postForEntity(baseURL + "/api/user?sendActivationMail=false", user, CustomerUser.class).getBody();
            Objects.requireNonNull(savedUser);

            String activationLink = restTemplate.getForEntity(baseURL + "/api/user/" + savedUser.getId().getId() + "/activationLink", String.class).getBody();
            Objects.requireNonNull(activationLink);

            String token = activationLink.substring(activationLink.lastIndexOf('=') + 1);
            ActivationRequest activationRequest = new ActivationRequest(token, password);
            ActivationAuthToken activationAuthToken = restTemplate.postForEntity(baseURL + "/api/noauth/activate?sendActivationMail=true", activationRequest, ActivationAuthToken.class).getBody();
            return savedUser;
        } catch (HttpClientErrorException.BadRequest ex) {
            log.trace("Customer was already activated");
            var customerUsers = getCustomerUsers(customer.getId().getId().toString());
            return customerUsers.stream().findAny().get();
        }
    }

    public Dashboard assignDashboardToSpecifiedCustomer(UUID dashboardId, UUID customerId) {
        try {
            return restTemplate.postForEntity(baseURL + "/api/customer/" + customerId.toString() + "/dashboard/" + dashboardId.toString(), null, Dashboard.class).getBody();
        } catch (Exception e) {
            throw new RuntimeException("", e);
        }
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

    public Optional<Dashboard> getDashboardById(UUID dashboardId) {
        try {
            Dashboard dashboard = restTemplate.getForEntity(baseURL + "/api/dashboard/" + dashboardId.toString(), Dashboard.class).getBody();
            return Optional.ofNullable(dashboard);
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
        var reference = new ParameterizedTypeReference<PageData<Customer>>() {
        };

        return getAllEntities("/api/customers", reference, new HashMap<>());
    }

    public Set<Asset> getAllAssets() {
        var reference = new ParameterizedTypeReference<PageData<Asset>>() {
        };

        return getAllEntities("/api/tenant/assets", reference, new HashMap<>());
    }

    public Set<Device> getAllDevices() {
        var reference = new ParameterizedTypeReference<PageData<Device>>() {
        };

        return getAllEntities("/api/tenant/devices", reference, new HashMap<>());
    }

    public Set<Dashboard> getAllTenantDashboards() {
        var reference = new ParameterizedTypeReference<PageData<Dashboard>>() {
        };

        return getAllEntities("/api/tenant/dashboards", reference, new HashMap<>());
    }

    public Set<Dashboard> getAllCustomerDashboards(UUID customerId) {
        var reference = new ParameterizedTypeReference<PageData<Dashboard>>() {
        };

        return getAllEntities("/api/customer/" + customerId.toString() + "/dashboards", reference, new HashMap<>());
    }

    public Customer createCustomer(String name) {
        var customer = new Customer();
        customer.setTitle(name);
        return restTemplate.postForEntity(baseURL + "/api/customer", customer, Customer.class).getBody();
    }

    public Customer createCustomerIfNotExists(String name) {
        var customerOpt = getCustomerByTitle(name);
        return customerOpt.orElseGet(() -> createCustomer(name));
    }

    public Asset createAsset(String name, String type, Set<Attribute<?>> attributes) {
        try {
            var asset = new Asset();
            asset.setName(name);
            asset.setType(type);
            var assetAdded = restTemplate.postForEntity(baseURL + "/api/asset", asset, Asset.class).getBody();

            if (nonNull(attributes) && !attributes.isEmpty()) {
                setEntityAttributes(assetAdded.getUuidId(), EntityType.ASSET, Attribute.Scope.SERVER_SCOPE, attributes);
            }

            return assetAdded;
        } catch (Exception e) {
            throw new RuntimeException("", e);
        }
    }

    public Asset createAssetIfNotExists(String name, String type, Set<Attribute<?>> attributes) {
        final var assetOpt = getAssetByName(name);
        return assetOpt.orElseGet(() -> createAsset(name, type, attributes));
    }

    public Device createDevice(String name, String type, Set<Attribute<?>> attributes) {
        try {
            var device = new Device();
            device.setName(name);
            device.setType(type);
            var deviceAdded = restTemplate.postForEntity(baseURL + "/api/device", device, Device.class).getBody();

            if (nonNull(attributes) && !attributes.isEmpty()) {
                setEntityAttributes(deviceAdded.getUuidId(), EntityType.DEVICE, Attribute.Scope.SERVER_SCOPE, attributes);
            }
            return deviceAdded;
        } catch (Exception e) {
            throw new RuntimeException("", e);
        }
    }

    public Device createDeviceIfNotExists(String name, String type, Set<Attribute<?>> attributes) {
        var deviceOpt = getDeviceByName(name);
        return deviceOpt.orElseGet(() -> createDevice(name, type, attributes));
    }

    public Dashboard createDashboard(String title) {
        try {
            Dashboard dashboard = new Dashboard();
            dashboard.setTitle(title);
            return restTemplate.postForEntity(baseURL + "/api/dashboard", dashboard, Dashboard.class).getBody();
        } catch (Exception e) {
            throw new RuntimeException("", e);
        }
    }

    public Dashboard createDashboardIfNotExists(String title) {
        var dashboardOpt = getTenantDashboardsByTitle(title).stream().findAny();
        return dashboardOpt.orElseGet(() -> createDashboard(title));
    }


    public Customer createCustomer(String name, EntityId ownerId) {
        Customer customer = new Customer();
        customer.setTitle(name);
        customer.setOwnerId(ownerId);
        return restTemplate.postForEntity(baseURL + "/api/customer", customer, Customer.class).getBody();
    }

    public Asset createAsset(String name, String type, EntityId ownerId, Set<Attribute<?>> attributes) {
        try {
            var asset = new Asset();
            asset.setName(name);
            asset.setType(type);
            asset.setOwnerId(ownerId);
            var assetAdded = restTemplate.postForEntity(baseURL + "/api/asset", asset, Asset.class).getBody();

            if (nonNull(attributes) && !attributes.isEmpty()) {
                setEntityAttributes(assetAdded.getUuidId(), EntityType.ASSET, Attribute.Scope.SERVER_SCOPE, attributes);
            }

            return assetAdded;
        } catch (Exception e) {
            throw new RuntimeException("", e);
        }
    }

    public Asset createAssetIfNotExists(String name, String type, EntityId ownerId, Set<Attribute<?>> attributes) {
        final var assetOpt = getAssetByName(name);
        return assetOpt.orElseGet(() -> createAsset(name, type, ownerId, attributes));
    }

    public Device createDevice(String name, String type, EntityId ownerId, Set<Attribute<?>> attributes) {
        try {
            var device = new Device();
            device.setName(name);
            device.setType(type);
            device.setOwnerId(ownerId);
            var deviceAdded = restTemplate.postForEntity(baseURL + "/api/device", device, Device.class).getBody();

            if (nonNull(attributes) && !attributes.isEmpty()) {
                setEntityAttributes(deviceAdded.getUuidId(), EntityType.DEVICE, Attribute.Scope.SERVER_SCOPE, attributes);
            }
            return deviceAdded;
        } catch (Exception e) {
            throw new RuntimeException("", e);
        }
    }

    public Device createDeviceIfNotExists(String name, String type, EntityId ownerId, Set<Attribute<?>> attributes) {
        var deviceOpt = getDeviceByName(name);
        return deviceOpt.orElseGet(() -> createDevice(name, type, ownerId, attributes));
    }

    public Dashboard createDashboard(String title, EntityId ownerId) {
        try {
            Dashboard dashboard = new Dashboard();
            dashboard.setTitle(title);
            dashboard.setOwnerId(ownerId);
            return restTemplate.postForEntity(baseURL + "/api/dashboard", dashboard, Dashboard.class).getBody();
        } catch (Exception e) {
            throw new RuntimeException("", e);
        }
    }

    public Dashboard createDashboardIfNotExists(String title, EntityId ownerId) {
        var dashBoardOpt = getTenantDashboardsByTitle(title).stream().findAny();
        return dashBoardOpt.orElseGet(() -> createDashboard(title, ownerId));
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

    public void deleteDashboard(UUID dashboardId) {
        restTemplate.delete(baseURL + "/api/dashboard/" + dashboardId);
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
                    log.info("Batch is sent ({}/{})", i + 1, partitions.size());
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

        return getAllEntities("/api/ruleChains", reference, new HashMap<>());
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
        try {
            return restTemplate.postForEntity(baseURL + "/api/ruleChain/metadata", metaData, RuleChainMetaData.class).getBody();
        } catch (Exception e) {
            String metadataJson = JsonUtils.makeNodeFromPojo(metaData).toPrettyString();
            throw new IllegalStateException("Error during rule chain saving: " + e.getMessage() + "\n\n " + metadataJson, e);
        }
    }


    // PE functions

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
        entityGroup.setOwnerId(isCustomerOwner ? new CustomerId(ownerId) : new TenantId(ownerId));

        return restTemplate.postForEntity(baseURL + "/api/entityGroup", entityGroup, EntityGroup.class).getBody();
    }

    public EntityGroup createEntityGroupIfNotExists(String name, EntityType entityType, UUID ownerId, boolean isCustomerOwner) {
        final var entityGroupOpt = getEntityGroup(name, entityType, ownerId, isCustomerOwner);
        return entityGroupOpt.orElseGet(() -> createEntityGroup(name, entityType, ownerId, isCustomerOwner));
    }

    public void deleteEntityGroup(UUID entityGroupId) {
        restTemplate.delete(baseURL + "/api/entityGroup/" + entityGroupId.toString());
    }

    public void addEntitiesToTheGroup(UUID entityGroupId, Set<UUID> entityIdSet) {
        ArrayNode arrayNode = JsonUtils.getObjectMapper().createArrayNode();
        for (UUID uuid : entityIdSet) {
            arrayNode.add(uuid.toString());
        }

        restTemplate.postForEntity(
                baseURL + "/api/entityGroup/" + entityGroupId.toString() + "/addEntities",
                arrayNode,
                EntityGroup.class
        ).getBody();
    }

    public void deleteEntitiesFromTheGroup(UUID entityGroupId, Set<UUID> entityIdSet) {
        ArrayNode arrayNode = JsonUtils.getObjectMapper().createArrayNode();
        for (UUID uuid : entityIdSet) {
            arrayNode.add(uuid.toString());
        }

        restTemplate.postForEntity(
                baseURL + "/api/entityGroup/" + entityGroupId.toString() + "/deleteEntities",
                arrayNode,
                EntityGroup.class
        ).getBody();
    }


    public void setCustomerUserToCustomerAdministratorsGroup(Customer customer, CustomerUser customerUser) {
        try {
            EntityGroup customerUsersGroup = getEntityGroup(
                    "Customer Administrators",
                    EntityType.USER,
                    customer.getUuidId(),
                    true
            ).orElseThrow();

            addEntitiesToTheGroup(customerUsersGroup.getUuidId(), Set.of(customerUser.getId().getId()));
        } catch (Exception e) {
            throw new RuntimeException("Can not assign customer user to the Customer Group", e);
        }
    }

    public void setCustomerUserToCustomerUsersGroup(Customer customer, CustomerUser customerUser) {
        try {
            EntityGroup customerUsersGroup = getEntityGroup(
                    "Customer Users",
                    EntityType.USER,
                    customer.getUuidId(),
                    true
            ).orElseThrow();

            addEntitiesToTheGroup(customerUsersGroup.getUuidId(), Set.of(customerUser.getId().getId()));
        } catch (Exception e) {
            throw new RuntimeException("Can not assign customer user to the Customer Group", e);
        }
    }

    public Set<CustomerUser> getCustomerUsers(String customerId) {
        var reference = new ParameterizedTypeReference<PageData<CustomerUser>>() {
        };

        return getAllEntities("/api/customer/" + customerId + "/users", reference, new HashMap<>());
    }

    public Set<Dashboard> getTenantDashboardsByTitle(String title) {
        var customParams = new HashMap<String, Object>() {{
            put("textSearch", title);
            put("sortProperty", "title");
            put("sortOrder", "ASC");
        }};
        var reference = new ParameterizedTypeReference<PageData<Dashboard>>() {
        };

        return getAllEntities("/api/tenant/dashboards", reference, customParams);
    }

    private <T> Set<T> getAllEntities(String request,
                                      ParameterizedTypeReference<PageData<T>> type,
                                      Map<String, Object> customParams) {
        Set<T> result = new HashSet<>();
        var pageSize = 100;
        var pageIndex = 0;
        var hasNextPage = true;
        PageData<T> page;

        try {
            while (hasNextPage) {
                customParams.put("page", pageIndex);
                customParams.put("pageSize", pageSize);

                var urlParams = generateUrlParams(customParams);
                page = restTemplate.exchange(
                        baseURL + request + urlParams,
                        HttpMethod.GET,
                        null,
                        type
                ).getBody();

                if (isNull(page)) break;

                hasNextPage = page.hasNext();
                customParams.put("page", pageIndex++);
                result.addAll(page.getData());
            }
            return result;
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException("Can not generate url params", e);
        }
    }

    private String generateUrlParams(Map<String, Object> params) throws UnsupportedEncodingException {
        if (isNull(params) || params.isEmpty()) {
            return "";
        }

        var charset = "UTF-8";
        var queryString = new StringJoiner("&", "?", "");

        for (Map.Entry<String, Object> entry : params.entrySet()) {
            final var encodedKey = URLEncoder.encode(entry.getKey(), charset);
            final var encodedValue = URLEncoder.encode(entry.getValue().toString(), charset);
            queryString.add(encodedKey + "=" + encodedValue);
        }

        return queryString.toString();
    }

    private void pushTelemetry0(String accessToken, Telemetry<?> telemetry) {
        final String json = telemetry.toJson();
        restTemplate.postForEntity(baseURL + "/api/v1/" + accessToken + "/telemetry", json, String.class).getBody();
    }

}

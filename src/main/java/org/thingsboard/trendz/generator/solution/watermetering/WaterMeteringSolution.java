package org.thingsboard.trendz.generator.solution.watermetering;

import com.google.common.collect.Sets;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.thingsboard.server.common.data.Customer;
import org.thingsboard.server.common.data.Device;
import org.thingsboard.server.common.data.EntityType;
import org.thingsboard.server.common.data.asset.Asset;
import org.thingsboard.server.common.data.rule.NodeConnectionInfo;
import org.thingsboard.server.common.data.rule.RuleChain;
import org.thingsboard.server.common.data.rule.RuleChainMetaData;
import org.thingsboard.server.common.data.rule.RuleNode;
import org.thingsboard.server.common.data.security.DeviceCredentials;
import org.thingsboard.trendz.generator.exception.AssetAlreadyExistException;
import org.thingsboard.trendz.generator.exception.CustomerAlreadyExistException;
import org.thingsboard.trendz.generator.exception.DeviceAlreadyExistException;
import org.thingsboard.trendz.generator.exception.RuleChainAlreadyExistException;
import org.thingsboard.trendz.generator.model.ModelData;
import org.thingsboard.trendz.generator.model.ModelEntity;
import org.thingsboard.trendz.generator.model.tb.Attribute;
import org.thingsboard.trendz.generator.model.tb.CustomerData;
import org.thingsboard.trendz.generator.model.tb.CustomerUser;
import org.thingsboard.trendz.generator.model.tb.RelationType;
import org.thingsboard.trendz.generator.service.FileService;
import org.thingsboard.trendz.generator.service.VisualizationService;
import org.thingsboard.trendz.generator.service.anomaly.AnomalyService;
import org.thingsboard.trendz.generator.service.rest.TbRestClient;
import org.thingsboard.trendz.generator.solution.SolutionTemplateGenerator;
import org.thingsboard.trendz.generator.solution.watermetering.model.City;
import org.thingsboard.trendz.generator.solution.watermetering.model.Consumer;
import org.thingsboard.trendz.generator.solution.watermetering.model.PumpStation;
import org.thingsboard.trendz.generator.solution.watermetering.model.Region;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
public class WaterMeteringSolution implements SolutionTemplateGenerator {

    private static final String CUSTOMER_TITLE = "Water Metering Customer";
    private static final String CUSTOMER_USER_EMAIL = "watermetering@thingsboard.io";
    private static final String CUSTOMER_USER_PASSWORD = "password";
    private static final String CUSTOMER_USER_FIRST_NAME = "John";
    private static final String CUSTOMER_USER_LAST_NAME = "Doe";

    private static final String RULE_CHAIN_NAME = "Water Metering Rule Chain";

    private final TbRestClient tbRestClient;
    private final FileService fileService;
    private final AnomalyService anomalyService;
    private final VisualizationService visualizationService;

    private final Map<Region, UUID> regionToIdMap = new HashMap<>();
    private final Map<Consumer, UUID> consumerToIdMap = new HashMap<>();
    private final Map<PumpStation, UUID> pumpStationToIdMap = new HashMap<>();

    @Autowired
    public WaterMeteringSolution(
            TbRestClient tbRestClient,
            FileService fileService,
            AnomalyService anomalyService,
            VisualizationService visualizationService
    ) {
        this.tbRestClient = tbRestClient;
        this.fileService = fileService;
        this.anomalyService = anomalyService;
        this.visualizationService = visualizationService;
    }

    @Override
    public String getSolutionName() {
        return "WaterMetering";
    }

    @Override
    public void validate() {
        try {
            log.info("Water Metering Solution - start validation");

            validateCustomerData();
            validateRuleChain();
            ModelData data = makeData(true, ZonedDateTime.now());
            validateData(data);

            log.info("Water Metering Solution - validation is completed!");
        } catch (Exception e) {
            log.error("Water Metering Solution validation was failed, skipping...", e);
        }
    }

    @Override
    public void generate(boolean skipTelemetry, ZonedDateTime startYear) {
        log.info("Water Metering Solution - start generation");
        try {
            CustomerData customerData = createCustomerData();
            ModelData data = makeData(skipTelemetry, startYear);
            applyData(data, customerData);
            createRuleChain(data);

            log.info("Water Metering Solution - generation is completed!");
        } catch (Exception e) {
            log.error("Water Metering Solution generate was failed, skipping...", e);
        }
    }

    @Override
    public void remove() {
        log.info("Water Metering Solution - start removal");
        try {
            deleteCustomerData();
            deleteRuleChain();
            ModelData data = makeData(true, ZonedDateTime.now());
            deleteData(data);

            log.info("Water Metering Solution - removal is completed!");
        } catch (Exception e) {
            log.error("Water Metering Solution removal was failed, skipping...", e);
        }
    }


    private Set<City> mapToCities(ModelData data) {
        return data.getData().stream()
                .map(modelEntity -> (City) modelEntity)
                .collect(Collectors.toSet());
    }


    private CustomerData createCustomerData() {
        Customer customer = this.tbRestClient.createCustomer(CUSTOMER_TITLE);
        CustomerUser customerUser = this.tbRestClient.createCustomerUser(
                customer, CUSTOMER_USER_EMAIL, CUSTOMER_USER_PASSWORD,
                CUSTOMER_USER_FIRST_NAME, CUSTOMER_USER_LAST_NAME
        );

        return new CustomerData(customer, customerUser);
    }

    private void validateCustomerData() {
        tbRestClient.getCustomerByTitle(CUSTOMER_TITLE)
                .ifPresent(customer -> {
                    throw new CustomerAlreadyExistException(customer);
                });
    }

    private void deleteCustomerData() {
        tbRestClient.getCustomerByTitle(CUSTOMER_TITLE)
                .ifPresent(customer -> this.tbRestClient.deleteCustomer(customer.getUuidId()));
    }


    private void createRuleChain(ModelData data) {
        Set<City> cities = mapToCities(data);

        try {
            RuleChain ruleChain = this.tbRestClient.createRuleChain(RULE_CHAIN_NAME);
            RuleChainMetaData metaData = this.tbRestClient.getRuleChainMetadataByRuleChainId(ruleChain.getUuidId())
                    .orElseThrow();

            List<RuleNode> nodes = metaData.getNodes();
            List<NodeConnectionInfo> connections = new ArrayList<>();
            metaData.setConnections(connections);

            for (City city : cities) {
                for (Region region : city.getRegions()) {
                    UUID regionId = this.regionToIdMap.get(region);

                    int index = nodes.size();
                }
                for (PumpStation pumpStation : city.getPumpStations()) {
                    UUID pumpStationId = this.pumpStationToIdMap.get(pumpStation);

                    int index = nodes.size();
                }
            }

            RuleChainMetaData savedMetaData = this.tbRestClient.saveRuleChainMetadata(metaData);
        } catch (Exception e) {
            throw new RuntimeException("Exception during rule chain creation", e);
        }
    }

    private void validateRuleChain() {
        this.tbRestClient.getAllRuleChains()
                .stream()
                .filter(ruleChain -> ruleChain.getName().equals(RULE_CHAIN_NAME))
                .findAny()
                .ifPresent(ruleChain -> {
                    throw new RuleChainAlreadyExistException(ruleChain);
                });
    }

    private void deleteRuleChain() {
        this.tbRestClient.getAllRuleChains()
                .stream()
                .filter(ruleChain -> ruleChain.getName().equals(RULE_CHAIN_NAME))
                .findAny()
                .ifPresent(ruleChain -> this.tbRestClient.deleteRuleChain(ruleChain.getUuidId()));
    }


    private ModelData makeData(boolean skipTelemetry, ZonedDateTime startYear) {
//        Building alpire = makeAlpire(skipTelemetry, startYear);
//        Building feline = makeFeline(skipTelemetry, startYear);
//        Building hogurity = makeHogurity(skipTelemetry, startYear);
//
//        return ModelData.builder()
//                .data(new TreeSet<>(Set.of(alpire, feline, hogurity)))
//                .build();
        return null;
    }

    private void applyData(ModelData data, CustomerData customerData) {
        CustomerUser customerUser = customerData.getUser();
        UUID ownerId = customerUser.getCustomerId().getId();

        Set<City> cities = mapToCities(data);
        for (City city : cities) {
            Asset cityAsset = createCity(city, ownerId);

            for (Region region : city.getRegions()) {
                Device regionDevice = createRegion(region, ownerId);
                this.tbRestClient.createRelation(RelationType.CONTAINS.getType(), cityAsset.getId(), regionDevice.getId());

                for (Consumer consumer : region.getConsumers()) {
                    Device consumerDevice = createConsumer(consumer, ownerId);
                    this.tbRestClient.createRelation(RelationType.CONTAINS.getType(), regionDevice.getId(), consumerDevice.getId());
                }
            }
            for (PumpStation pumpStation : city.getPumpStations()) {
                Device pumpStationDevice = createPumpStation(pumpStation, ownerId);
                this.tbRestClient.createRelation(RelationType.CONTAINS.getType(), cityAsset.getId(), pumpStationDevice.getId());
            }
        }
    }

    private void validateData(ModelData data) {
        Set<City> cities = mapToCities(data);

        Set<PumpStation> pumpStations = cities.stream()
                .map(City::getPumpStations)
                .flatMap(Collection::stream)
                .collect(Collectors.toSet());

        Set<Region> regions = cities.stream()
                .map(City::getRegions)
                .flatMap(Collection::stream)
                .collect(Collectors.toSet());

        Set<Consumer> consumers = regions.stream()
                .map(Region::getConsumers)
                .flatMap(Collection::stream)
                .collect(Collectors.toSet());

        Set<String> assets = cities
                .stream()
                .map(ModelEntity::getSystemName)
                .collect(Collectors.toSet());

        Set<String> devices = Sets.union(pumpStations, Sets.union(regions, consumers))
                .stream()
                .map(ModelEntity::getSystemName)
                .collect(Collectors.toSet());

        Set<Asset> badAssets = this.tbRestClient.getAllAssets()
                .stream()
                .filter(asset -> assets.contains(asset.getName()))
                .collect(Collectors.toSet());

        if (!badAssets.isEmpty()) {
            log.error("There are assets that already exists: {}", badAssets);
            throw new AssetAlreadyExistException(badAssets.iterator().next());
        }

        Set<Device> badDevices = this.tbRestClient.getAllDevices()
                .stream()
                .filter(device -> devices.contains(device.getName()))
                .collect(Collectors.toSet());

        if (!badDevices.isEmpty()) {
            log.error("There are devices that already exists: {}", badDevices);
            throw new DeviceAlreadyExistException(badDevices.iterator().next());
        }
    }

    private void deleteData(ModelData data) {
        Set<City> cities = mapToCities(data);

        Set<PumpStation> pumpStations = cities.stream()
                .map(City::getPumpStations)
                .flatMap(Collection::stream)
                .collect(Collectors.toSet());

        Set<Region> regions = cities.stream()
                .map(City::getRegions)
                .flatMap(Collection::stream)
                .collect(Collectors.toSet());

        Set<Consumer> consumers = regions.stream()
                .map(Region::getConsumers)
                .flatMap(Collection::stream)
                .collect(Collectors.toSet());

        Set<String> assets = cities
                .stream()
                .map(ModelEntity::getSystemName)
                .collect(Collectors.toSet());

        Set<String> devices = Sets.union(pumpStations, Sets.union(regions, consumers))
                .stream()
                .map(ModelEntity::getSystemName)
                .collect(Collectors.toSet());

        this.tbRestClient.getAllDevices()
                .stream()
                .filter(device -> devices.contains(device.getName()))
                .forEach(device -> this.tbRestClient.deleteDevice(device.getUuidId()));

        this.tbRestClient.getAllAssets()
                .stream()
                .filter(asset -> assets.contains(asset.getName()))
                .forEach(asset -> this.tbRestClient.deleteAsset(asset.getUuidId()));
    }


    private Asset createCity(City city, UUID ownerId) {
        Asset asset = tbRestClient.createAsset(city.getSystemName(), "WM City");
        tbRestClient.assignAssetToCustomer(ownerId, asset.getUuidId());

        Set<Attribute<?>> attributes = Set.of(
                new Attribute<>("population", city.getPopulation())
        );
        tbRestClient.setEntityAttributes(asset.getUuidId(), EntityType.ASSET, Attribute.Scope.SERVER_SCOPE, attributes);
        return asset;
    }

    private Device createRegion(Region region, UUID ownerId) {
        Device device = tbRestClient.createDevice(region.getSystemName(), "WM Region");
        DeviceCredentials deviceCredentials = tbRestClient.getDeviceCredentials(device.getUuidId());
        tbRestClient.assignDeviceToCustomer(ownerId, device.getUuidId());

        tbRestClient.pushTelemetry(deviceCredentials.getCredentialsId(), region.getFullConsumption());

        this.regionToIdMap.put(region, device.getUuidId());
        return device;
    }

    private Device createConsumer(Consumer consumer, UUID ownerId) {
        Device device = tbRestClient.createDevice(consumer.getSystemName(), "WM Consumer");
        DeviceCredentials deviceCredentials = tbRestClient.getDeviceCredentials(device.getUuidId());
        tbRestClient.assignDeviceToCustomer(ownerId, device.getUuidId());

        Set<Attribute<?>> attributes = Set.of(
                new Attribute<>("type", consumer.getType())
        );
        tbRestClient.setEntityAttributes(device.getUuidId(), EntityType.DEVICE, Attribute.Scope.SERVER_SCOPE, attributes);

        tbRestClient.pushTelemetry(deviceCredentials.getCredentialsId(), consumer.getConsumption());

        this.consumerToIdMap.put(consumer, device.getUuidId());
        return device;
    }

    private Device createPumpStation(PumpStation pumpStation, UUID ownerId) {
        Device device = tbRestClient.createDevice(pumpStation.getSystemName(), "WM Pump Station");
        DeviceCredentials deviceCredentials = tbRestClient.getDeviceCredentials(device.getUuidId());
        tbRestClient.assignDeviceToCustomer(ownerId, device.getUuidId());

        tbRestClient.pushTelemetry(deviceCredentials.getCredentialsId(), pumpStation.getProvided());

        this.pumpStationToIdMap.put(pumpStation, device.getUuidId());
        return device;
    }
}

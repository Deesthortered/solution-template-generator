package org.thingsboard.trendz.generator.solution.watermetering;

import com.google.common.collect.Sets;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.thingsboard.server.common.data.Customer;
import org.thingsboard.server.common.data.Device;
import org.thingsboard.server.common.data.EntityType;
import org.thingsboard.server.common.data.asset.Asset;
import org.thingsboard.server.common.data.group.EntityGroup;
import org.thingsboard.server.common.data.id.CustomerId;
import org.thingsboard.server.common.data.rule.NodeConnectionInfo;
import org.thingsboard.server.common.data.rule.RuleChain;
import org.thingsboard.server.common.data.rule.RuleChainMetaData;
import org.thingsboard.server.common.data.rule.RuleNode;
import org.thingsboard.server.common.data.security.DeviceCredentials;
import org.thingsboard.trendz.generator.exception.AssetAlreadyExistException;
import org.thingsboard.trendz.generator.exception.CustomerAlreadyExistException;
import org.thingsboard.trendz.generator.exception.DeviceAlreadyExistException;
import org.thingsboard.trendz.generator.exception.RuleChainAlreadyExistException;
import org.thingsboard.trendz.generator.exception.SolutionValidationException;
import org.thingsboard.trendz.generator.model.ModelData;
import org.thingsboard.trendz.generator.model.ModelEntity;
import org.thingsboard.trendz.generator.model.anomaly.AnomalyInfo;
import org.thingsboard.trendz.generator.model.anomaly.AnomalyType;
import org.thingsboard.trendz.generator.model.tb.Attribute;
import org.thingsboard.trendz.generator.model.tb.CustomerData;
import org.thingsboard.trendz.generator.model.tb.CustomerUser;
import org.thingsboard.trendz.generator.model.tb.RelationType;
import org.thingsboard.trendz.generator.model.tb.RuleNodeAdditionalInfo;
import org.thingsboard.trendz.generator.model.tb.Telemetry;
import org.thingsboard.trendz.generator.model.tb.Timestamp;
import org.thingsboard.trendz.generator.service.anomaly.AnomalyService;
import org.thingsboard.trendz.generator.service.dashboard.DashboardService;
import org.thingsboard.trendz.generator.service.rest.TbRestClient;
import org.thingsboard.trendz.generator.service.roolchain.RuleChainBuildingService;
import org.thingsboard.trendz.generator.solution.SolutionTemplateGenerator;
import org.thingsboard.trendz.generator.solution.watermetering.configuration.CityConfiguration;
import org.thingsboard.trendz.generator.solution.watermetering.configuration.ConsumerConfiguration;
import org.thingsboard.trendz.generator.solution.watermetering.configuration.PumpStationConfiguration;
import org.thingsboard.trendz.generator.solution.watermetering.configuration.RegionConfiguration;
import org.thingsboard.trendz.generator.solution.watermetering.model.City;
import org.thingsboard.trendz.generator.solution.watermetering.model.Consumer;
import org.thingsboard.trendz.generator.solution.watermetering.model.ConsumerType;
import org.thingsboard.trendz.generator.solution.watermetering.model.PumpStation;
import org.thingsboard.trendz.generator.solution.watermetering.model.Region;
import org.thingsboard.trendz.generator.utils.DateTimeUtils;
import org.thingsboard.trendz.generator.utils.MySortedSet;
import org.thingsboard.trendz.generator.utils.RandomUtils;

import java.time.DayOfWeek;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.stream.Collectors;

@Slf4j
@Service
public class WaterMeteringSolution implements SolutionTemplateGenerator {

    private static final String CUSTOMER_TITLE = "Water Metering Customer";
    private static final String CUSTOMER_USER_EMAIL = "watermetering@thingsboard.io";
    private static final String CUSTOMER_USER_PASSWORD = "password";
    private static final String CUSTOMER_USER_FIRST_NAME = "Water Metering Solution";
    private static final String CUSTOMER_USER_LAST_NAME = "";

    private static final String ASSET_GROUP_NAME = "Water Metering Asset Group";
    private static final String DEVICE_GROUP_NAME = "Water Metering Device Group";
    private static final String RULE_CHAIN_NAME = "Water Metering Rule Chain";

    private final TbRestClient tbRestClient;
    private final AnomalyService anomalyService;
    private final RuleChainBuildingService ruleChainBuildingService;
    private final DashboardService dashboardService;

    private final Map<Consumer, UUID> consumerToIdMap = new HashMap<>();

    @Autowired
    public WaterMeteringSolution(
            TbRestClient tbRestClient,
            AnomalyService anomalyService,
            RuleChainBuildingService ruleChainBuildingService,
            DashboardService dashboardService
    ) {
        this.tbRestClient = tbRestClient;
        this.anomalyService = anomalyService;
        this.ruleChainBuildingService = ruleChainBuildingService;
        this.dashboardService = dashboardService;
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

            if (!tbRestClient.isPe()) {
                dashboardService.validateDashboardItems(getSolutionName(), null);
                ModelData data = makeData(true, ZonedDateTime.now());
                validateData(data);
            }

            log.info("Water Metering Solution - validation is completed!");
        } catch (Exception e) {
            throw new SolutionValidationException(getSolutionName(), e);
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
            dashboardService.createDashboardItems(getSolutionName(), customerData.getCustomer().getId());

            checkRandomStability();
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

            if (!tbRestClient.isPe()) {
                dashboardService.deleteDashboardItems(getSolutionName(), null);
                ModelData data = makeData(true, ZonedDateTime.now());
                deleteData(data);
            }

            log.info("Water Metering Solution - removal is completed!");
        } catch (Exception e) {
            log.error("Water Metering Solution removal was failed, skipping...", e);
        }
    }


    private void checkRandomStability() {
        int count = 5;
        List<Long> actual = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            actual.add(RandomUtils.getRandomNumber(0, 100));
        }

        List<Long> expected = List.of(38L, 67L, 29L, 81L, 92L);

        for (int i = 0; i < count; i++) {
            if (Long.compare(expected.get(i), actual.get(i)) != 0) {
                log.warn("Random Stability check if failed");
                break;
            }
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
        if (tbRestClient.isPe()) {
            tbRestClient.setCustomerUserToCustomerAdministratorsGroup(customer, customerUser);
        }

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

            int consumerCounter = 0;
            for (City city : cities) {

                for (Region region : city.getRegions()) {

                    for (Consumer consumer : region.getConsumers()) {
                        UUID consumerId = this.consumerToIdMap.get(consumer);

                        RuleNode generatorNode = ruleChainBuildingService.createGeneratorNode(
                                getSolutionName(),
                                consumer.getSystemName() + ": generate node",
                                consumerId,
                                getConsumerConsumptionFileName(consumer.getType()),
                                getPositionX(consumerCounter, 0),
                                getPositionY(consumerCounter, 0)
                        );
                        RuleNode changeOriginatorNode1 = ruleChainBuildingService.createChangeOriginatorNode(
                                region.getSystemName() + ": change originator node (" + consumer.getSystemName() + ")",
                                region.getSystemName(),
                                EntityType.DEVICE,
                                getPositionX(consumerCounter, 1),
                                getPositionY(consumerCounter, 1)
                        );
                        RuleNode latestTelemetryLoadNode1 = ruleChainBuildingService.createLatestTelemetryLoadNode(
                                region.getSystemName() + ": latest telemetry node (" + consumer.getSystemName() + ")",
                                "full_consumption",
                                getPositionX(consumerCounter, 2),
                                getPositionY(consumerCounter, 2)
                        );
                        RuleNode transformationNode2 = ruleChainBuildingService.createTransformationNode(
                                getSolutionName(),
                                region.getSystemName() + ": transform node (" + consumer.getSystemName() + ")",
                                "node_tr1.js",
                                getPositionX(consumerCounter, 3),
                                getPositionY(consumerCounter, 3)
                        );
                        RuleNode changeOriginatorNode2 = ruleChainBuildingService.createChangeOriginatorNode(
                                region.getSystemName() + " Pump Station: change originator node (" + consumer.getSystemName() + ")",
                                region.getSystemName() + " Pump Station",
                                EntityType.DEVICE,
                                getPositionX(consumerCounter, 4),
                                getPositionY(consumerCounter, 4)
                        );
                        RuleNode transformationNode3 = ruleChainBuildingService.createTransformationNode(
                                getSolutionName(),
                                region.getSystemName() + " Pump Station: transform node (" + consumer.getSystemName() + ")",
                                "node_tr2.js",
                                getPositionX(consumerCounter, 5),
                                getPositionY(consumerCounter, 5)
                        );

                        RuleNode saveNode = ruleChainBuildingService.createSaveNode(
                                region.getSystemName() + ": save node",
                                getPositionX(consumerCounter, 6),
                                getPositionY(consumerCounter, 6)
                        );

                        int index = nodes.size();

                        nodes.add(generatorNode);
                        nodes.add(changeOriginatorNode1);
                        nodes.add(latestTelemetryLoadNode1);
                        nodes.add(transformationNode2);
                        nodes.add(changeOriginatorNode2);
                        nodes.add(transformationNode3);
                        nodes.add(saveNode);

                        connections.add(ruleChainBuildingService.createRuleConnection(index + 0, index + 1));
                        connections.add(ruleChainBuildingService.createRuleConnection(index + 1, index + 2));
                        connections.add(ruleChainBuildingService.createRuleConnection(index + 2, index + 3));
                        connections.add(ruleChainBuildingService.createRuleConnection(index + 3, index + 4));
                        connections.add(ruleChainBuildingService.createRuleConnection(index + 4, index + 5));
                        connections.add(ruleChainBuildingService.createRuleConnection(index + 5, index + 6));
                        connections.add(ruleChainBuildingService.createRuleConnection(index + 0, index + 6));
                        connections.add(ruleChainBuildingService.createRuleConnection(index + 3, index + 6));

                        consumerCounter++;
                        RuleChainMetaData savedMetaData = this.tbRestClient.saveRuleChainMetadata(metaData);
//                        TimeUnit.SECONDS.sleep(10);
//                        log.warn("Sleeping for solving race condition problem!");
                    }
                }
            }
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
        int order = 0;
        Set<CityConfiguration> cityConfigurations = MySortedSet.of(
                CityConfiguration.builder()
                        .order(order++)
                        .name("London")
                        .label("Label for City London")
                        .population(4_000_000)
                        .regionConfigurations(MySortedSet.of(
                                RegionConfiguration.builder()
                                        .order(order++)
                                        .startYear(startYear)
                                        .name("Dulwich")
                                        .label("Label for Dulwich, London")
                                        .anomalies(Collections.emptySet())
                                        .consumerConfigurations(MySortedSet.of(
                                                new ConsumerConfiguration(order++, "1", ConsumerType.HSH, Collections.emptySet()),
                                                new ConsumerConfiguration(order++, "2", ConsumerType.HSH, MySortedSet.of(
                                                        AnomalyInfo.builder()
                                                                .startDate(startYear.withMonth(3).withDayOfMonth(15))
                                                                .endDate(startYear.withMonth(3).withDayOfMonth(22))
                                                                .type(AnomalyType.SET_VALUES)
                                                                .settingValue(0)
                                                                .build()
                                                )),
                                                new ConsumerConfiguration(order++, "3", ConsumerType.HSH, Collections.emptySet()),
                                                new ConsumerConfiguration(order++, "4", ConsumerType.HSH, Collections.emptySet()),
                                                new ConsumerConfiguration(order++, "1", ConsumerType.GOV, Collections.emptySet()),
                                                new ConsumerConfiguration(order++, "1", ConsumerType.IND, Collections.emptySet())
                                        ))
                                        .pumpStationConfiguration(
                                                PumpStationConfiguration.builder()
                                                        .anomalies(
                                                                ((Supplier<Set<AnomalyInfo>>) () -> {
                                                                    Set<AnomalyInfo> result = MySortedSet.of();
                                                                    ZonedDateTime from = startYear.withMonth(5).withDayOfMonth(1);
                                                                    ZonedDateTime to = startYear.withMonth(5).withDayOfMonth(21);
                                                                    ZonedDateTime iteratedDate = from;
                                                                    while (iteratedDate.isBefore(to)) {
                                                                        ZonedDateTime start = iteratedDate.withHour(3);
                                                                        ZonedDateTime end = iteratedDate.withHour(8);

                                                                        AnomalyInfo anomalyInfo = AnomalyInfo.builder()
                                                                                .startDate(start)
                                                                                .endDate(end)
                                                                                .type(AnomalyType.SHIFTED_DATA)
                                                                                .shiftValue(200)
                                                                                .coefficient(1.3)
                                                                                .noiseAmplitude(100)
                                                                                .settingValue(0)
                                                                                .build();

                                                                        result.add(anomalyInfo);
                                                                        iteratedDate = iteratedDate.plus(1, ChronoUnit.DAYS);
                                                                    }
                                                                    return result;
                                                                }).get())
                                                        .build()
                                        )
                                        .build(),
                                RegionConfiguration.builder()
                                        .order(order++)
                                        .startYear(startYear)
                                        .name("Wimbledon")
                                        .label("Label for Wimbledon, London")
                                        .anomalies(Collections.emptySet())
                                        .consumerConfigurations(MySortedSet.of(
                                                new ConsumerConfiguration(order++, "1", ConsumerType.GOV, MySortedSet.of(
                                                        AnomalyInfo.builder()
                                                                .startDate(startYear.withMonth(1).withDayOfMonth(5))
                                                                .endDate(startYear.withMonth(1).withDayOfMonth(10))
                                                                .type(AnomalyType.SET_VALUES)
                                                                .settingValue(0)
                                                                .build()
                                                )),
                                                new ConsumerConfiguration(order++, "1", ConsumerType.IND, Collections.emptySet())
                                        ))
                                        .pumpStationConfiguration(
                                                PumpStationConfiguration.builder()
                                                        .anomalies(Collections.emptySet())
                                                        .build()
                                        )
                                        .build()
                        ))
                        .build(),

                CityConfiguration.builder()
                        .order(order++)
                        .name("Edinburgh")
                        .label("Label for City Edinburgh")
                        .population(300_000)
                        .regionConfigurations(MySortedSet.of(
                                RegionConfiguration.builder()
                                        .order(order++)
                                        .startYear(startYear)
                                        .name("Leith")
                                        .label("Label for Leith, Edinburgh")
                                        .anomalies(MySortedSet.of(
                                                AnomalyInfo.builder()
                                                        .startDate(startYear.withMonth(2).withDayOfMonth(10))
                                                        .endDate(startYear.withMonth(4).withDayOfMonth(10))
                                                        .type(AnomalyType.SHIFTED_DATA)
                                                        .shiftValue(100)
                                                        .coefficient(1.2)
                                                        .noiseAmplitude(50)
                                                        .settingValue(0)
                                                        .build(),
                                                AnomalyInfo.builder()
                                                        .startDate(startYear.withMonth(6).withDayOfMonth(5))
                                                        .endDate(startYear.withMonth(6).withDayOfMonth(25))
                                                        .type(AnomalyType.SHIFTED_DATA)
                                                        .shiftValue(100)
                                                        .coefficient(1.2)
                                                        .noiseAmplitude(50)
                                                        .settingValue(0)
                                                        .build()
                                        ))
                                        .consumerConfigurations(MySortedSet.of(
                                                new ConsumerConfiguration(order++, "1", ConsumerType.HSH, Collections.emptySet()),
                                                new ConsumerConfiguration(order++, "1", ConsumerType.GOV, Collections.emptySet()),
                                                new ConsumerConfiguration(order++, "1", ConsumerType.IND, Collections.emptySet())
                                        ))
                                        .pumpStationConfiguration(
                                                PumpStationConfiguration.builder()
                                                        .anomalies(Collections.emptySet())
                                                        .build()
                                        )
                                        .build(),
                                RegionConfiguration.builder()
                                        .order(order++)
                                        .startYear(startYear)
                                        .name("Stockbridge")
                                        .label("Label for Stockbridge, Edinburgh")
                                        .anomalies(MySortedSet.of(
                                                AnomalyInfo.builder()
                                                        .startDate(startYear.withMonth(1).withDayOfMonth(5))
                                                        .endDate(startYear.withMonth(1).withDayOfMonth(16))
                                                        .type(AnomalyType.SHIFTED_DATA)
                                                        .shiftValue(100)
                                                        .coefficient(1.2)
                                                        .noiseAmplitude(50)
                                                        .settingValue(0)
                                                        .build(),
                                                AnomalyInfo.builder()
                                                        .startDate(startYear.withMonth(3).withDayOfMonth(1))
                                                        .endDate(startYear.withMonth(3).withDayOfMonth(14))
                                                        .type(AnomalyType.SHIFTED_DATA)
                                                        .shiftValue(100)
                                                        .coefficient(1.3)
                                                        .noiseAmplitude(50)
                                                        .settingValue(0)
                                                        .build()
                                        ))
                                        .consumerConfigurations(MySortedSet.of(
                                                new ConsumerConfiguration(order++, "1", ConsumerType.HSH, Collections.emptySet()),
                                                new ConsumerConfiguration(order++, "1", ConsumerType.GOV, Collections.emptySet()),
                                                new ConsumerConfiguration(order++, "1", ConsumerType.IND, Collections.emptySet())
                                        ))
                                        .pumpStationConfiguration(
                                                PumpStationConfiguration.builder()
                                                        .anomalies(Collections.emptySet())
                                                        .build()
                                        )
                                        .build()
                        ))
                        .build()
        );

        Set<ModelEntity> cities = cityConfigurations.stream()
                .map(configuration -> makeCityByConfiguration(configuration, skipTelemetry))
                .collect(Collectors.toCollection(TreeSet::new));

        return new ModelData(cities);
    }

    private void applyData(ModelData data, CustomerData customerData) {
        CustomerUser customerUser = customerData.getUser();
        UUID ownerId = customerUser.getCustomerId().getId();

        UUID assetGroupId = null;
        UUID deviceGroupId = null;
        if (tbRestClient.isPe()) {
            EntityGroup assetGroup = tbRestClient.createEntityGroup(ASSET_GROUP_NAME, EntityType.ASSET, ownerId, true);
            EntityGroup deviceGroup = tbRestClient.createEntityGroup(DEVICE_GROUP_NAME, EntityType.DEVICE, ownerId, true);
            assetGroupId = assetGroup.getUuidId();
            deviceGroupId = deviceGroup.getUuidId();
        }

        Set<City> cities = mapToCities(data);
        for (City city : cities) {
            Asset cityAsset = createCity(city, ownerId, assetGroupId);

            for (Region region : city.getRegions()) {
                Device regionDevice = createRegion(region, ownerId, deviceGroupId);
                this.tbRestClient.createRelation(RelationType.CONTAINS.getType(), cityAsset.getId(), regionDevice.getId());

                for (Consumer consumer : region.getConsumers()) {
                    Device consumerDevice = createConsumer(consumer, ownerId, deviceGroupId);
                    this.tbRestClient.createRelation(RelationType.CONTAINS.getType(), regionDevice.getId(), consumerDevice.getId());
                }
            }
            for (PumpStation pumpStation : city.getPumpStations()) {
                Device pumpStationDevice = createPumpStation(pumpStation, ownerId, deviceGroupId);
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


    private Asset createCity(City city, UUID ownerId, UUID assetGroupId) {
        Asset asset;
        if (tbRestClient.isPe()) {
            asset = tbRestClient.createAsset(city.getSystemName(), city.entityType(), new CustomerId(ownerId));
            tbRestClient.addEntitiesToTheGroup(assetGroupId, Set.of(asset.getUuidId()));
        } else {
            asset = tbRestClient.createAsset(city.getSystemName(), city.entityType());
            tbRestClient.assignAssetToCustomer(ownerId, asset.getUuidId());
        }

        Set<Attribute<?>> attributes = Set.of(
                new Attribute<>("population", city.getPopulation())
        );
        tbRestClient.setEntityAttributes(asset.getUuidId(), EntityType.ASSET, Attribute.Scope.SERVER_SCOPE, attributes);
        return asset;
    }

    private Device createRegion(Region region, UUID ownerId, UUID deviceGroupId) {
        Device device;
        if (tbRestClient.isPe()) {
            device = tbRestClient.createDevice(region.getSystemName(), region.entityType(), new CustomerId(ownerId));
            tbRestClient.addEntitiesToTheGroup(deviceGroupId, Set.of(device.getUuidId()));
        } else {
            device = tbRestClient.createDevice(region.getSystemName(), region.entityType());
            tbRestClient.assignDeviceToCustomer(ownerId, device.getUuidId());
        }
        DeviceCredentials deviceCredentials = tbRestClient.getDeviceCredentials(device.getUuidId());

        tbRestClient.pushTelemetry(deviceCredentials.getCredentialsId(), region.getFullConsumption());

        return device;
    }

    private Device createConsumer(Consumer consumer, UUID ownerId, UUID deviceGroupId) {
        Device device;
        if (tbRestClient.isPe()) {
            device = tbRestClient.createDevice(consumer.getSystemName(), consumer.entityType(), new CustomerId(ownerId));
            tbRestClient.addEntitiesToTheGroup(deviceGroupId, Set.of(device.getUuidId()));
        } else {
            device = tbRestClient.createDevice(consumer.getSystemName(), consumer.entityType());
            tbRestClient.assignDeviceToCustomer(ownerId, device.getUuidId());
        }
        DeviceCredentials deviceCredentials = tbRestClient.getDeviceCredentials(device.getUuidId());

        Set<Attribute<?>> attributes = Set.of(
                new Attribute<>("type", consumer.getType())
        );
        tbRestClient.setEntityAttributes(device.getUuidId(), EntityType.DEVICE, Attribute.Scope.SERVER_SCOPE, attributes);

        tbRestClient.pushTelemetry(deviceCredentials.getCredentialsId(), consumer.getConsumption());

        this.consumerToIdMap.put(consumer, device.getUuidId());
        return device;
    }

    private Device createPumpStation(PumpStation pumpStation, UUID ownerId, UUID deviceGroupId) {
        Device device;
        if (tbRestClient.isPe()) {
            device = tbRestClient.createDevice(pumpStation.getSystemName(), pumpStation.entityType(), new CustomerId(ownerId));
            tbRestClient.addEntitiesToTheGroup(deviceGroupId, Set.of(device.getUuidId()));
        } else {
            device = tbRestClient.createDevice(pumpStation.getSystemName(), pumpStation.entityType());
            tbRestClient.assignDeviceToCustomer(ownerId, device.getUuidId());
        }
        DeviceCredentials deviceCredentials = tbRestClient.getDeviceCredentials(device.getUuidId());

        tbRestClient.pushTelemetry(deviceCredentials.getCredentialsId(), pumpStation.getProvided());

        return device;
    }


    private City makeCityByConfiguration(CityConfiguration cityConfiguration, boolean skipTelemetry) {
        Set<Region> regions = MySortedSet.of();
        Set<PumpStation> pumpStations = MySortedSet.of();
        for (RegionConfiguration regionConfiguration : cityConfiguration.getRegionConfigurations()) {
            Region region = makeRegionByConfiguration(regionConfiguration, skipTelemetry);
            regions.add(region);

            PumpStationConfiguration pumpStationConfiguration = regionConfiguration.getPumpStationConfiguration();
            PumpStation pumpStation = makePumpStationByRegion(pumpStationConfiguration, region);
            pumpStations.add(pumpStation);
        }

        return City.builder()
                .systemName(cityConfiguration.getName())
                .systemLabel(cityConfiguration.getLabel())
                .population(cityConfiguration.getPopulation())
                .regions(regions)
                .pumpStations(pumpStations)
                .build();
    }

    private Region makeRegionByConfiguration(RegionConfiguration regionConfiguration, boolean skipTelemetry) {
        Set<Consumer> consumers = MySortedSet.of();
        for (ConsumerConfiguration consumerConfiguration : regionConfiguration.getConsumerConfigurations()) {
            Consumer consumer = makeConsumerByConfiguration(regionConfiguration, consumerConfiguration, skipTelemetry);
            consumers.add(consumer);
        }

        Set<Telemetry<Long>> consumptionSet = consumers.stream()
                .map(Consumer::getConsumption)
                .collect(Collectors.toSet());

        Telemetry<Long> fullConsumption = createTelemetryRegionFullConsumption(consumptionSet, skipTelemetry);
        this.anomalyService.applyAnomaly(fullConsumption, regionConfiguration.getAnomalies());

        return Region.builder()
                .systemName(regionConfiguration.getName())
                .systemLabel(regionConfiguration.getLabel())
                .fullConsumption(fullConsumption)
                .consumers(consumers)
                .build();
    }

    private Consumer makeConsumerByConfiguration(RegionConfiguration regionConfiguration, ConsumerConfiguration consumerConfiguration, boolean skipTelemetry) {
        ZonedDateTime startYear = regionConfiguration.getStartYear();
        String regionName = regionConfiguration.getName();
        String index = consumerConfiguration.getIndex();
        ConsumerType type = consumerConfiguration.getType();

        String consumerName = regionName + " " + type.name() + "_" + index;
        Telemetry<Long> consumption = createTelemetryConsumerConsumption(consumerConfiguration, startYear, skipTelemetry);
        this.anomalyService.applyAnomaly(consumption, consumerConfiguration.getAnomalies());

        return Consumer.builder()
                .systemName(consumerName)
                .systemLabel("Label for consumer " + consumerName)
                .type(type)
                .consumption(consumption)
                .build();
    }

    private PumpStation makePumpStationByRegion(PumpStationConfiguration pumpStationConfiguration, Region region) {
        String name = region.getSystemName() + " Pump Station";
        Telemetry<Long> provided = new Telemetry<>("provided", region.getFullConsumption().getPoints());
        this.anomalyService.applyAnomaly(provided, pumpStationConfiguration.getAnomalies());

        return PumpStation.builder()
                .systemName(name)
                .systemLabel("Label for " + name)
                .provided(provided)
                .build();
    }


    private Telemetry<Long> createTelemetryConsumerConsumption(ConsumerConfiguration consumerConfiguration, ZonedDateTime startYear, boolean skipTelemetry) {
        if (skipTelemetry) {
            return new Telemetry<>("skip");
        }

        Telemetry<Long> result = new Telemetry<>("consumption");
        ConsumerType type = consumerConfiguration.getType();

        long now = System.currentTimeMillis();
        ZonedDateTime startDate = startYear.truncatedTo(ChronoUnit.HOURS);
        ZonedDateTime nowDate = DateTimeUtils.fromTs(now).truncatedTo(ChronoUnit.HOURS);

        Pair<Long, Long> intervalValues = getIntervalValuesRangeByConsumerType(consumerConfiguration.getType());
        int fullInterval = 365;
        long minValue = intervalValues.getLeft();
        long maxValue = intervalValues.getRight();
        List<Pair<Long, Long>> intervals = generateRandomRanges(fullInterval, fullInterval / 40, minValue, maxValue);

        ZonedDateTime iteratedDate = startDate;
        while (iteratedDate.isBefore(nowDate)) {
            long iteratedTs = DateTimeUtils.toTs(iteratedDate);
            int hour = iteratedDate.getHour();
            DayOfWeek dayOfWeek = iteratedDate.getDayOfWeek();
            int dayOfYear = iteratedDate.getDayOfYear();

            long dailyNoiseAmplitude = 60;
            int timezoneShift = 2;
            hour = (hour + timezoneShift + 24) % 24;

            long consumption = 0;
            consumption += getHourConsumerConsumption(type, dayOfWeek, hour);
            consumption += getModificationByDayOfYear(dayOfYear, intervals);
            consumption += RandomUtils.getRandomNumber(-dailyNoiseAmplitude, dailyNoiseAmplitude);

            long value = Math.max(0, consumption);

            result.add(iteratedTs, value);
            iteratedDate = iteratedDate.plus(1, ChronoUnit.HOURS);
        }

        return result;
    }

    private Telemetry<Long> createTelemetryRegionFullConsumption(Set<Telemetry<Long>> consumerConsumptionOfRegion, boolean skipTelemetry) {
        if (skipTelemetry) {
            return new Telemetry<>("skip");
        }

        Set<Telemetry.Point<Long>> summedConsumption = consumerConsumptionOfRegion.stream()
                .map(Telemetry::getPoints)
                .flatMap(Collection::stream)
                .collect(Collectors.groupingBy(
                        Telemetry.Point::getTs,
                        Collectors.mapping(Telemetry.Point::getValue, Collectors.toList()))
                )
                .entrySet()
                .stream()
                .map(entry -> {
                    Timestamp ts = entry.getKey();
                    long sum = entry.getValue()
                            .stream()
                            .mapToLong(l -> l)
                            .sum();

                    return new Telemetry.Point<>(ts, sum);
                })
                .collect(Collectors.toCollection(TreeSet::new));

        return new Telemetry<>("full_consumption", summedConsumption);
    }


    private long getModificationByDayOfYear(int dayOfYear, List<Pair<Long, Long>> intervals) {
        Pair<Long, Long> prev = Pair.of(0L, 0L);
        for (Pair<Long, Long> interval : intervals) {
            if (dayOfYear <= interval.getKey()) {
                long x1 = prev.getKey();
                long x2 = interval.getKey();
                long y1 = prev.getValue();
                long y2 = interval.getValue();
                long x = dayOfYear;
                return ((x - x1) * (y2 - y1)) / (x2 - x1) + y1;
            }
            prev = interval;
        }
        throw new IllegalStateException("Can not assign day of year to corresponding interval");
    }

    private List<Pair<Long, Long>> generateRandomRanges(int interval, int count, long minValue, long maxValue) {
        List<Pair<Long, Long>> result = new ArrayList<>(count);
        int minLength = interval / (count * 10);
        int maxLength = interval / count;
        long used = 0;

        while (used < interval) {
            long length = RandomUtils.getRandomNumber(minLength, maxLength);
            long value = RandomUtils.getRandomNumber(minValue, maxValue);
            used += length;
            result.add(Pair.of(used, value));
        }

        return result;
    }

    private Pair<Long, Long> getIntervalValuesRangeByConsumerType(ConsumerType type) {
        switch (type) {
            case HSH:
                return Pair.of(0L, 100L);
            case GOV:
                return Pair.of(-30L, 100L);
            case IND:
                return Pair.of(-100L, 100L);
            default:
                throw new IllegalArgumentException("Unsupported consumer type: " + type);
        }
    }


    private long getHourConsumerConsumption(ConsumerType type, DayOfWeek day, int hour) {
        if (day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY) {
            switch (type) {
                case HSH:
                    return getHourHshConsumptionWeekend(hour);
                case GOV:
                    return getHourGovConsumptionWeekend(hour);
                case IND:
                    return getHourIndConsumptionWeekend(hour);
                default:
                    throw new IllegalArgumentException("Unsupported consumer type = " + type);
            }
        }

        switch (type) {
            case HSH:
                return getHourHshConsumption(hour);
            case GOV:
                return getHourGovConsumption(hour);
            case IND:
                return getHourIndConsumption(hour);
            default:
                throw new IllegalArgumentException("Unsupported consumer type = " + type);
        }
    }

    private long getHourHshConsumption(int hour) {
        switch (hour) {
            case 0:
            case 1:
                return 0;
            case 2:
            case 3:
            case 4:
            case 5:
            case 6:
                return 10;
            case 7:
            case 8:
            case 9:
            case 10:
                return 100;
            case 11:
            case 12:
            case 13:
            case 14:
            case 15:
            case 16:
                return 10;
            case 17:
            case 18:
            case 19:
            case 20:
            case 21:
                return 100;
            case 22:
            case 23:
                return 0;
            default:
                throw new IllegalArgumentException("Unsupported hour = " + hour);
        }
    }

    private long getHourGovConsumption(int hour) {
        switch (hour) {
            case 0:
            case 1:
            case 2:
            case 3:
            case 4:
            case 5:
            case 6:
                return 0;
            case 7:
            case 8:
            case 9:
            case 10:
            case 11:
            case 12:
            case 13:
            case 14:
            case 15:
            case 16:
            case 17:
            case 18:
            case 19:
            case 20:
                return 50;
            case 21:
            case 22:
            case 23:
                return 0;
            default:
                throw new IllegalArgumentException("Unsupported hour = " + hour);
        }
    }

    private long getHourIndConsumption(int hour) {
        switch (hour) {
            case 0:
            case 1:
            case 2:
            case 3:
            case 4:
            case 5:
                return 10;
            case 6:
            case 7:
            case 8:
            case 9:
            case 10:
            case 11:
            case 12:
            case 13:
            case 14:
            case 15:
            case 16:
            case 17:
            case 18:
            case 19:
                return 500;
            case 20:
            case 21:
            case 22:
            case 23:
                return 10;
            default:
                throw new IllegalArgumentException("Unsupported hour = " + hour);
        }
    }

    private long getHourHshConsumptionWeekend(int hour) {
        switch (hour) {
            case 0:
            case 1:
            case 2:
            case 3:
            case 4:
            case 5:
            case 6:
            case 7:
                return 0;
            case 8:
            case 9:
            case 10:
            case 11:
            case 12:
            case 13:
            case 14:
            case 15:
            case 16:
            case 17:
            case 18:
            case 19:
            case 20:
            case 21:
                return 10;
            case 22:
            case 23:
                return 0;
            default:
                throw new IllegalArgumentException("Unsupported hour = " + hour);
        }
    }

    private long getHourGovConsumptionWeekend(int hour) {
        switch (hour) {
            case 0:
            case 1:
            case 2:
            case 3:
            case 4:
            case 5:
            case 6:
            case 7:
            case 8:
            case 9:
            case 10:
            case 11:
            case 12:
            case 13:
            case 14:
            case 15:
            case 16:
            case 17:
            case 18:
            case 19:
            case 20:
            case 21:
            case 22:
            case 23:
                return 0;
            default:
                throw new IllegalArgumentException("Unsupported hour = " + hour);
        }
    }

    private long getHourIndConsumptionWeekend(int hour) {
        switch (hour) {
            case 0:
            case 1:
            case 2:
            case 3:
            case 4:
            case 5:
                return 10;
            case 6:
            case 7:
            case 8:
            case 9:
            case 10:
            case 11:
            case 12:
            case 13:
            case 14:
            case 15:
            case 16:
            case 17:
            case 18:
            case 19:
                return 500;
            case 20:
            case 21:
            case 22:
            case 23:
                return 10;
            default:
                throw new IllegalArgumentException("Unsupported hour = " + hour);
        }
    }


    private double getPositionX(int consumerCounter, int i) {
        return 70 + RuleNodeAdditionalInfo.CELL_SIZE * i * 11;
    }

    private double getPositionY(int consumerCounter, int i) {
        int shift = 75;
        if (i == 0 || i == 6) {
            shift = 0;
        }
        return 300 + RuleNodeAdditionalInfo.CELL_SIZE * consumerCounter * 6 + shift;
    }

    private String getConsumerConsumptionFileName(ConsumerType type) {
        switch (type) {
            case HSH:
                return "consumer_consumption_hsh.js";
            case IND:
                return "consumer_consumption_ind.js";
            case GOV:
                return "consumer_consumption_gov.js";
            default:
                throw new IllegalArgumentException("Unsupported type: " + type);
        }
    }
}

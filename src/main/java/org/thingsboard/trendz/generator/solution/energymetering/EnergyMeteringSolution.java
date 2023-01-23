package org.thingsboard.trendz.generator.solution.energymetering;

import com.google.common.collect.Sets;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.thingsboard.rule.engine.api.NodeConfiguration;
import org.thingsboard.rule.engine.debug.TbMsgGeneratorNode;
import org.thingsboard.rule.engine.debug.TbMsgGeneratorNodeConfiguration;
import org.thingsboard.rule.engine.metadata.TbGetAttributesNode;
import org.thingsboard.rule.engine.metadata.TbGetAttributesNodeConfiguration;
import org.thingsboard.rule.engine.telemetry.TbMsgTimeseriesNode;
import org.thingsboard.rule.engine.telemetry.TbMsgTimeseriesNodeConfiguration;
import org.thingsboard.rule.engine.transform.TbTransformMsgNode;
import org.thingsboard.rule.engine.transform.TbTransformMsgNodeConfiguration;
import org.thingsboard.server.common.data.Customer;
import org.thingsboard.server.common.data.Device;
import org.thingsboard.server.common.data.EntityType;
import org.thingsboard.server.common.data.asset.Asset;
import org.thingsboard.server.common.data.rule.NodeConnectionInfo;
import org.thingsboard.server.common.data.rule.RuleChain;
import org.thingsboard.server.common.data.rule.RuleChainMetaData;
import org.thingsboard.server.common.data.rule.RuleNode;
import org.thingsboard.server.common.data.script.ScriptLanguage;
import org.thingsboard.server.common.data.security.DeviceCredentials;
import org.thingsboard.trendz.generator.exception.AssetAlreadyExistException;
import org.thingsboard.trendz.generator.exception.CustomerAlreadyExistException;
import org.thingsboard.trendz.generator.exception.DeviceAlreadyExistException;
import org.thingsboard.trendz.generator.exception.RuleChainAlreadyExistException;
import org.thingsboard.trendz.generator.model.ModelData;
import org.thingsboard.trendz.generator.model.ModelEntity;
import org.thingsboard.trendz.generator.model.anomaly.AnomalyInfo;
import org.thingsboard.trendz.generator.model.anomaly.AnomalyType;
import org.thingsboard.trendz.generator.model.tb.Attribute;
import org.thingsboard.trendz.generator.model.tb.CustomerData;
import org.thingsboard.trendz.generator.model.tb.CustomerUser;
import org.thingsboard.trendz.generator.model.tb.NodeConnectionType;
import org.thingsboard.trendz.generator.model.tb.RelationType;
import org.thingsboard.trendz.generator.model.tb.RuleNodeAdditionalInfo;
import org.thingsboard.trendz.generator.model.tb.Telemetry;
import org.thingsboard.trendz.generator.model.tb.Timestamp;
import org.thingsboard.trendz.generator.service.FileService;
import org.thingsboard.trendz.generator.service.VisualizationService;
import org.thingsboard.trendz.generator.service.anomaly.AnomalyService;
import org.thingsboard.trendz.generator.service.rest.TbRestClient;
import org.thingsboard.trendz.generator.solution.SolutionTemplateGenerator;
import org.thingsboard.trendz.generator.solution.energymetering.configuration.ApartmentConfiguration;
import org.thingsboard.trendz.generator.solution.energymetering.configuration.BuildingConfiguration;
import org.thingsboard.trendz.generator.solution.energymetering.model.Apartment;
import org.thingsboard.trendz.generator.solution.energymetering.model.Building;
import org.thingsboard.trendz.generator.solution.energymetering.model.EnergyMeter;
import org.thingsboard.trendz.generator.solution.energymetering.model.HeatMeter;
import org.thingsboard.trendz.generator.utils.DateTimeUtils;
import org.thingsboard.trendz.generator.utils.JsonUtils;
import org.thingsboard.trendz.generator.utils.RandomUtils;

import java.io.IOException;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
public class EnergyMeteringSolution implements SolutionTemplateGenerator {

    private static final String CUSTOMER_TITLE = "Energy Metering Customer";
    private static final String CUSTOMER_USER_EMAIL = "energymetering@thingsboard.io";
    private static final String CUSTOMER_USER_PASSWORD = "password";
    private static final String CUSTOMER_USER_FIRST_NAME = "John";
    private static final String CUSTOMER_USER_LAST_NAME = "Doe";

    private static final String RULE_CHAIN_NAME = "Energy Metering Rule Chain";

    private static final long dateRangeFrom = 100000;
    private static final long dateRangeTo = 10000000;
    private static final long serialRangeFrom = 10000;
    private static final long serialRangeTo = 99999;

    private final TbRestClient tbRestClient;
    private final FileService fileService;
    private final AnomalyService anomalyService;
    private final VisualizationService visualizationService;

    private final Map<Apartment, ApartmentConfiguration> apartmentConfigurationMap = new HashMap<>();
    private final Map<EnergyMeter, UUID> energyMeterIdMap = new HashMap<>();
    private final Map<HeatMeter, UUID> heatMeterIdMap = new HashMap<>();

    @Autowired
    public EnergyMeteringSolution(
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
        return "EnergyMetering";
    }

    @Override
    public void validate() {
        try {
            log.info("Energy Metering Solution - start validation");

            validateCustomerData();
            validateRuleChain();
            ModelData data = makeData(true, ZonedDateTime.now());
            validateData(data);

            log.info("Energy Metering Solution - validation is completed!");
        } catch (Exception e) {
            log.error("Energy Metering Solution validation was failed, skipping...", e);
        }
    }

    @Override
    public void generate(boolean skipTelemetry, ZonedDateTime startYear) {
        log.info("Energy Metering Solution - start generation");
        try {
            CustomerData customerData = createCustomerData();
            ModelData data = makeData(skipTelemetry, startYear);
            applyData(data, customerData);
            createRuleChain(data);

            log.info("Energy Metering Solution - generation is completed!");
        } catch (Exception e) {
            log.error("Energy Metering Solution generate was failed, skipping...", e);
        }
    }

    @Override
    public void remove() {
        log.info("Energy Metering Solution - start removal");
        try {
            deleteCustomerData();
            deleteRuleChain();
            ModelData data = makeData(true, ZonedDateTime.now());
            deleteData(data);

            log.info("Energy Metering Solution - removal is completed!");
        } catch (Exception e) {
            log.error("Energy Metering Solution removal was failed, skipping...", e);
        }
    }


    private Set<Building> mapToBuildings(ModelData data) {
        return data.getData().stream()
                .map(modelEntity -> (Building) modelEntity)
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
        Set<Building> buildings = mapToBuildings(data);

        try {
            RuleChain ruleChain = this.tbRestClient.createRuleChain(RULE_CHAIN_NAME);
            RuleChainMetaData metaData = this.tbRestClient.getRuleChainMetadataByRuleChainId(ruleChain.getUuidId())
                    .orElseThrow();

            List<RuleNode> nodes = metaData.getNodes();
            List<NodeConnectionInfo> connections = new ArrayList<>();
            metaData.setConnections(connections);

            for (Building building : buildings) {
                for (Apartment apartment : building.getApartments()) {
                    ApartmentConfiguration configuration = this.apartmentConfigurationMap.get(apartment);
                    boolean occupied = configuration.isOccupied();
                    int level = configuration.getLevel();

                    EnergyMeter energyMeter = apartment.getEnergyMeter();
                    HeatMeter heatMeter = apartment.getHeatMeter();
                    UUID energyMeterId = this.energyMeterIdMap.get(energyMeter);
                    UUID heatMeterId = this.heatMeterIdMap.get(heatMeter);

                    int index = nodes.size();

                    RuleNode saveNode = createSaveNode(
                            "Save: " + apartment.getSystemName(),
                            getNodePositionX(false),
                            getNodePositionY(index, 0)
                    );
                    nodes.add(saveNode);


                    RuleNode energyMeterConsumptionGeneratorNode = createGeneratorNode(
                            energyMeter.getSystemName() + ": energyConsumption",
                            energyMeterId,
                            getEnergyMeterConsumptionFile(occupied, level),
                            getNodePositionX(false),
                            getNodePositionY(index, 1)
                    );
                    nodes.add(energyMeterConsumptionGeneratorNode);
                    connections.add(createRuleConnection(index + 1, index));


                    RuleNode energyMeterGetLatestConsumptionNode = createLatestTelemetryLoadNode(
                            energyMeter.getSystemName() + ": energyConsAbsolute (1/2)",
                            "energyConsAbsolute",
                            getNodePositionX(false),
                            getNodePositionY(index, 2)
                    );
                    RuleNode energyMeterConsumptionTransformationNode = createTransformationNode(
                            energyMeter.getSystemName() + ": energyConsAbsolute (2/2)",
                            getEnergyMeterConsAbsoluteFile(),
                            getNodePositionX(false),
                            getNodePositionY(index, 3)
                    );
                    nodes.add(energyMeterGetLatestConsumptionNode);
                    nodes.add(energyMeterConsumptionTransformationNode);
                    connections.add(createRuleConnection(index + 1, index + 2));
                    connections.add(createRuleConnection(index + 2, index + 3));
                    connections.add(createRuleConnection(index + 3, index));


                    RuleNode heatMeterTemperatureGeneratorNode = createGeneratorNode(
                            heatMeter.getSystemName() + ": temperature",
                            heatMeterId,
                            getHeatMeterTemperatureFile(occupied),
                            getNodePositionX(true),
                            getNodePositionY(index, 0)
                    );
                    nodes.add(heatMeterTemperatureGeneratorNode);
                    connections.add(createRuleConnection(index + 4, index));


                    RuleNode heatMeterConsumptionGeneratorNode = createGeneratorNode(
                            heatMeter.getSystemName() + ": heatConsumption",
                            heatMeterId,
                            getHeatMeterConsumptionFile(occupied, level),
                            getNodePositionX(true),
                            getNodePositionY(index, 1)
                    );
                    nodes.add(heatMeterConsumptionGeneratorNode);
                    connections.add(createRuleConnection(index + 5, index));


                    RuleNode heatMeterGetLatestConsumptionNode = createLatestTelemetryLoadNode(
                            heatMeter.getSystemName() + ": heatConsAbsolute (1/2)",
                            "heatConsAbsolute",
                            getNodePositionX(true),
                            getNodePositionY(index, 2)
                    );
                    RuleNode heatMeterConsumptionTransformationNode = createTransformationNode(
                            heatMeter.getSystemName() + ": heatConsAbsolute (2/2)",
                            getHeatMeterConsAbsoluteFile(),
                            getNodePositionX(true),
                            getNodePositionY(index, 3)
                    );
                    nodes.add(heatMeterGetLatestConsumptionNode);
                    nodes.add(heatMeterConsumptionTransformationNode);
                    connections.add(createRuleConnection(index + 5, index + 6));
                    connections.add(createRuleConnection(index + 6, index + 7));
                    connections.add(createRuleConnection(index + 7, index));
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
        Building alpire = makeAlpire(skipTelemetry, startYear);
        Building feline = makeFeline(skipTelemetry, startYear);
        Building hogurity = makeHogurity(skipTelemetry, startYear);

        return ModelData.builder()
                .data(new TreeSet<>(Set.of(alpire, feline, hogurity)))
                .build();
    }

    private void applyData(ModelData data, CustomerData customerData) {
        CustomerUser customerUser = customerData.getUser();
        UUID ownerId = customerUser.getCustomerId().getId();

        Set<Building> buildings = mapToBuildings(data);
        for (Building building : buildings) {
            Asset buildingAsset = createBuilding(building, ownerId);

            Set<Apartment> apartments = building.getApartments();
            for (Apartment apartment : apartments) {
                Asset apartmentAsset = createApartment(apartment, ownerId);
                this.tbRestClient.assignAssetToCustomer(ownerId, apartmentAsset.getUuidId());
                this.tbRestClient.createRelation(RelationType.CONTAINS.getType(), buildingAsset.getId(), apartmentAsset.getId());

                EnergyMeter energyMeter = apartment.getEnergyMeter();
                HeatMeter heatMeter = apartment.getHeatMeter();
                Device energyMeterDevice = createEnergyMeter(energyMeter, ownerId);
                Device heatMeterDevice = createHeatMeter(heatMeter, ownerId);
                this.tbRestClient.assignDeviceToCustomer(ownerId, energyMeterDevice.getUuidId());
                this.tbRestClient.assignDeviceToCustomer(ownerId, heatMeterDevice.getUuidId());
                this.tbRestClient.createRelation(RelationType.CONTAINS.getType(), apartmentAsset.getId(), energyMeterDevice.getId());
                this.tbRestClient.createRelation(RelationType.CONTAINS.getType(), apartmentAsset.getId(), heatMeterDevice.getId());
            }
        }
    }

    private void validateData(ModelData data) {
        Set<Building> buildings = mapToBuildings(data);

        Set<Apartment> apartments = buildings.stream()
                .map(Building::getApartments)
                .flatMap(Collection::stream)
                .collect(Collectors.toSet());

        Set<EnergyMeter> energyMeters = apartments.stream()
                .map(Apartment::getEnergyMeter)
                .collect(Collectors.toSet());

        Set<HeatMeter> heatMeters = apartments.stream()
                .map(Apartment::getHeatMeter)
                .collect(Collectors.toSet());

        Set<String> assets = Sets.union(buildings, apartments)
                .stream()
                .map(ModelEntity::getSystemName)
                .collect(Collectors.toSet());

        Set<String> devices = Sets.union(energyMeters, heatMeters)
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
        Set<Building> buildings = mapToBuildings(data);

        Set<Apartment> apartments = buildings.stream()
                .map(Building::getApartments)
                .flatMap(Collection::stream)
                .collect(Collectors.toSet());

        Set<EnergyMeter> energyMeters = apartments.stream()
                .map(Apartment::getEnergyMeter)
                .collect(Collectors.toSet());

        Set<HeatMeter> heatMeters = apartments.stream()
                .map(Apartment::getHeatMeter)
                .collect(Collectors.toSet());

        Set<String> assets = Sets.union(buildings, apartments)
                .stream()
                .map(ModelEntity::getSystemName)
                .collect(Collectors.toSet());

        Set<String> devices = Sets.union(energyMeters, heatMeters)
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


    private Building makeAlpire(boolean skipTelemetry, ZonedDateTime startYear) {
        long TS_JANUARY = DateTimeUtils.toTs(startYear);
        long TS_FEBRUARY = DateTimeUtils.toTs(startYear.plusMonths(1));
        long TS_MARCH = DateTimeUtils.toTs(startYear.plusMonths(2));
        long TS_MAY = DateTimeUtils.toTs(startYear.plusMonths(4));

        BuildingConfiguration configuration = BuildingConfiguration.builder()
                .name("Alpire")
                .label("Asset label for Alpire building")
                .address("USA, California, San Francisco, ...")
                .floorCount(5)
                .apartmentsByFloorCount(2)
                .defaultApartmentConfiguration(
                        ApartmentConfiguration.builder()
                                .occupied(true)
                                .level(2)
                                .startDate(TS_JANUARY)
                                .anomalies(List.of())
                                .area(2)
                                .build()
                )
                .build();

        configuration.setApartmentConfiguration(
                1, 2,
                ApartmentConfiguration.builder()
                        .occupied(false)
                        .level(0)
                        .startDate(TS_JANUARY)
                        .anomalies(List.of(
                            AnomalyInfo.builder()
                                    .startTs(Timestamp.of(DateTimeUtils.toTs(startYear.withMonth(7).withDayOfMonth(10))))
                                    .endTs(Timestamp.of(DateTimeUtils.toTs(startYear.withMonth(7).withDayOfMonth(20))))
                                    .type(AnomalyType.INCREASED_DATA)
                                    .value(300)
                                    .build()
                        ))
                        .area(0)
                        .build()
        );

        configuration.setApartmentConfiguration(
                3, 1,
                ApartmentConfiguration.builder()
                        .occupied(false)
                        .level(0)
                        .startDate(TS_JANUARY)
                        .anomalies(List.of())
                        .area(0)
                        .build()
        );

        configuration.setApartmentConfiguration(
                4, 1,
                ApartmentConfiguration.builder()
                        .occupied(true)
                        .level(3)
                        .startDate(TS_JANUARY)
                        .anomalies(List.of())
                        .area(3)
                        .build()
        );

        configuration.setApartmentConfiguration(
                4, 2,
                ApartmentConfiguration.builder()
                        .occupied(true)
                        .level(3)
                        .startDate(TS_JANUARY)
                        .anomalies(List.of())
                        .area(3)
                        .build()
        );

        configuration.setApartmentConfiguration(
                5, 1,
                ApartmentConfiguration.builder()
                        .occupied(false)
                        .level(0)
                        .startDate(TS_FEBRUARY)
                        .anomalies(List.of())
                        .area(0)
                        .build()
        );

        configuration.setApartmentConfiguration(
                5, 2,
                ApartmentConfiguration.builder()
                        .occupied(false)
                        .level(0)
                        .startDate(TS_FEBRUARY)
                        .anomalies(List.of())
                        .area(0)
                        .build()
        );

        return makeBuildingByConfiguration(configuration, skipTelemetry);
    }

    private Building makeFeline(boolean skipTelemetry, ZonedDateTime startYear) {
        long TS_JANUARY = DateTimeUtils.toTs(startYear);
        long TS_FEBRUARY = DateTimeUtils.toTs(startYear.plusMonths(1));
        long TS_MARCH = DateTimeUtils.toTs(startYear.plusMonths(2));
        long TS_MAY = DateTimeUtils.toTs(startYear.plusMonths(4));

        BuildingConfiguration configuration = BuildingConfiguration.builder()
                .name("Feline")
                .label("Asset label for Feline building")
                .address("USA, New York, New York City, Brooklyn, ...")
                .floorCount(3)
                .apartmentsByFloorCount(3)
                .defaultApartmentConfiguration(
                        ApartmentConfiguration.builder()
                                .occupied(true)
                                .level(3)
                                .startDate(TS_MAY)
                                .anomalies(List.of())
                                .area(3)
                                .build()
                )
                .build();

        configuration.setApartmentConfiguration(
                1, 1,
                ApartmentConfiguration.builder()
                        .occupied(false)
                        .level(0)
                        .startDate(TS_MAY)
                        .anomalies(List.of(
                                AnomalyInfo.builder()
                                        .startTs(Timestamp.of(DateTimeUtils.toTs(startYear.withMonth(9).withDayOfMonth(1))))
                                        .endTs(Timestamp.of(DateTimeUtils.toTs(startYear.withMonth(9).withDayOfMonth(10))))
                                        .type(AnomalyType.INCREASED_DATA)
                                        .value(300)
                                        .build()
                        ))
                        .area(0)
                        .build()
        );

        configuration.setApartmentConfiguration(
                1, 2,
                ApartmentConfiguration.builder()
                        .occupied(true)
                        .level(1)
                        .startDate(TS_MAY)
                        .anomalies(List.of(

                        ))
                        .area(1)
                        .build()
        );

        configuration.setApartmentConfiguration(
                1, 3,
                ApartmentConfiguration.builder()
                        .occupied(true)
                        .level(1)
                        .startDate(TS_MAY)
                        .anomalies(List.of(

                        ))
                        .area(1)
                        .build()
        );

        configuration.setApartmentConfiguration(
                2, 1,
                ApartmentConfiguration.builder()
                        .occupied(true)
                        .level(2)
                        .startDate(TS_MAY)
                        .anomalies(List.of(

                        ))
                        .area(2)
                        .build()
        );

        configuration.setApartmentConfiguration(
                2, 2,
                ApartmentConfiguration.builder()
                        .occupied(true)
                        .level(2)
                        .startDate(TS_MAY)
                        .anomalies(List.of(

                        ))
                        .area(2)
                        .build()
        );

        configuration.setApartmentConfiguration(
                2, 3,
                ApartmentConfiguration.builder()
                        .occupied(true)
                        .level(2)
                        .startDate(TS_MAY)
                        .anomalies(List.of(

                        ))
                        .area(2)
                        .build()
        );

        configuration.setApartmentConfiguration(
                3, 1,
                ApartmentConfiguration.builder()
                        .occupied(true)
                        .level(3)
                        .startDate(TS_MAY)
                        .anomalies(List.of(
                                AnomalyInfo.builder()
                                        .startTs(Timestamp.of(DateTimeUtils.toTs(startYear.withMonth(10).withDayOfMonth(20))))
                                        .endTs(Timestamp.of(DateTimeUtils.toTs(startYear.withMonth(10).withDayOfMonth(25))))
                                        .type(AnomalyType.DECREASED_DATA)
                                        .value(3000)
                                        .build()
                        ))
                        .area(3)
                        .build()
        );

        configuration.setApartmentConfiguration(
                3, 2,
                ApartmentConfiguration.builder()
                        .occupied(true)
                        .level(3)
                        .startDate(TS_MAY)
                        .anomalies(List.of(
                                AnomalyInfo.builder()
                                        .startTs(Timestamp.of(DateTimeUtils.toTs(startYear.withMonth(9).withDayOfMonth(15))))
                                        .endTs(Timestamp.of(DateTimeUtils.toTs(startYear.withMonth(9).withDayOfMonth(20))))
                                        .type(AnomalyType.ZERO_VALUES)
                                        .build()
                        ))
                        .area(3)
                        .build()
        );

        return makeBuildingByConfiguration(configuration, skipTelemetry);
    }

    private Building makeHogurity(boolean skipTelemetry, ZonedDateTime startYear) {
        long TS_JANUARY = DateTimeUtils.toTs(startYear);
        long TS_FEBRUARY = DateTimeUtils.toTs(startYear.plusMonths(1));
        long TS_MARCH = DateTimeUtils.toTs(startYear.plusMonths(2));
        long TS_MAY = DateTimeUtils.toTs(startYear.plusMonths(4));

        BuildingConfiguration configuration = BuildingConfiguration.builder()
                .name("Hogurity")
                .label("Asset label for Hogurity building")
                .address("USA, New York, New York City, Manhattan, ...")
                .floorCount(1)
                .apartmentsByFloorCount(4)
                .defaultApartmentConfiguration(
                        ApartmentConfiguration.builder()
                                .occupied(true)
                                .level(3)
                                .startDate(TS_JANUARY)
                                .anomalies(List.of(
                                        AnomalyInfo.builder()
                                                .startTs(Timestamp.of(DateTimeUtils.toTs(startYear.withMonth(12).withDayOfMonth(1))))
                                                .endTs(Timestamp.of(DateTimeUtils.toTs(startYear.withMonth(12).withDayOfMonth(3))))
                                                .type(AnomalyType.DATA_GAP)
                                                .build()
                                ))
                                .area(3)
                                .build()
                )
                .build();

        configuration.setApartmentConfiguration(
                3, 1,
                ApartmentConfiguration.builder()
                        .occupied(true)
                        .level(3)
                        .startDate(TS_MARCH)
                        .anomalies(List.of(

                        ))
                        .area(3)
                        .build()
        );

        configuration.setApartmentConfiguration(
                4, 1,
                ApartmentConfiguration.builder()
                        .occupied(true)
                        .level(3)
                        .startDate(TS_MARCH)
                        .anomalies(List.of(
                                AnomalyInfo.builder()
                                        .startTs(Timestamp.of(DateTimeUtils.toTs(startYear.withMonth(8).withDayOfMonth(1))))
                                        .endTs(Timestamp.of(DateTimeUtils.toTs(startYear.withMonth(8).withDayOfMonth(5))))
                                        .type(AnomalyType.ZERO_VALUES)
                                        .build()
                        ))
                        .area(3)
                        .build()
        );

        return makeBuildingByConfiguration(configuration, skipTelemetry);
    }


    private Building makeBuildingByConfiguration(BuildingConfiguration configuration, boolean skipTelemetry) {
        Set<Apartment> apartments = new TreeSet<>();
        for (int floor = 1; floor <= configuration.getFloorCount(); floor++) {
            for (int number = 1; number <= configuration.getApartmentsByFloorCount(); number++) {
                ApartmentConfiguration apartmentConfiguration = configuration.getApartmentConfiguration(floor, number);
                Apartment apartment = createApartmentByConfiguration(apartmentConfiguration, configuration.getName(), floor, number, configuration.getApartmentsByFloorCount(), skipTelemetry);
                apartments.add(apartment);
            }
        }

        return Building.builder()
                .systemName(configuration.getName())
                .systemLabel(configuration.getLabel())
                .address(configuration.getAddress())
                .apartments(apartments)
                .build();
    }

    private Apartment createApartmentByConfiguration(ApartmentConfiguration configuration, String buildingName, int floor, int number, int apartmentByFloorCount, boolean skipTelemetry) {
        String titleNumber = floor + "0" + number;
        String letterAndNumber = buildingName.charAt(0) + titleNumber;
        long startDate = configuration.getStartDate() + createRandomDateBias();

        Telemetry<Long> energyMeterConsumption = createTelemetryEnergyMeterConsumption(configuration, skipTelemetry);
        this.anomalyService.applyAnomaly(energyMeterConsumption, configuration.getAnomalies());
        Telemetry<Long> energyMeterConsAbsolute = createTelemetryEnergyMeterConsAbsolute(energyMeterConsumption, skipTelemetry);

        Telemetry<Long> heatMeterTemperature = createTelemetryHeatMeterTemperature(configuration, skipTelemetry);
        Telemetry<Long> heatMeterConsumption = createTelemetryHeatMeterConsumption(configuration, skipTelemetry);
        this.anomalyService.applyAnomaly(heatMeterTemperature, configuration.getAnomalies());
        this.anomalyService.applyAnomaly(heatMeterConsumption, configuration.getAnomalies());
        Telemetry<Long> heatMeterConsAbsolute = createTelemetryHeatMeterConsAbsolute(heatMeterConsumption, skipTelemetry);

        EnergyMeter energyMeter = EnergyMeter.builder()
                .systemName("Energy Meter " + letterAndNumber)
                .systemLabel("")
                .serialNumber(createRandomSerialNumber())
                .installDate(startDate)
                .energyConsumption(energyMeterConsumption)
                .energyConsAbsolute(energyMeterConsAbsolute)
                .build();

        HeatMeter heatMeter = HeatMeter.builder()
                .systemName("Heat Meter " + letterAndNumber)
                .systemLabel("")
                .serialNumber(createRandomSerialNumber())
                .installDate(startDate)
                .temperature(heatMeterTemperature)
                .heatConsumption(heatMeterConsumption)
                .heatConsAbsolute(heatMeterConsAbsolute)
                .build();

        Apartment apartment = Apartment.builder()
                .systemName("Apt " + titleNumber + " in " + buildingName)
                .systemLabel("")
                .floor(floor)
                .area(createRandomAreaByLevel(configuration.getArea()))
                .roomNumber((floor - 1) * apartmentByFloorCount + number)
                .state(configuration.isOccupied() ? "occupied" : "free")
                .energyMeter(energyMeter)
                .heatMeter(heatMeter)
                .build();

        this.apartmentConfigurationMap.put(apartment, configuration);
        return apartment;
    }


    private long createRandomDateBias() {
        return RandomUtils.getRandomNumber(dateRangeFrom, dateRangeTo);
    }

    private long createRandomSerialNumber() {
        return RandomUtils.getRandomNumber(serialRangeFrom, serialRangeTo);
    }

    private int createRandomAreaByLevel(int level) {
        switch (level) {
            case 0:
                return (int) RandomUtils.getRandomNumber(30, 300);
            case 1:
                return (int) RandomUtils.getRandomNumber(30, 60);
            case 2:
                return (int) RandomUtils.getRandomNumber(60, 150);
            case 3:
                return (int) RandomUtils.getRandomNumber(150, 300);
            default:
                throw new IllegalArgumentException("Unsupported level: " + level);
        }
    }


    private Telemetry<Long> createTelemetryEnergyMeterConsumption(ApartmentConfiguration configuration, boolean skipTelemetry) {
        if (skipTelemetry) {
            return new Telemetry<>("skip");
        }

        Telemetry<Long> result = new Telemetry<>("energyConsumption");
        long now = System.currentTimeMillis();

        long startTs = configuration.getStartDate();
        boolean occupied = configuration.isOccupied();
        int level = configuration.getLevel();

        if (occupied) {
            switch (level) {
                case 1: {
                    long minValue = 5_000;
                    long amplitude = 2_000;
                    long noiseWidth = 500;
                    long noiseAmplitude = (amplitude / noiseWidth);
                    double phase = (3.14 * 1) / 12;
                    double koeff = 3.14 / 24;

                    ZonedDateTime startDate = DateTimeUtils.fromTs(startTs).truncatedTo(ChronoUnit.HOURS);
                    ZonedDateTime nowDate = DateTimeUtils.fromTs(now).truncatedTo(ChronoUnit.HOURS);
                    ZonedDateTime iteratedDate = startDate;
                    while (iteratedDate.isBefore(nowDate)) {
                        long iteratedTs = DateTimeUtils.toTs(iteratedDate);
                        long argument = iteratedDate.getHour() - 12;
                        long noise = RandomUtils.getRandomNumber(-noiseAmplitude, noiseAmplitude) * noiseWidth;
                        long value = minValue + noise + Math.round(amplitude * Math.sin(phase + koeff * argument));

                        result.add(iteratedTs, value);
                        iteratedDate = iteratedDate.plus(1, ChronoUnit.HOURS);
                    }
                    return result;
                }
                case 2: {
                    long minValue = 15_000;
                    long amplitude = 2_000;
                    long noiseWidth = 500;
                    long noiseAmplitude = (amplitude / noiseWidth) * 3;
                    double phase = (3.14 * 3) / 12;
                    double koeff = 3.14 / 12;

                    ZonedDateTime startDate = DateTimeUtils.fromTs(startTs).truncatedTo(ChronoUnit.HOURS);
                    ZonedDateTime nowDate = DateTimeUtils.fromTs(now).truncatedTo(ChronoUnit.HOURS);
                    ZonedDateTime iteratedDate = startDate;
                    while (iteratedDate.isBefore(nowDate)) {
                        long iteratedTs = DateTimeUtils.toTs(iteratedDate);
                        long argument = iteratedDate.getHour() - 12;
                        long noise = RandomUtils.getRandomNumber(-noiseAmplitude, noiseAmplitude) * noiseWidth;
                        long value = minValue + noise + Math.round(amplitude * Math.sin(phase + koeff * argument));

                        result.add(iteratedTs, value);
                        iteratedDate = iteratedDate.plus(1, ChronoUnit.HOURS);
                    }
                    return result;
                }
                case 3: {
                    long minValue = 30_000;
                    long amplitude = 5_000;
                    long noiseWidth = 3000;
                    long noiseAmplitude = (amplitude / noiseWidth) * 3;
                    double phase = (3.14 * 0.3) / 14;
                    double koeffDay = 3.14 / 14;
                    double koeffHour = 3.14 / 6;

                    ZonedDateTime startDate = DateTimeUtils.fromTs(startTs).truncatedTo(ChronoUnit.HOURS);
                    ZonedDateTime nowDate = DateTimeUtils.fromTs(now).truncatedTo(ChronoUnit.HOURS);
                    ZonedDateTime iteratedDate = startDate;
                    while (iteratedDate.isBefore(nowDate)) {
                        long iteratedTs = DateTimeUtils.toTs(iteratedDate);
                        long argumentDay = iteratedDate.getDayOfWeek().getValue() * 2L - 7;
                        long argumentHour = iteratedDate.getHour() - 12;
                        long noise = RandomUtils.getRandomNumber(-noiseAmplitude, noiseAmplitude) * noiseWidth;
                        long value = minValue + noise + Math.round(amplitude * Math.sin(phase + koeffDay * argumentDay + koeffHour * argumentHour));

                        result.add(iteratedTs, value);
                        iteratedDate = iteratedDate.plus(1, ChronoUnit.HOURS);
                    }
                    return result;
                }
                default:
                    throw new IllegalStateException("Unsupported level: " + level);
            }
        } else {
            long minValue = 15;
            long amplitude = 10;
            long noiseWidth = 3;
            long noiseAmplitude = (amplitude / noiseWidth);
            double phase = (3.14 * 3) / 128;
            double koeff = 3.14 / 128;

            ZonedDateTime startDate = DateTimeUtils.fromTs(startTs).truncatedTo(ChronoUnit.HOURS);
            ZonedDateTime nowDate = DateTimeUtils.fromTs(now).truncatedTo(ChronoUnit.HOURS);
            ZonedDateTime iteratedDate = startDate;
            while (iteratedDate.isBefore(nowDate)) {
                long iteratedTs = DateTimeUtils.toTs(iteratedDate);
                long argument = iteratedDate.getHour() - 12;
                long noise = RandomUtils.getRandomNumber(-noiseAmplitude, noiseAmplitude) * noiseWidth;
                long value = minValue + noise + Math.round(amplitude * Math.sin(phase + koeff * argument));

                result.add(iteratedTs, value);
                iteratedDate = iteratedDate.plus(1, ChronoUnit.HOURS);
            }
            return result;
        }
    }

    private Telemetry<Long> createTelemetryEnergyMeterConsAbsolute(Telemetry<Long> energyConsumptionTelemetry, boolean skipTelemetry) {
        if (skipTelemetry) {
            return new Telemetry<>("skip");
        }

        Telemetry<Long> result = new Telemetry<>("energyConsAbsolute");
        long sum = 0;
        for (Telemetry.Point<Long> point : energyConsumptionTelemetry.getPoints()) {
            sum += point.getValue();
            result.add(new Telemetry.Point<>(point.getTs(), sum));
        }
        return result;
    }

    private Telemetry<Long> createTelemetryHeatMeterTemperature(ApartmentConfiguration configuration, boolean skipTelemetry) {
        if (skipTelemetry) {
            return new Telemetry<>("skip");
        }

        Telemetry<Long> result = new Telemetry<>("temperature");
        long now = System.currentTimeMillis();

        long startTs = configuration.getStartDate();
        boolean occupied = configuration.isOccupied();

        long noiseAmplitude = 3;

        ZonedDateTime startDate = DateTimeUtils.fromTs(startTs).truncatedTo(ChronoUnit.HOURS);
        ZonedDateTime nowDate = DateTimeUtils.fromTs(now).truncatedTo(ChronoUnit.HOURS);
        ZonedDateTime iteratedDate = startDate;
        while (iteratedDate.isBefore(nowDate)) {
            long iteratedTs = DateTimeUtils.toTs(iteratedDate);
            long value;
            if (occupied) {
                value = 20;
            } else {
                value = 15;
            }
            value += RandomUtils.getRandomNumber(-noiseAmplitude, noiseAmplitude);

            result.add(iteratedTs, value);
            iteratedDate = iteratedDate.plus(1, ChronoUnit.HOURS);
        }

        return result;
    }

    private Telemetry<Long> createTelemetryHeatMeterConsumption(ApartmentConfiguration configuration, boolean skipTelemetry) {
        if (skipTelemetry) {
            return new Telemetry<>("skip");
        }

        long now = System.currentTimeMillis();

        long startTs = configuration.getStartDate();
        boolean occupied = configuration.isOccupied();
        int level = configuration.getLevel();

        if (occupied) {
            switch (level) {
                case 1: {
                    long valueWarmTime = 0;
                    long valueColdTime = 3_000;
                    long noiseAmplitude = 300;
                    long noiseWidth = 100;

                    return makeHeatConsumption(now, startTs, valueWarmTime, valueColdTime, noiseAmplitude, noiseWidth);
                }
                case 2: {
                    long valueWarmTime = 0;
                    long valueColdTime = 6_000;
                    long noiseAmplitude = 800;
                    long noiseWidth = 400;

                    return makeHeatConsumption(now, startTs, valueWarmTime, valueColdTime, noiseAmplitude, noiseWidth);
                }
                case 3: {
                    long valueWarmTime = 0;
                    long valueColdTime = 10_000;
                    long noiseAmplitude = 2_000;
                    long noiseWidth = 800;

                    return makeHeatConsumption(now, startTs, valueWarmTime, valueColdTime, noiseAmplitude, noiseWidth);
                }
                default:
                    throw new IllegalStateException("Unsupported level: " + level);
            }
        } else {
            long valueWarmTime = 0;
            long valueColdTime = 0;
            long noiseAmplitude = 0;
            long noiseWidth = 1;

            return makeHeatConsumption(now, startTs, valueWarmTime, valueColdTime, noiseAmplitude, noiseWidth);
        }
    }

    private Telemetry<Long> makeHeatConsumption(long now, long startTs, long valueWarmTime, long valueColdTime, long noiseAmplitude, long noiseWidth) {
        Telemetry<Long> result = new Telemetry<>("heatConsumption");
        int dayColdTimeEnd = 80;
        int dayWarmTimeStart = 120;
        int dayWarmTimeEnd = 230;
        int dayColdTimeStart = 290;

        long shiftedNoiseAmplitude = noiseAmplitude / noiseWidth;
        ZonedDateTime startDate = DateTimeUtils.fromTs(startTs).truncatedTo(ChronoUnit.HOURS);
        ZonedDateTime nowDate = DateTimeUtils.fromTs(now).truncatedTo(ChronoUnit.HOURS);
        ZonedDateTime iteratedDate = startDate;
        while (iteratedDate.isBefore(nowDate)) {
            long iteratedTs = DateTimeUtils.toTs(iteratedDate);
            int day = iteratedDate.getDayOfYear();

            long value;
            long noise = RandomUtils.getRandomNumber(-shiftedNoiseAmplitude, shiftedNoiseAmplitude) * noiseWidth;
            if (day <= dayColdTimeEnd || day > dayColdTimeStart) {
                // Cold Time
                value = valueColdTime;
                value += noise;
            } else if (day <= dayWarmTimeStart) {
                // Cold To Warm
                int diff = dayWarmTimeStart - dayColdTimeEnd;
                int current = dayWarmTimeStart - day;
                value = valueWarmTime + Math.round((1.0 * current * (valueColdTime - valueWarmTime)) / diff);
                value += noise;
            } else if (day <= dayWarmTimeEnd) {
                // Warm
                value = valueWarmTime;
            } else {
                // Warm To Cold
                int diff = dayColdTimeStart - dayWarmTimeEnd;
                int current = -(dayWarmTimeEnd - day);
                value = valueWarmTime + Math.round((1.0 * current * (valueColdTime - valueWarmTime)) / diff);
                value += noise;
            }

            value = Math.max(0, value);
            result.add(iteratedTs, value);
            iteratedDate = iteratedDate.plus(1, ChronoUnit.HOURS);
        }
        return result;
    }

    private Telemetry<Long> createTelemetryHeatMeterConsAbsolute(Telemetry<Long> heatConsumptionTelemetry, boolean skipTelemetry) {
        if (skipTelemetry) {
            return new Telemetry<>("skip");
        }

        Telemetry<Long> result = new Telemetry<>("heatConsAbsolute");
        long sum = 0;
        for (Telemetry.Point<Long> point : heatConsumptionTelemetry.getPoints()) {
            sum += point.getValue();
            result.add(new Telemetry.Point<>(point.getTs(), sum));
        }
        return result;
    }


    private Asset createBuilding(Building building, UUID ownerId) {
        Asset asset = tbRestClient.createAsset(building.getSystemName(), "building");
        tbRestClient.assignAssetToCustomer(ownerId, asset.getUuidId());

        Set<Attribute<?>> attributes = Set.of(
                new Attribute<>("address", building.getAddress())
        );
        tbRestClient.setEntityAttributes(asset.getUuidId(), EntityType.ASSET, Attribute.Scope.SERVER_SCOPE, attributes);
        return asset;
    }

    private Asset createApartment(Apartment apartment, UUID ownerId) {
        Asset asset = tbRestClient.createAsset(apartment.getSystemName(), "apartment");
        tbRestClient.assignAssetToCustomer(ownerId, asset.getUuidId());

        Set<Attribute<?>> attributes = Set.of(
                new Attribute<>("floor", apartment.getFloor()),
                new Attribute<>("area", apartment.getArea()),
                new Attribute<>("roomNumber", apartment.getRoomNumber()),
                new Attribute<>("state", apartment.getState())
        );
        tbRestClient.setEntityAttributes(asset.getUuidId(), EntityType.ASSET, Attribute.Scope.SERVER_SCOPE, attributes);
        return asset;
    }

    private Device createEnergyMeter(EnergyMeter energyMeter, UUID ownerId) {
        Device device = tbRestClient.createDevice(energyMeter.getSystemName(), "energyMeter");
        DeviceCredentials deviceCredentials = tbRestClient.getDeviceCredentials(device.getUuidId());
        tbRestClient.assignDeviceToCustomer(ownerId, device.getUuidId());

        Set<Attribute<?>> attributes = Set.of(
                new Attribute<>("installDate", energyMeter.getInstallDate()),
                new Attribute<>("serialNumber", energyMeter.getSerialNumber())
        );
        tbRestClient.setEntityAttributes(device.getUuidId(), EntityType.DEVICE, Attribute.Scope.SERVER_SCOPE, attributes);

        tbRestClient.pushTelemetry(deviceCredentials.getCredentialsId(), energyMeter.getEnergyConsumption());
        tbRestClient.pushTelemetry(deviceCredentials.getCredentialsId(), energyMeter.getEnergyConsAbsolute());

        this.energyMeterIdMap.put(energyMeter, device.getUuidId());
        return device;
    }

    private Device createHeatMeter(HeatMeter heatMeter, UUID ownerId) {
        Device device = tbRestClient.createDevice(heatMeter.getSystemName(), "heatMeter");
        DeviceCredentials deviceCredentials = tbRestClient.getDeviceCredentials(device.getUuidId());
        tbRestClient.assignDeviceToCustomer(ownerId, device.getUuidId());

        Set<Attribute<?>> attributes = Set.of(
                new Attribute<>("installDate", heatMeter.getInstallDate()),
                new Attribute<>("serialNumber", heatMeter.getSerialNumber())
        );
        tbRestClient.setEntityAttributes(device.getUuidId(), EntityType.DEVICE, Attribute.Scope.SERVER_SCOPE, attributes);

        tbRestClient.pushTelemetry(deviceCredentials.getCredentialsId(), heatMeter.getTemperature());
        tbRestClient.pushTelemetry(deviceCredentials.getCredentialsId(), heatMeter.getHeatConsumption());
        tbRestClient.pushTelemetry(deviceCredentials.getCredentialsId(), heatMeter.getHeatConsAbsolute());

        this.heatMeterIdMap.put(heatMeter, device.getUuidId());
        return device;
    }


    private RuleNode createSaveNode(String name, double gridX, double gridY) {
        TbMsgTimeseriesNodeConfiguration saveConfiguration = new TbMsgTimeseriesNodeConfiguration();
        saveConfiguration.setDefaultTTL(0);
        saveConfiguration.setUseServerTs(false);
        saveConfiguration.setSkipLatestPersistence(false);

        return createRuleNode(name, TbMsgTimeseriesNode.class, saveConfiguration, (int) gridX, (int) gridY);
    }

    private RuleNode createGeneratorNode(String name, UUID entityId, String fileName, double gridX, double gridY) throws IOException {
        String fileContent = this.fileService.getFileContent(getSolutionName(), fileName);

        TbMsgGeneratorNodeConfiguration generatorConfiguration = new TbMsgGeneratorNodeConfiguration();
        generatorConfiguration.setOriginatorType(EntityType.DEVICE);
        generatorConfiguration.setOriginatorId(entityId.toString());
        generatorConfiguration.setMsgCount(0);
        generatorConfiguration.setPeriodInSeconds(3600);
        generatorConfiguration.setJsScript(fileContent);

        return createRuleNode(name, TbMsgGeneratorNode.class, generatorConfiguration, (int) gridX, (int) gridY);
    }

    private RuleNode createLatestTelemetryLoadNode(String name, String telemetryName, double gridX, double gridY) {
        TbGetAttributesNodeConfiguration configuration = new TbGetAttributesNodeConfiguration();
        configuration.setLatestTsKeyNames(List.of(telemetryName));

        return createRuleNode(name, TbGetAttributesNode.class, configuration, (int) gridX, (int) gridY);
    }

    private RuleNode createTransformationNode(String name, String scriptFileName, double gridX, double gridY) throws IOException {
        String fileContent = this.fileService.getFileContent(getSolutionName(), scriptFileName);

        TbTransformMsgNodeConfiguration configuration = new TbTransformMsgNodeConfiguration();
        configuration.setScriptLang(ScriptLanguage.JS);
        configuration.setJsScript(fileContent);

        return createRuleNode(name, TbTransformMsgNode.class, configuration, (int) gridX, (int) gridY);
    }


    private double getNodePositionX(boolean left) {
        return left
                ? RuleNodeAdditionalInfo.CELL_SIZE * 5
                : RuleNodeAdditionalInfo.CELL_SIZE * 20;
    }

    private double getNodePositionY(int index, int i) {
        double shift = 5;
        double koeff = 1.7;
        double startShift = 10;
        double step = 3;

        return (RuleNodeAdditionalInfo.CELL_SIZE + shift) * (index * koeff + startShift + step * i);
    }

    private RuleNode createRuleNode(String name, Class<?> typeClass, NodeConfiguration<?> configuration, int x, int y) {
        RuleNode generatorNode = new RuleNode();
        generatorNode.setName(name);
        generatorNode.setType(typeClass.getName());
        generatorNode.setConfiguration(JsonUtils.makeNodeFromPojo(configuration));
        generatorNode.setAdditionalInfo(
                RuleNodeAdditionalInfo.builder()
                        .description("Description for " + name)
                        .layoutX(x)
                        .layoutY(y)
                        .build()
                        .toJsonNode()
        );

        return generatorNode;
    }

    private NodeConnectionInfo createRuleConnection(int from, int to) {
        NodeConnectionInfo connection = new NodeConnectionInfo();
        connection.setType(NodeConnectionType.SUCCESS.toString());
        connection.setFromIndex(from);
        connection.setToIndex(to);
        return connection;
    }


    private String getEnergyMeterConsumptionFile(boolean occupied, int level) {
        return occupied
                ? "energy_consumption_level" + level + ".js"
                : "energy_consumption_level0.js";
    }

    private String getEnergyMeterConsAbsoluteFile() {
        return "energy_cons_absolute.js";
    }

    private String getHeatMeterTemperatureFile(boolean occupied) {
        return occupied
                ? "heat_temperature_level1.js"
                : "heat_temperature_level0.js";
    }

    private String getHeatMeterConsumptionFile(boolean occupied, int level) {
        return occupied
                ? "heat_consumption_level" + level + ".js"
                : "heat_consumption_level0.js";
    }

    private String getHeatMeterConsAbsoluteFile() {
        return "heat_cons_absolute.js";
    }
}

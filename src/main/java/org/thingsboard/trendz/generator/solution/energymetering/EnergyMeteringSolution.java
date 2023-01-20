package org.thingsboard.trendz.generator.solution.energymetering;

import com.google.common.collect.Sets;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.thingsboard.rule.engine.debug.TbMsgGeneratorNode;
import org.thingsboard.rule.engine.debug.TbMsgGeneratorNodeConfiguration;
import org.thingsboard.rule.engine.telemetry.TbMsgTimeseriesNode;
import org.thingsboard.rule.engine.telemetry.TbMsgTimeseriesNodeConfiguration;
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
import org.thingsboard.trendz.generator.model.Attribute;
import org.thingsboard.trendz.generator.model.CustomerUser;
import org.thingsboard.trendz.generator.model.NodeConnectionType;
import org.thingsboard.trendz.generator.model.RelationType;
import org.thingsboard.trendz.generator.model.RuleNodeAdditionalInfo;
import org.thingsboard.trendz.generator.model.Scope;
import org.thingsboard.trendz.generator.model.Telemetry;
import org.thingsboard.trendz.generator.service.FileService;
import org.thingsboard.trendz.generator.service.TbRestClient;
import org.thingsboard.trendz.generator.service.VisualizationService;
import org.thingsboard.trendz.generator.solution.SolutionTemplateGenerator;
import org.thingsboard.trendz.generator.solution.energymetering.model.Apartment;
import org.thingsboard.trendz.generator.solution.energymetering.model.Building;
import org.thingsboard.trendz.generator.solution.energymetering.model.EnergyMeter;
import org.thingsboard.trendz.generator.solution.energymetering.model.HeatMeter;
import org.thingsboard.trendz.generator.utils.DateTimeUtils;
import org.thingsboard.trendz.generator.utils.JsonUtils;
import org.thingsboard.trendz.generator.utils.RandomUtils;

import java.io.IOException;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
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

    private static final long TS_2022_JANUARY = DateTimeUtils.toTs(
            ZonedDateTime.of(2022, 1, 1, 0, 0, 0, 0, ZoneId.systemDefault())
    );
    private static final long TS_2022_FEBRUARY = DateTimeUtils.toTs(
            ZonedDateTime.of(2022, 2, 1, 0, 0, 0, 0, ZoneId.systemDefault())
    );
    private static final long TS_2022_MARCH = DateTimeUtils.toTs(
            ZonedDateTime.of(2022, 3, 1, 0, 0, 0, 0, ZoneId.systemDefault())
    );
    private static final long TS_2022_MAY = DateTimeUtils.toTs(
            ZonedDateTime.of(2022, 5, 1, 0, 0, 0, 0, ZoneId.systemDefault())
    );
    private static final long dateRangeFrom = 100000;
    private static final long dateRangeTo = 10000000;
    private static final long serialRangeFrom = 10000;
    private static final long serialRangeTo = 99999;

    private final TbRestClient tbRestClient;
    private final FileService fileService;
    private final VisualizationService visualizationService;

    private final Map<Apartment, ApartmentConfiguration> apartmentConfigurationMap = new HashMap<>();
    private final Map<EnergyMeter, UUID> energyMeterIdMap = new HashMap<>();
    private final Map<HeatMeter, UUID> heatMeterIdMap = new HashMap<>();

    @Autowired
    public EnergyMeteringSolution(
            TbRestClient tbRestClient,
            FileService fileService,
            VisualizationService visualizationService
    ) {
        this.tbRestClient = tbRestClient;
        this.fileService = fileService;
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

            tbRestClient.getCustomerByTitle(CUSTOMER_TITLE)
                    .ifPresent(customer -> {
                        throw new CustomerAlreadyExistException(customer);
                    });

            tbRestClient.getAllRuleChains()
                    .stream()
                    .filter(ruleChain -> ruleChain.getName().equals(RULE_CHAIN_NAME))
                    .findAny()
                    .ifPresent(ruleChain -> {
                        throw new RuleChainAlreadyExistException(ruleChain);
                    });

            List<Building> data = makeData(true);

            Set<String> allAssetNames = getAllAssetNames(data);
            Set<Asset> badAssets = tbRestClient.getAllAssets()
                    .stream()
                    .filter(asset -> allAssetNames.contains(asset.getName()))
                    .collect(Collectors.toSet());

            if (!badAssets.isEmpty()) {
                log.error("There are assets that already exists: {}", badAssets);
                throw new AssetAlreadyExistException(badAssets.iterator().next());
            }


            Set<String> allDeviceNames = getAllDeviceNames(data);
            Set<Device> badDevices = tbRestClient.getAllDevices()
                    .stream()
                    .filter(device -> allDeviceNames.contains(device.getName()))
                    .collect(Collectors.toSet());

            if (!badDevices.isEmpty()) {
                log.error("There are devices that already exists: {}", badDevices);
                throw new DeviceAlreadyExistException(badDevices.iterator().next());
            }

            log.info("Energy Metering Solution - validation is completed!");
        } catch (Exception e) {
            log.error("Energy Metering Solution validation was failed, skipping...", e);
        }
    }

    @Override
    public void generate() {
        log.info("Energy Metering Solution - start generation");
        try {

            Customer customer = tbRestClient.createCustomer(CUSTOMER_TITLE);
            CustomerUser customerUser = tbRestClient.createCustomerUser(
                    customer, CUSTOMER_USER_EMAIL, CUSTOMER_USER_PASSWORD,
                    CUSTOMER_USER_FIRST_NAME, CUSTOMER_USER_LAST_NAME
            );

            List<Building> data = makeData(false);
            visualizeData(data);
            applyData(data, customerUser);
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
            tbRestClient.getCustomerByTitle(CUSTOMER_TITLE)
                    .ifPresent(customer -> tbRestClient.deleteCustomer(customer.getUuidId()));

            tbRestClient.getAllRuleChains()
                    .stream()
                    .filter(ruleChain -> ruleChain.getName().equals(RULE_CHAIN_NAME))
                    .findAny()
                    .flatMap(ruleChain -> tbRestClient.getRuleChainById(ruleChain.getUuidId()))
                    .ifPresent(ruleChain -> {
                        tbRestClient.deleteRuleChain(ruleChain.getUuidId());
                    });

            List<Building> data = makeData(true);

            Set<String> allDeviceNames = getAllDeviceNames(data);
            tbRestClient.getAllDevices()
                    .stream()
                    .filter(device -> allDeviceNames.contains(device.getName()))
                    .forEach(device -> tbRestClient.deleteDevice(device.getUuidId()));

            Set<String> allAssetNames = getAllAssetNames(data);
            tbRestClient.getAllAssets()
                    .stream()
                    .filter(asset -> allAssetNames.contains(asset.getName()))
                    .forEach(asset -> tbRestClient.deleteAsset(asset.getUuidId()));

            log.info("Energy Metering Solution - removal is completed!");
        } catch (Exception e) {
            log.error("Energy Metering Solution removal was failed, skipping...", e);
        }
    }


    private List<Building> makeData(boolean skipTelemetry) {
        Building alpire = makeAlpire(skipTelemetry);
        Building feline = makeFeline(skipTelemetry);
        Building hogurity = makeHogurity(skipTelemetry);

        return List.of(alpire, feline, hogurity);
    }

    private void visualizeData(List<Building> data) {
//        visualizationService.visualize();
    }

    private void applyData(List<Building> buildings, CustomerUser customerUser) {
        UUID ownerId = customerUser.getCustomerId().getId();
        for (Building building : buildings) {
            Asset buildingAsset = createBuilding(building, ownerId);

            Set<Apartment> apartments = building.getApartments();
            for (Apartment apartment : apartments) {
                Asset apartmentAsset = createApartment(apartment, ownerId);
                tbRestClient.assignAssetToCustomer(ownerId, apartmentAsset.getUuidId());
                tbRestClient.createRelation(RelationType.CONTAINS.getType(), buildingAsset.getId(), apartmentAsset.getId());

                EnergyMeter energyMeter = apartment.getEnergyMeter();
                HeatMeter heatMeter = apartment.getHeatMeter();
                Device energyMeterDevice = createEnergyMeter(energyMeter, ownerId);
                Device heatMeterDevice = createHeatMeter(heatMeter, ownerId);
                tbRestClient.assignDeviceToCustomer(ownerId, energyMeterDevice.getUuidId());
                tbRestClient.assignDeviceToCustomer(ownerId, heatMeterDevice.getUuidId());
                tbRestClient.createRelation(RelationType.CONTAINS.getType(), apartmentAsset.getId(), energyMeterDevice.getId());
                tbRestClient.createRelation(RelationType.CONTAINS.getType(), apartmentAsset.getId(), heatMeterDevice.getId());
            }
        }
    }

    private void createRuleChain(List<Building> buildings) {
        try {
            RuleChain ruleChain = tbRestClient.createRuleChain(RULE_CHAIN_NAME);
            RuleChainMetaData metaData = tbRestClient.getRuleChainMetadataByRuleChainId(ruleChain.getUuidId())
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
                            RuleNodeAdditionalInfo.CELL_SIZE * 20,
                            RuleNodeAdditionalInfo.CELL_SIZE * (index * 1.7 + 10)
                    );
                    nodes.add(saveNode);


                    RuleNode energyMeterConsumptionGeneratorNode = createGeneratorMode(
                            energyMeter.getSystemName() + " : energyConsumption",
                            energyMeterId,
                            getEnergyMeterConsumptionFile(occupied, level),
                            RuleNodeAdditionalInfo.CELL_SIZE * 20,
                            RuleNodeAdditionalInfo.CELL_SIZE * (index * 1.7 + 13)
                    );
                    nodes.add(energyMeterConsumptionGeneratorNode);

                    NodeConnectionInfo energyMeterConsumptionConnection = new NodeConnectionInfo();
                    energyMeterConsumptionConnection.setType(NodeConnectionType.SUCCESS.getType());
                    energyMeterConsumptionConnection.setFromIndex(index + 1);
                    energyMeterConsumptionConnection.setToIndex(index);
                    connections.add(energyMeterConsumptionConnection);


                    RuleNode energyMeterConsAbsoluteGeneratorNode = createGeneratorMode(
                            energyMeter.getSystemName() + " : energyConsAbsolute",
                            energyMeterId,
                            getEnergyMeterConsAbsoluteFile(occupied, level),
                            RuleNodeAdditionalInfo.CELL_SIZE * 20,
                            RuleNodeAdditionalInfo.CELL_SIZE * (index * 1.7 + 16)
                    );
                    nodes.add(energyMeterConsAbsoluteGeneratorNode);

                    NodeConnectionInfo energyMeterConsAbsoluteConnection = new NodeConnectionInfo();
                    energyMeterConsAbsoluteConnection.setType(NodeConnectionType.SUCCESS.getType());
                    energyMeterConsAbsoluteConnection.setFromIndex(index + 2);
                    energyMeterConsAbsoluteConnection.setToIndex(index);
                    connections.add(energyMeterConsAbsoluteConnection);


                    RuleNode heatMeterTemperatureGeneratorNode = createGeneratorMode(
                            heatMeter.getSystemName() + " : temperature",
                            heatMeterId,
                            getHeatMeterTemperatureFile(occupied),
                            RuleNodeAdditionalInfo.CELL_SIZE * 5,
                            RuleNodeAdditionalInfo.CELL_SIZE * (index * 1.7 + 10)
                    );
                    nodes.add(heatMeterTemperatureGeneratorNode);

                    NodeConnectionInfo heatMeterTemperatureConnection = new NodeConnectionInfo();
                    heatMeterTemperatureConnection.setType(NodeConnectionType.SUCCESS.getType());
                    heatMeterTemperatureConnection.setFromIndex(index + 3);
                    heatMeterTemperatureConnection.setToIndex(index);
                    connections.add(heatMeterTemperatureConnection);


                    RuleNode heatMeterConsumptionGeneratorNode = createGeneratorMode(
                            heatMeter.getSystemName() + " : heatConsumption",
                            heatMeterId,
                            getHeatMeterConsumptionFile(occupied, level),
                            RuleNodeAdditionalInfo.CELL_SIZE * 5,
                            RuleNodeAdditionalInfo.CELL_SIZE * (index * 1.7 + 13)
                    );
                    nodes.add(heatMeterConsumptionGeneratorNode);

                    NodeConnectionInfo heatMeterConsumptionConnection = new NodeConnectionInfo();
                    heatMeterConsumptionConnection.setType(NodeConnectionType.SUCCESS.getType());
                    heatMeterConsumptionConnection.setFromIndex(index + 4);
                    heatMeterConsumptionConnection.setToIndex(index);
                    connections.add(heatMeterConsumptionConnection);


                    RuleNode heatMeterConsAbsoluteGeneratorNode = createGeneratorMode(
                            heatMeter.getSystemName() + " : heatConsAbsolute",
                            heatMeterId,
                            getHeatMeterConsAbsoluteFile(occupied, level),
                            RuleNodeAdditionalInfo.CELL_SIZE * 5,
                            RuleNodeAdditionalInfo.CELL_SIZE * (index * 1.7 + 16)
                    );
                    nodes.add(heatMeterConsAbsoluteGeneratorNode);

                    NodeConnectionInfo heatMeterConsAbsoluteConnection = new NodeConnectionInfo();
                    heatMeterConsAbsoluteConnection.setType(NodeConnectionType.SUCCESS.getType());
                    heatMeterConsAbsoluteConnection.setFromIndex(index + 5);
                    heatMeterConsAbsoluteConnection.setToIndex(index);
                    connections.add(heatMeterConsAbsoluteConnection);
                }
            }

            RuleChainMetaData savedMetaData = tbRestClient.saveRuleChainMetadata(metaData);
        } catch (Exception e) {
            throw new RuntimeException("", e);
        }
    }


    private Set<String> getAllAssetNames(List<Building> data) {
        Set<String> building = data.stream()
                .map(Building::getSystemName)
                .collect(Collectors.toSet());

        Set<String> apartments = data.stream()
                .map(Building::getApartments)
                .flatMap(Collection::stream)
                .map(Apartment::getSystemName)
                .collect(Collectors.toSet());

        return Sets.union(building, apartments);
    }

    private Set<String> getAllDeviceNames(List<Building> data) {
        Set<String> energyMeters = data.stream()
                .map(Building::getApartments)
                .flatMap(Collection::stream)
                .map(Apartment::getEnergyMeter)
                .map(EnergyMeter::getSystemName)
                .collect(Collectors.toSet());

        Set<String> heatMeters = data.stream()
                .map(Building::getApartments)
                .flatMap(Collection::stream)
                .map(Apartment::getHeatMeter)
                .map(HeatMeter::getSystemName)
                .collect(Collectors.toSet());

        return Sets.union(energyMeters, heatMeters);
    }


    private Building makeAlpire(boolean skipTelemetry) {
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
                                .startDate(TS_2022_JANUARY)
                                .anomaly(false)
                                .area(2)
                                .build()
                )
                .build();

        configuration.setApartmentConfiguration(
                1, 2,
                ApartmentConfiguration.builder()
                        .occupied(false)
                        .level(0)
                        .startDate(TS_2022_JANUARY)
                        .anomaly(false)
                        .area(0)
                        .build()
        );

        configuration.setApartmentConfiguration(
                3, 1,
                ApartmentConfiguration.builder()
                        .occupied(false)
                        .level(0)
                        .startDate(TS_2022_JANUARY)
                        .anomaly(false)
                        .area(0)
                        .build()
        );

        configuration.setApartmentConfiguration(
                4, 1,
                ApartmentConfiguration.builder()
                        .occupied(true)
                        .level(3)
                        .startDate(TS_2022_JANUARY)
                        .anomaly(false)
                        .area(3)
                        .build()
        );

        configuration.setApartmentConfiguration(
                4, 2,
                ApartmentConfiguration.builder()
                        .occupied(true)
                        .level(3)
                        .startDate(TS_2022_JANUARY)
                        .anomaly(false)
                        .area(3)
                        .build()
        );

        configuration.setApartmentConfiguration(
                5, 1,
                ApartmentConfiguration.builder()
                        .occupied(false)
                        .level(0)
                        .startDate(TS_2022_FEBRUARY)
                        .anomaly(false)
                        .area(0)
                        .build()
        );

        configuration.setApartmentConfiguration(
                5, 2,
                ApartmentConfiguration.builder()
                        .occupied(false)
                        .level(0)
                        .startDate(TS_2022_FEBRUARY)
                        .anomaly(false)
                        .area(0)
                        .build()
        );

        return makeBuildingByConfiguration(configuration, skipTelemetry);
    }

    private Building makeFeline(boolean skipTelemetry) {
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
                                .startDate(TS_2022_MAY)
                                .anomaly(false)
                                .area(3)
                                .build()
                )
                .build();

        configuration.setApartmentConfiguration(
                1, 1,
                ApartmentConfiguration.builder()
                        .occupied(false)
                        .level(0)
                        .startDate(TS_2022_MAY)
                        .anomaly(true)
                        .area(0)
                        .build()
        );

        configuration.setApartmentConfiguration(
                1, 2,
                ApartmentConfiguration.builder()
                        .occupied(true)
                        .level(1)
                        .startDate(TS_2022_MAY)
                        .anomaly(false)
                        .area(1)
                        .build()
        );

        configuration.setApartmentConfiguration(
                1, 3,
                ApartmentConfiguration.builder()
                        .occupied(true)
                        .level(1)
                        .startDate(TS_2022_MAY)
                        .anomaly(false)
                        .area(1)
                        .build()
        );

        configuration.setApartmentConfiguration(
                2, 1,
                ApartmentConfiguration.builder()
                        .occupied(true)
                        .level(2)
                        .startDate(TS_2022_MAY)
                        .anomaly(false)
                        .area(2)
                        .build()
        );

        configuration.setApartmentConfiguration(
                2, 2,
                ApartmentConfiguration.builder()
                        .occupied(true)
                        .level(2)
                        .startDate(TS_2022_MAY)
                        .anomaly(false)
                        .area(2)
                        .build()
        );

        configuration.setApartmentConfiguration(
                2, 3,
                ApartmentConfiguration.builder()
                        .occupied(true)
                        .level(2)
                        .startDate(TS_2022_MAY)
                        .anomaly(false)
                        .area(2)
                        .build()
        );

        return makeBuildingByConfiguration(configuration, skipTelemetry);
    }

    private Building makeHogurity(boolean skipTelemetry) {
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
                                .startDate(TS_2022_JANUARY)
                                .anomaly(false)
                                .area(3)
                                .build()
                )
                .build();

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
        Telemetry<Long> energyMeterConsAbsolute = createTelemetryEnergyMeterConsAbsolute(energyMeterConsumption, skipTelemetry);

        Telemetry<Long> heatMeterTemperature = createTelemetryHeatMeterTemperature(configuration, skipTelemetry);
        Telemetry<Long> heatMeterConsumption = createTelemetryHeatMeterConsumption(configuration, skipTelemetry);
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
            case 0: return (int) RandomUtils.getRandomNumber(30, 300);
            case 1: return (int) RandomUtils.getRandomNumber(30, 60);
            case 2: return (int) RandomUtils.getRandomNumber(60, 150);
            case 3: return (int) RandomUtils.getRandomNumber(150, 300);
            default: throw new IllegalArgumentException("Unsupported level: " + level);
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
        boolean anomaly = configuration.isAnomaly();

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
        boolean anomaly = configuration.isAnomaly();

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
        tbRestClient.setEntityAttributes(asset.getUuidId(), EntityType.ASSET, Scope.SERVER_SCOPE, attributes);
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
        tbRestClient.setEntityAttributes(asset.getUuidId(), EntityType.ASSET, Scope.SERVER_SCOPE, attributes);
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
        tbRestClient.setEntityAttributes(device.getUuidId(), EntityType.DEVICE, Scope.SERVER_SCOPE, attributes);

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
        tbRestClient.setEntityAttributes(device.getUuidId(), EntityType.DEVICE, Scope.SERVER_SCOPE, attributes);

        tbRestClient.pushTelemetry(deviceCredentials.getCredentialsId(), heatMeter.getTemperature());
        tbRestClient.pushTelemetry(deviceCredentials.getCredentialsId(), heatMeter.getHeatConsumption());
        tbRestClient.pushTelemetry(deviceCredentials.getCredentialsId(), heatMeter.getHeatConsAbsolute());

        this.heatMeterIdMap.put(heatMeter, device.getUuidId());
        return device;
    }


    private RuleNode createSaveNode(String name, double gridIndexX, double gridIndexY) {
        TbMsgTimeseriesNodeConfiguration saveConfiguration = new TbMsgTimeseriesNodeConfiguration();
        saveConfiguration.setDefaultTTL(0);
        saveConfiguration.setUseServerTs(false);
        saveConfiguration.setSkipLatestPersistence(false);

        RuleNode saveNode = new RuleNode();
        saveNode.setName(name);
        saveNode.setType(TbMsgTimeseriesNode.class.getName());
        saveNode.setConfiguration(JsonUtils.makeNodeFromPojo(saveConfiguration));
        saveNode.setAdditionalInfo(
                RuleNodeAdditionalInfo.builder()
                        .description("Basic description")
                        .layoutX((int) Math.round(gridIndexX))
                        .layoutY((int) Math.round(gridIndexY))
                        .build()
                        .toJsonNode()
        );

        return saveNode;
    }

    private RuleNode createGeneratorMode(String name, UUID entityId, String fileName, double gridIndexX, double gridIndexY) throws IOException {
        TbMsgGeneratorNodeConfiguration generatorConfiguration = new TbMsgGeneratorNodeConfiguration();
        generatorConfiguration.setOriginatorType(EntityType.DEVICE);
        generatorConfiguration.setOriginatorId(entityId.toString());
        generatorConfiguration.setMsgCount(0);
        generatorConfiguration.setPeriodInSeconds(3600);
        generatorConfiguration.setJsScript(this.fileService.getFileContent(getSolutionName(), fileName));

        RuleNode generatorNode = new RuleNode();
        generatorNode.setName(name);
        generatorNode.setType(TbMsgGeneratorNode.class.getName());
        generatorNode.setConfiguration(JsonUtils.makeNodeFromPojo(generatorConfiguration));
        generatorNode.setAdditionalInfo(
                RuleNodeAdditionalInfo.builder()
                        .description("")
                        .layoutX((int) Math.round(gridIndexX))
                        .layoutY((int) Math.round(gridIndexY))
                        .build()
                        .toJsonNode()
        );

        return generatorNode;
    }


    private String getEnergyMeterConsumptionFile(boolean occupied, int level) {
        return occupied
                ? "energy_consumption_level" + level + ".js"
                : "energy_consumption_level0.js";
    }

    private String getEnergyMeterConsAbsoluteFile(boolean occupied, int level) {
        return occupied
                ? "energy_cons_absolute_level" + level + ".js"
                : "energy_cons_absolute_level0.js";
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

    private String getHeatMeterConsAbsoluteFile(boolean occupied, int level) {
        return occupied
                ? "heat_cons_absolute_level" + level + ".js"
                : "heat_cons_absolute_level0.js";
    }
}

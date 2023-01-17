package org.thingsboard.trendz.generator.solution.energymetering;

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
    private static final long dateRangeTo   = 10000000;
    private static final long serialRangeFrom = 10000;
    private static final long serialRangeTo   = 99999;

    private final TbRestClient tbRestClient;
    private final FileService fileService;
    private final VisualizationService visualizationService;

    private final Map<Apartment, ApartmentConfiguration> apartmentConfigurationMap = new HashMap<>();

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


            Set<String> allAssetNames = getAllAssetNames();
            Set<Asset> badAssets = tbRestClient.getAllAssets()
                    .stream()
                    .filter(asset -> allAssetNames.contains(asset.getName()))
                    .collect(Collectors.toSet());

            if (!badAssets.isEmpty()) {
                log.error("There are assets that already exists: {}", badAssets);
                throw new AssetAlreadyExistException(badAssets.iterator().next());
            }


            Set<String> allDeviceNames = getAllDeviceNames();
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

            List<Building> data = makeData();
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

            Set<String> allDeviceNames = getAllDeviceNames();
            tbRestClient.getAllDevices()
                    .stream()
                    .filter(device -> allDeviceNames.contains(device.getName()))
                    .forEach(device -> tbRestClient.deleteDevice(device.getUuidId()));

            Set<String> allAssetNames = getAllAssetNames();
            tbRestClient.getAllAssets()
                    .stream()
                    .filter(asset -> allAssetNames.contains(asset.getName()))
                    .forEach(asset -> tbRestClient.deleteAsset(asset.getUuidId()));

            log.info("Energy Metering Solution - removal is completed!");
        } catch (Exception e) {
            log.error("Energy Metering Solution removal was failed, skipping...", e);
        }
    }


    private List<Building> makeData() {
        Building alpire = makeAlpire();
        Building feline = makeFeline();
        Building hogurity = makeHogurity();

        return List.of(alpire, feline, hogurity);
    }

    private void visualizeData(List<Building> buildings) {
//        visualizationService.visualize();
    }

    private void applyData(List<Building> buildings, CustomerUser customerUser) {
        for (Building building : buildings) {
            Asset buildingAsset = createBuilding(building);
            tbRestClient.assignAssetToCustomer(customerUser.getCustomerId().getId(), buildingAsset.getUuidId());

            Set<Apartment> apartments = building.getApartments();
            for (Apartment apartment : apartments) {
                Asset apartmentAsset = createApartment(apartment);
                tbRestClient.assignAssetToCustomer(customerUser.getCustomerId().getId(), apartmentAsset.getUuidId());
                tbRestClient.createRelation(RelationType.CONTAINS.getType(), buildingAsset.getId(), apartmentAsset.getId());

                EnergyMeter energyMeter = apartment.getEnergyMeter();
                HeatMeter heatMeter = apartment.getHeatMeter();
                Device energyMeterDevice = createEnergyMeter(energyMeter);
                Device heatMeterDevice = createHeatMeter(heatMeter);
                tbRestClient.assignDeviceToCustomer(customerUser.getCustomerId().getId(), energyMeterDevice.getUuidId());
                tbRestClient.assignDeviceToCustomer(customerUser.getCustomerId().getId(), heatMeterDevice.getUuidId());
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

            RuleNode saveNode = createSaveNode();
            nodes.add(saveNode);

            int counter = 0;
            for (Building building : buildings) {
                for (Apartment apartment : building.getApartments()) {

                    EnergyMeter energyMeter = apartment.getEnergyMeter();
                    HeatMeter heatMeter = apartment.getHeatMeter();

                    RuleNode energyMeterGeneratorMode = createGeneratorMode(
                            energyMeter.getSystemName(),
                            energyMeter.getSystemId(), "level1.js",
                            RuleNodeAdditionalInfo.CELL_SIZE * 5, (5 + counter) * (RuleNodeAdditionalInfo.CELL_SIZE + 25)
                    );
                    NodeConnectionInfo energyMeterConnection = new NodeConnectionInfo();
                    energyMeterConnection.setType(NodeConnectionType.SUCCESS.getType());
                    energyMeterConnection.setFromIndex(counter * 2 + 1);
                    energyMeterConnection.setToIndex(0);

                    RuleNode heatMeterGeneratorMode = createGeneratorMode(
                            heatMeter.getSystemName(),
                            heatMeter.getSystemId(), "level1.js",
                            RuleNodeAdditionalInfo.CELL_SIZE * 25, (5 + counter) * (RuleNodeAdditionalInfo.CELL_SIZE + 25)
                    );
                    NodeConnectionInfo heatMeterConnection = new NodeConnectionInfo();
                    heatMeterConnection.setType(NodeConnectionType.SUCCESS.getType());
                    heatMeterConnection.setFromIndex(counter * 2 + 2);
                    heatMeterConnection.setToIndex(0);

                    nodes.add(energyMeterGeneratorMode);
                    nodes.add(heatMeterGeneratorMode);

                    connections.add(energyMeterConnection);
                    connections.add(heatMeterConnection);
                    counter++;
                }
            }

            RuleChainMetaData savedMetaData = tbRestClient.saveRuleChainMetadata(metaData);
        } catch (Exception e) {
            throw new RuntimeException("", e);
        }
    }


    private Set<String> getAllAssetNames() {
        return Set.of(
                // Buildings
                "Alpire", "Feline", "Hogurity",
                // Apartments
                "Apt 101 in Alpire", "Apt 102 in Alpire", "Apt 201 in Alpire", "Apt 202 in Alpire", "Apt 301 in Alpire",
                "Apt 302 in Alpire", "Apt 401 in Alpire", "Apt 402 in Alpire", "Apt 501 in Alpire", "Apt 502 in Alpire",
                "Apt 101 in Feline", "Apt 102 in Feline", "Apt 103 in Feline", "Apt 201 in Feline", "Apt 202 in Feline",
                "Apt 203 in Feline", "Apt 301 in Feline", "Apt 302 in Feline", "Apt 303 in Feline",
                "Apt 101 in Hogurity", "Apt 102 in Hogurity", "Apt 103 in Hogurity", "Apt 104 in Hogurity"
        );
    }

    private Set<String> getAllDeviceNames() {
        return Set.of(
                // Energy Meters
                "Energy Meter A101", "Energy Meter A102", "Energy Meter A201", "Energy Meter A202", "Energy Meter A301",
                "Energy Meter A302", "Energy Meter A401", "Energy Meter A402", "Energy Meter A501", "Energy Meter A502",
                "Energy Meter F101", "Energy Meter F102", "Energy Meter F103", "Energy Meter F201", "Energy Meter F202",
                "Energy Meter F203", "Energy Meter F301", "Energy Meter F302", "Energy Meter F303",
                "Energy Meter H101", "Energy Meter H102", "Energy Meter H103", "Energy Meter H104",

                // Heat Meters
                "Heat Meter A101", "Heat Meter A102", "Heat Meter A201", "Heat Meter A202", "Heat Meter A301",
                "Heat Meter A302", "Heat Meter A401", "Heat Meter A402", "Heat Meter A501", "Heat Meter A502",
                "Heat Meter F101", "Heat Meter F102", "Heat Meter F103", "Heat Meter F201", "Heat Meter F202",
                "Heat Meter F203", "Heat Meter F301", "Heat Meter F302", "Heat Meter F303",
                "Heat Meter H101", "Heat Meter H102", "Heat Meter H103", "Heat Meter H104"
        );
    }


    private Building makeAlpire() {
        Apartment ap11 = makeAlpireApartment11();
        Apartment ap12 = makeAlpireApartment12();
        Apartment ap21 = makeAlpireApartment21();
        Apartment ap22 = makeAlpireApartment22();
        Apartment ap31 = makeAlpireApartment31();
        Apartment ap32 = makeAlpireApartment32();
        Apartment ap41 = makeAlpireApartment41();
        Apartment ap42 = makeAlpireApartment42();
        Apartment ap51 = makeAlpireApartment51();
        Apartment ap52 = makeAlpireApartment52();

        return Building.builder()
                .systemName("Alpire")
                .systemLabel("Asset label for Alpire building")
                .address("USA, California, San Francisco, ...")
                .apartments(Set.of(ap11, ap12, ap21, ap22, ap31, ap32, ap41, ap42, ap51, ap52))
                .build();
    }

    private Building makeFeline() {
        Apartment ap11 = makeFelineApartment11();
        Apartment ap12 = makeFelineApartment12();
        Apartment ap13 = makeFelineApartment13();
        Apartment ap21 = makeFelineApartment21();
        Apartment ap22 = makeFelineApartment22();
        Apartment ap23 = makeFelineApartment23();
        Apartment ap31 = makeFelineApartment31();
        Apartment ap32 = makeFelineApartment32();
        Apartment ap33 = makeFelineApartment33();

        return Building.builder()
                .systemName("Feline")
                .systemLabel("Asset label for Feline building")
                .address("USA, New York, New York City, Brooklyn, ...")
                .apartments(Set.of(ap11, ap12, ap13, ap21, ap22, ap23, ap31, ap32, ap33))
                .build();
    }

    private Building makeHogurity() {
        Apartment ap11 = makeHogurityApartment11();
        Apartment ap12 = makeHogurityApartment12();
        Apartment ap13 = makeHogurityApartment13();
        Apartment ap14 = makeHogurityApartment14();

        return Building.builder()
                .systemName("Hogurity")
                .systemLabel("Asset label for Hogurity building")
                .address("USA, New York, New York City, Manhattan, ...")
                .apartments(Set.of(ap11, ap12, ap13, ap14))
                .build();
    }


    private Apartment makeAlpireApartment11() {
        long bias = createRandomDateBias();

        ApartmentConfiguration configuration = ApartmentConfiguration.builder()
                .occupied(true)
                .level(2)
                .startDate(TS_2022_JANUARY + bias)
                .anomaly(false)
                .build();

        return createApartmentByConfiguration(configuration, "Alpire", "101");
    }

    private Apartment makeAlpireApartment12() {
        long bias = createRandomDateBias();

        ApartmentConfiguration configuration = ApartmentConfiguration.builder()
                .occupied(false)
                .level(0)
                .startDate(TS_2022_JANUARY + bias)
                .anomaly(false)
                .build();

        return createApartmentByConfiguration(configuration, "Alpire", "102");
    }

    private Apartment makeAlpireApartment21() {
        long bias = createRandomDateBias();

        ApartmentConfiguration configuration = ApartmentConfiguration.builder()
                .occupied(true)
                .level(2)
                .startDate(TS_2022_JANUARY + bias)
                .anomaly(false)
                .build();

        return createApartmentByConfiguration(configuration, "Alpire", "201");
    }

    private Apartment makeAlpireApartment22() {
        long bias = createRandomDateBias();

        ApartmentConfiguration configuration = ApartmentConfiguration.builder()
                .occupied(false)
                .level(2)
                .startDate(TS_2022_JANUARY + bias)
                .anomaly(false)
                .build();

        return createApartmentByConfiguration(configuration, "Alpire", "202");
    }

    private Apartment makeAlpireApartment31() {
        long bias = createRandomDateBias();

        ApartmentConfiguration configuration = ApartmentConfiguration.builder()
                .occupied(false)
                .level(0)
                .startDate(TS_2022_JANUARY + bias)
                .anomaly(false)
                .build();

        return createApartmentByConfiguration(configuration, "Alpire", "301");
    }

    private Apartment makeAlpireApartment32() {
        long bias = createRandomDateBias();

        ApartmentConfiguration configuration = ApartmentConfiguration.builder()
                .occupied(true)
                .level(2)
                .startDate(TS_2022_JANUARY + bias)
                .anomaly(false)
                .build();

        return createApartmentByConfiguration(configuration, "Alpire", "302");
    }

    private Apartment makeAlpireApartment41() {
        long bias = createRandomDateBias();

        ApartmentConfiguration configuration = ApartmentConfiguration.builder()
                .occupied(true)
                .level(3)
                .startDate(TS_2022_JANUARY + bias)
                .anomaly(false)
                .build();

        return createApartmentByConfiguration(configuration, "Alpire", "401");
    }

    private Apartment makeAlpireApartment42() {
        long bias = createRandomDateBias();

        ApartmentConfiguration configuration = ApartmentConfiguration.builder()
                .occupied(true)
                .level(3)
                .startDate(TS_2022_JANUARY + bias)
                .anomaly(false)
                .build();

        return createApartmentByConfiguration(configuration, "Alpire", "402");
    }

    private Apartment makeAlpireApartment51() {
        long bias = createRandomDateBias();

        ApartmentConfiguration configuration = ApartmentConfiguration.builder()
                .occupied(false)
                .level(0)
                .startDate(TS_2022_FEBRUARY + bias)
                .anomaly(false)
                .build();

        return createApartmentByConfiguration(configuration, "Alpire", "501");
    }

    private Apartment makeAlpireApartment52() {
        long bias = createRandomDateBias();

        ApartmentConfiguration configuration = ApartmentConfiguration.builder()
                .occupied(false)
                .level(0)
                .startDate(TS_2022_FEBRUARY + bias)
                .anomaly(false)
                .build();

        return createApartmentByConfiguration(configuration, "Alpire", "502");
    }


    private Apartment makeFelineApartment11() {
        long bias = createRandomDateBias();

        ApartmentConfiguration configuration = ApartmentConfiguration.builder()
                .occupied(false)
                .level(0)
                .startDate(TS_2022_MAY + bias)
                .anomaly(true)
                .build();

        return createApartmentByConfiguration(configuration, "Feline", "101");
    }

    private Apartment makeFelineApartment12() {
        long bias = createRandomDateBias();

        ApartmentConfiguration configuration = ApartmentConfiguration.builder()
                .occupied(true)
                .level(1)
                .startDate(TS_2022_MAY + bias)
                .anomaly(false)
                .build();

        return createApartmentByConfiguration(configuration, "Feline", "102");
    }

    private Apartment makeFelineApartment13() {
        long bias = createRandomDateBias();

        ApartmentConfiguration configuration = ApartmentConfiguration.builder()
                .occupied(true)
                .level(1)
                .startDate(TS_2022_MAY + bias)
                .anomaly(false)
                .build();

        return createApartmentByConfiguration(configuration, "Feline", "103");
    }

    private Apartment makeFelineApartment21() {
        long bias = createRandomDateBias();

        ApartmentConfiguration configuration = ApartmentConfiguration.builder()
                .occupied(true)
                .level(2)
                .startDate(TS_2022_MAY + bias)
                .anomaly(false)
                .build();

        return createApartmentByConfiguration(configuration, "Feline", "201");
    }

    private Apartment makeFelineApartment22() {
        long bias = createRandomDateBias();

        ApartmentConfiguration configuration = ApartmentConfiguration.builder()
                .occupied(true)
                .level(2)
                .startDate(TS_2022_MAY + bias)
                .anomaly(false)
                .build();

        return createApartmentByConfiguration(configuration, "Feline", "202");
    }

    private Apartment makeFelineApartment23() {
        long bias = createRandomDateBias();

        ApartmentConfiguration configuration = ApartmentConfiguration.builder()
                .occupied(true)
                .level(2)
                .startDate(TS_2022_MAY + bias)
                .anomaly(false)
                .build();

        return createApartmentByConfiguration(configuration, "Feline", "203");
    }

    private Apartment makeFelineApartment31() {
        long bias = createRandomDateBias();

        ApartmentConfiguration configuration = ApartmentConfiguration.builder()
                .occupied(true)
                .level(3)
                .startDate(TS_2022_MAY + bias)
                .anomaly(false)
                .build();

        return createApartmentByConfiguration(configuration, "Feline", "301");
    }

    private Apartment makeFelineApartment32() {
        long bias = createRandomDateBias();

        ApartmentConfiguration configuration = ApartmentConfiguration.builder()
                .occupied(true)
                .level(3)
                .startDate(TS_2022_MAY + bias)
                .anomaly(false)
                .build();

        return createApartmentByConfiguration(configuration, "Feline", "302");
    }

    private Apartment makeFelineApartment33() {
        long bias = createRandomDateBias();

        ApartmentConfiguration configuration = ApartmentConfiguration.builder()
                .occupied(false)
                .level(0)
                .startDate(TS_2022_MAY + bias)
                .anomaly(false)
                .build();

        return createApartmentByConfiguration(configuration, "Feline", "303");
    }


    private Apartment makeHogurityApartment11() {
        long bias = createRandomDateBias();

        ApartmentConfiguration configuration = ApartmentConfiguration.builder()
                .occupied(true)
                .level(3)
                .startDate(TS_2022_JANUARY + bias)
                .anomaly(false)
                .build();

        return createApartmentByConfiguration(configuration, "Hogurity", "101");
    }

    private Apartment makeHogurityApartment12() {
        long bias = createRandomDateBias();

        ApartmentConfiguration configuration = ApartmentConfiguration.builder()
                .occupied(true)
                .level(3)
                .startDate(TS_2022_JANUARY + bias)
                .anomaly(false)
                .build();

        return createApartmentByConfiguration(configuration, "Hogurity", "102");
    }

    private Apartment makeHogurityApartment13() {
        long bias = createRandomDateBias();

        ApartmentConfiguration configuration = ApartmentConfiguration.builder()
                .occupied(true)
                .level(3)
                .startDate(TS_2022_MARCH + bias)
                .anomaly(false)
                .build();

        return createApartmentByConfiguration(configuration, "Hogurity", "103");
    }

    private Apartment makeHogurityApartment14() {
        long bias = createRandomDateBias();

        ApartmentConfiguration configuration = ApartmentConfiguration.builder()
                .occupied(true)
                .level(3)
                .startDate(TS_2022_JANUARY + bias)
                .anomaly(false)
                .build();

        return createApartmentByConfiguration(configuration, "Hogurity", "104");
    }


    private Apartment createApartmentByConfiguration(ApartmentConfiguration configuration, String buildingName, String number) {
        String letter = buildingName.substring(0, 1);

        Telemetry<Integer> energyMeterConsumption = createTelemetryEnergyMeterConsumption(configuration);
        Telemetry<Integer> energyMeterConsAbsolute = createTelemetryEnergyMeterConsAbsolute(energyMeterConsumption);

        Telemetry<Integer> heatMeterTemperature = createTelemetryHeatMeterTemperature(configuration);
        Telemetry<Integer> heatMeterConsumption = createTelemetryHeatMeterConsumption(heatMeterTemperature);
        Telemetry<Integer> heatMeterConsAbsolute = createTelemetryHeatMeterConsAbsolute(heatMeterConsumption);

        EnergyMeter energyMeter = EnergyMeter.builder()
                .systemName("Energy Meter " + letter + number)
                .systemLabel("")
                .serialNumber(createRandomSerialNumber())
                .installDate(configuration.getStartDate())
                .energyConsumption(energyMeterConsumption)
                .energyConsAbsolute(energyMeterConsAbsolute)
                .build();

        HeatMeter heatMeter = HeatMeter.builder()
                .systemName("Heat Meter " + letter + number)
                .systemLabel("")
                .serialNumber(createRandomSerialNumber())
                .installDate(configuration.getStartDate())
                .temperature(heatMeterTemperature)
                .heatConsumption(heatMeterConsumption)
                .heatConsAbsolute(heatMeterConsAbsolute)
                .build();

        Apartment apartment = Apartment.builder()
                .systemName("Apt " + number + " in " + buildingName)
                .systemLabel("")
                .floor(configuration.getFloor())
                .area(configuration.getArea())
                .roomNumber(configuration.getRoomNumber())
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

    private Telemetry<Integer> createTelemetryEnergyMeterConsumption(ApartmentConfiguration configuration) {
        Telemetry<Integer> result = new Telemetry<>("energyConsumption");
        return result;
    }

    private Telemetry<Integer> createTelemetryEnergyMeterConsAbsolute(Telemetry<Integer> energyConsumptionTelemetry) {
        Telemetry<Integer> result = new Telemetry<>("energyConsAbsolute");
        return result;
    }

    private Telemetry<Integer> createTelemetryHeatMeterTemperature(ApartmentConfiguration configuration) {
        Telemetry<Integer> result = new Telemetry<>("temperature");
        return result;
    }

    private Telemetry<Integer> createTelemetryHeatMeterConsumption(Telemetry<Integer> heatConsumptionTelemetry) {
        Telemetry<Integer> result = new Telemetry<>("heatConsumption");
        return result;
    }

    private Telemetry<Integer> createTelemetryHeatMeterConsAbsolute(Telemetry<Integer> heatConsumptionTelemetry) {
        Telemetry<Integer> result = new Telemetry<>("heatConsumption");
        return result;
    }


    private Asset createBuilding(Building building) {
        Asset asset = tbRestClient.createAsset(building.getSystemName(), "building");

        Set<Attribute<?>> attributes = Set.of(
                new Attribute<>("address", building.getAddress())
        );
        tbRestClient.setEntityAttributes(asset.getUuidId(), EntityType.ASSET, Scope.SERVER_SCOPE, attributes);

        building.setSystemId(asset.getUuidId());
        return asset;
    }

    private Asset createApartment(Apartment apartment) {
        Asset asset = tbRestClient.createAsset(apartment.getSystemName(), "apartment");

        Set<Attribute<?>> attributes = Set.of(
                new Attribute<>("floor", apartment.getFloor()),
                new Attribute<>("area", apartment.getArea()),
                new Attribute<>("roomNumber", apartment.getRoomNumber()),
                new Attribute<>("state", apartment.getState())
        );
        tbRestClient.setEntityAttributes(asset.getUuidId(), EntityType.ASSET, Scope.SERVER_SCOPE, attributes);

        apartment.setSystemId(asset.getUuidId());
        return asset;
    }

    private Device createEnergyMeter(EnergyMeter energyMeter) {
        Device device = tbRestClient.createDevice(energyMeter.getSystemName(), "energyMeter");
        DeviceCredentials deviceCredentials = tbRestClient.getDeviceCredentials(device.getUuidId());

        Set<Attribute<?>> attributes = Set.of(
                new Attribute<>("installDate", energyMeter.getInstallDate()),
                new Attribute<>("serialNumber", energyMeter.getSerialNumber())
        );
        tbRestClient.setEntityAttributes(device.getUuidId(), EntityType.DEVICE, Scope.SERVER_SCOPE, attributes);

        tbRestClient.pushTelemetry(deviceCredentials.getCredentialsId(), energyMeter.getEnergyConsumption());
        tbRestClient.pushTelemetry(deviceCredentials.getCredentialsId(), energyMeter.getEnergyConsAbsolute());

        energyMeter.setSystemId(device.getUuidId());
        return device;
    }

    private Device createHeatMeter(HeatMeter heatMeter) {
        Device device = tbRestClient.createDevice(heatMeter.getSystemName(), "heatMeter");
        DeviceCredentials deviceCredentials = tbRestClient.getDeviceCredentials(device.getUuidId());

        Set<Attribute<?>> attributes = Set.of(
                new Attribute<>("installDate", heatMeter.getInstallDate()),
                new Attribute<>("serialNumber", heatMeter.getSerialNumber())
        );
        tbRestClient.setEntityAttributes(device.getUuidId(), EntityType.DEVICE, Scope.SERVER_SCOPE, attributes);

        tbRestClient.pushTelemetry(deviceCredentials.getCredentialsId(), heatMeter.getHeatConsumption());
        tbRestClient.pushTelemetry(deviceCredentials.getCredentialsId(), heatMeter.getTemperature());

        heatMeter.setSystemId(device.getUuidId());
        return device;
    }


    private RuleNode createSaveNode() {
        TbMsgTimeseriesNodeConfiguration saveConfiguration = new TbMsgTimeseriesNodeConfiguration();
        saveConfiguration.setDefaultTTL(0);
        saveConfiguration.setUseServerTs(true);
        saveConfiguration.setSkipLatestPersistence(false);

        RuleNode saveNode = new RuleNode();
        saveNode.setName("EnergyMetering - save");
        saveNode.setType(TbMsgTimeseriesNode.class.getName());
        saveNode.setConfiguration(JsonUtils.makeNodeFromPojo(saveConfiguration));
        saveNode.setAdditionalInfo(
                RuleNodeAdditionalInfo.builder()
                        .description("Basic description")
                        .layoutX(RuleNodeAdditionalInfo.CELL_SIZE * 15)
                        .layoutY(RuleNodeAdditionalInfo.CELL_SIZE * 25)
                        .build()
                        .toJsonNode()
        );

        return saveNode;
    }

    private RuleNode createGeneratorMode(String name, UUID entityId, String fileName, int gridIndexX, int gridIndexY) throws IOException {
        TbMsgGeneratorNodeConfiguration generatorConfiguration = new TbMsgGeneratorNodeConfiguration();
        generatorConfiguration.setOriginatorType(EntityType.DEVICE);
        generatorConfiguration.setOriginatorId(entityId.toString());
        generatorConfiguration.setMsgCount(0);
        generatorConfiguration.setPeriodInSeconds(30);
        generatorConfiguration.setJsScript(this.fileService.getFileContent(getSolutionName(), fileName));

        RuleNode generatorNode = new RuleNode();
        generatorNode.setName(name);
        generatorNode.setType(TbMsgGeneratorNode.class.getName());
        generatorNode.setConfiguration(JsonUtils.makeNodeFromPojo(generatorConfiguration));
        generatorNode.setAdditionalInfo(
                RuleNodeAdditionalInfo.builder()
                        .description("")
                        .layoutX(gridIndexX)
                        .layoutY(gridIndexY)
                        .build()
                        .toJsonNode()
        );

        return generatorNode;
    }
}

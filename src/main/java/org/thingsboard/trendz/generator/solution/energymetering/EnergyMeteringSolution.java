package org.thingsboard.trendz.generator.solution.energymetering;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.thingsboard.server.common.data.Customer;
import org.thingsboard.server.common.data.Device;
import org.thingsboard.server.common.data.EntityType;
import org.thingsboard.server.common.data.asset.Asset;
import org.thingsboard.server.common.data.security.DeviceCredentials;
import org.thingsboard.trendz.generator.exception.AssetAlreadyExistException;
import org.thingsboard.trendz.generator.exception.CustomerAlreadyExistException;
import org.thingsboard.trendz.generator.exception.DeviceAlreadyExistException;
import org.thingsboard.trendz.generator.exception.RuleChainAlreadyExistException;
import org.thingsboard.trendz.generator.model.Attribute;
import org.thingsboard.trendz.generator.model.CustomerUser;
import org.thingsboard.trendz.generator.model.RelationType;
import org.thingsboard.trendz.generator.model.Scope;
import org.thingsboard.trendz.generator.model.Telemetry;
import org.thingsboard.trendz.generator.service.FileService;
import org.thingsboard.trendz.generator.service.TbRestClient;
import org.thingsboard.trendz.generator.service.VisualizationService;
import org.thingsboard.trendz.generator.solution.SolutionTemplateGenerator;
import org.thingsboard.trendz.generator.utils.DateTimeUtils;
import org.thingsboard.trendz.generator.utils.RandomUtils;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Set;
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
            applyData(data);

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

    private void applyData(List<Building> buildings) {
        for (Building building : buildings) {
            Asset buildingAsset = createBuilding(building);

            Set<Apartment> apartments = building.getApartments();
            for (Apartment apartment : apartments) {
                Asset apartmentAsset = createApartment(apartment);
                tbRestClient.createRelation(RelationType.CONTAINS.getType(), buildingAsset.getId(), apartmentAsset.getId());

                EnergyMeter energyMeter = apartment.getEnergyMeter();
                HeatMeter heatMeter = apartment.getHeatMeter();
                Device energyMeterDevice = createEnergyMeter(energyMeter);
                Device heatMeterDevice = createHeatMeter(heatMeter);
                tbRestClient.createRelation(RelationType.CONTAINS.getType(), apartmentAsset.getId(), energyMeterDevice.getId());
                tbRestClient.createRelation(RelationType.CONTAINS.getType(), apartmentAsset.getId(), heatMeterDevice.getId());
            }
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
        boolean occupancy = true;
        int level = 2;
        long startTime = TS_2022_JANUARY + RandomUtils.getRandomNumber(dateRangeFrom, dateRangeTo);
        boolean anomaly = false;

        Telemetry<Integer> energyMeterConsumption = createTelemetryEnergyMeterConsumption(occupancy, level, startTime, anomaly);
        Telemetry<Integer> energyMeterConsAbsolute = createTelemetryEnergyMeterConsAbsolute(energyMeterConsumption);

        Telemetry<Integer> heatMeterTemperature = createTelemetryHeatMeterTemperature(occupancy, level, startTime, anomaly);
        Telemetry<Integer> heatMeterConsumption = createTelemetryHeatMeterConsumption(heatMeterTemperature);
        Telemetry<Integer> heatMeterConsAbsolute = createTelemetryHeatMeterConsAbsolute(heatMeterConsumption);

        EnergyMeter energyMeter = EnergyMeter.builder()
                .systemName("Energy Meter A101")
                .systemLabel("")
                .serialNumber(RandomUtils.getRandomNumber(serialRangeFrom, serialRangeTo))
                .installDate(startTime)
                .energyConsumption(energyMeterConsumption)
                .energyConsAbsolute(energyMeterConsAbsolute)
                .build();

        HeatMeter heatMeter = HeatMeter.builder()
                .systemName("Heat Meter A101")
                .systemLabel("")
                .serialNumber(RandomUtils.getRandomNumber(serialRangeFrom, serialRangeTo))
                .installDate(startTime)
                .temperature(heatMeterTemperature)
                .heatConsumption(heatMeterConsumption)
                .heatConsAbsolute(heatMeterConsAbsolute)
                .build();

        return Apartment.builder()
                .systemName("Apt 101 in Alpire")
                .systemLabel("Apartment label")
                .floor(1)
                .area(0)
                .roomNumber(1)
                .state("occupied")
                .energyMeter(energyMeter)
                .heatMeter(heatMeter)
                .build();
    }

    private Apartment makeAlpireApartment12() {
        boolean occupancy = false;
        int level = 0;
        long startTime = TS_2022_JANUARY + RandomUtils.getRandomNumber(dateRangeFrom, dateRangeTo);
        boolean anomaly = false;

        Telemetry<Integer> energyMeterConsumption = createTelemetryEnergyMeterConsumption(occupancy, level, startTime, anomaly);
        Telemetry<Integer> energyMeterConsAbsolute = createTelemetryEnergyMeterConsAbsolute(energyMeterConsumption);

        Telemetry<Integer> heatMeterTemperature = createTelemetryHeatMeterTemperature(occupancy, level, startTime, anomaly);
        Telemetry<Integer> heatMeterConsumption = createTelemetryHeatMeterConsumption(heatMeterTemperature);
        Telemetry<Integer> heatMeterConsAbsolute = createTelemetryHeatMeterConsAbsolute(heatMeterConsumption);

        EnergyMeter energyMeter = EnergyMeter.builder()
                .systemName("Energy Meter A102")
                .systemLabel("")
                .serialNumber(RandomUtils.getRandomNumber(serialRangeFrom, serialRangeTo))
                .installDate(startTime)
                .energyConsumption(energyMeterConsumption)
                .energyConsAbsolute(energyMeterConsAbsolute)
                .build();

        HeatMeter heatMeter = HeatMeter.builder()
                .systemName("Heat Meter A102")
                .systemLabel("")
                .serialNumber(RandomUtils.getRandomNumber(serialRangeFrom, serialRangeTo))
                .installDate(startTime)
                .temperature(heatMeterTemperature)
                .heatConsumption(heatMeterConsumption)
                .heatConsAbsolute(heatMeterConsAbsolute)
                .build();

        return Apartment.builder()
                .systemName("Apt 102 in Alpire")
                .systemLabel("Apartment label")
                .floor(1)
                .area(0)
                .roomNumber(2)
                .state("free")
                .energyMeter(energyMeter)
                .heatMeter(heatMeter)
                .build();
    }

    private Apartment makeAlpireApartment21() {
        boolean occupancy = true;
        int level = 2;
        long startTime = TS_2022_JANUARY + RandomUtils.getRandomNumber(dateRangeFrom, dateRangeTo);
        boolean anomaly = false;

        Telemetry<Integer> energyMeterConsumption = createTelemetryEnergyMeterConsumption(occupancy, level, startTime, anomaly);
        Telemetry<Integer> energyMeterConsAbsolute = createTelemetryEnergyMeterConsAbsolute(energyMeterConsumption);

        Telemetry<Integer> heatMeterTemperature = createTelemetryHeatMeterTemperature(occupancy, level, startTime, anomaly);
        Telemetry<Integer> heatMeterConsumption = createTelemetryHeatMeterConsumption(heatMeterTemperature);
        Telemetry<Integer> heatMeterConsAbsolute = createTelemetryHeatMeterConsAbsolute(heatMeterConsumption);

        EnergyMeter energyMeter = EnergyMeter.builder()
                .systemName("Energy Meter A103")
                .systemLabel("")
                .serialNumber(RandomUtils.getRandomNumber(serialRangeFrom, serialRangeTo))
                .installDate(startTime)
                .energyConsumption(energyMeterConsumption)
                .energyConsAbsolute(energyMeterConsAbsolute)
                .build();

        HeatMeter heatMeter = HeatMeter.builder()
                .systemName("Heat Meter A103")
                .systemLabel("")
                .serialNumber(RandomUtils.getRandomNumber(serialRangeFrom, serialRangeTo))
                .installDate(startTime)
                .temperature(heatMeterTemperature)
                .heatConsumption(heatMeterConsumption)
                .heatConsAbsolute(heatMeterConsAbsolute)
                .build();

        return Apartment.builder()
                .systemName("Apt 201 in Alpire")
                .systemLabel("Apartment label")
                .floor(2)
                .area(0)
                .roomNumber(3)
                .state("occupied")
                .energyMeter(energyMeter)
                .heatMeter(heatMeter)
                .build();
    }

    private Apartment makeAlpireApartment22() {
        boolean occupancy = true;
        int level = 2;
        long startTime = TS_2022_JANUARY + RandomUtils.getRandomNumber(dateRangeFrom, dateRangeTo);
        boolean anomaly = false;

        Telemetry<Integer> energyMeterConsumption = createTelemetryEnergyMeterConsumption(occupancy, level, startTime, anomaly);
        Telemetry<Integer> energyMeterConsAbsolute = createTelemetryEnergyMeterConsAbsolute(energyMeterConsumption);

        Telemetry<Integer> heatMeterTemperature = createTelemetryHeatMeterTemperature(occupancy, level, startTime, anomaly);
        Telemetry<Integer> heatMeterConsumption = createTelemetryHeatMeterConsumption(heatMeterTemperature);
        Telemetry<Integer> heatMeterConsAbsolute = createTelemetryHeatMeterConsAbsolute(heatMeterConsumption);

        EnergyMeter energyMeter = EnergyMeter.builder()
                .systemName("Energy Meter A202")
                .systemLabel("")
                .serialNumber(RandomUtils.getRandomNumber(serialRangeFrom, serialRangeTo))
                .installDate(startTime)
                .energyConsumption(energyMeterConsumption)
                .energyConsAbsolute(energyMeterConsAbsolute)
                .build();

        HeatMeter heatMeter = HeatMeter.builder()
                .systemName("Heat Meter A202")
                .systemLabel("")
                .serialNumber(RandomUtils.getRandomNumber(serialRangeFrom, serialRangeTo))
                .installDate(startTime)
                .temperature(heatMeterTemperature)
                .heatConsumption(heatMeterConsumption)
                .heatConsAbsolute(heatMeterConsAbsolute)
                .build();

        return Apartment.builder()
                .systemName("Apt 202 in Alpire")
                .systemLabel("Apartment label")
                .floor(2)
                .area(0)
                .roomNumber(4)
                .state("occupied")
                .energyMeter(energyMeter)
                .heatMeter(heatMeter)
                .build();
    }

    private Apartment makeAlpireApartment31() {
        boolean occupancy = false;
        int level = 0;
        long startTime = TS_2022_JANUARY + RandomUtils.getRandomNumber(dateRangeFrom, dateRangeTo);
        boolean anomaly = false;

        Telemetry<Integer> energyMeterConsumption = createTelemetryEnergyMeterConsumption(occupancy, level, startTime, anomaly);
        Telemetry<Integer> energyMeterConsAbsolute = createTelemetryEnergyMeterConsAbsolute(energyMeterConsumption);

        Telemetry<Integer> heatMeterTemperature = createTelemetryHeatMeterTemperature(occupancy, level, startTime, anomaly);
        Telemetry<Integer> heatMeterConsumption = createTelemetryHeatMeterConsumption(heatMeterTemperature);
        Telemetry<Integer> heatMeterConsAbsolute = createTelemetryHeatMeterConsAbsolute(heatMeterConsumption);

        EnergyMeter energyMeter = EnergyMeter.builder()
                .systemName("Energy Meter A301")
                .systemLabel("")
                .serialNumber(RandomUtils.getRandomNumber(serialRangeFrom, serialRangeTo))
                .installDate(startTime)
                .energyConsumption(energyMeterConsumption)
                .energyConsAbsolute(energyMeterConsAbsolute)
                .build();

        HeatMeter heatMeter = HeatMeter.builder()
                .systemName("Heat Meter A301")
                .systemLabel("")
                .serialNumber(RandomUtils.getRandomNumber(serialRangeFrom, serialRangeTo))
                .installDate(startTime)
                .temperature(heatMeterTemperature)
                .heatConsumption(heatMeterConsumption)
                .heatConsAbsolute(heatMeterConsAbsolute)
                .build();

        return Apartment.builder()
                .systemName("Apt 301 in Alpire")
                .systemLabel("Apartment label")
                .floor(3)
                .area(0)
                .roomNumber(5)
                .state("free")
                .energyMeter(energyMeter)
                .heatMeter(heatMeter)
                .build();
    }

    private Apartment makeAlpireApartment32() {
        boolean occupancy = true;
        int level = 2;
        long startTime = TS_2022_JANUARY + RandomUtils.getRandomNumber(dateRangeFrom, dateRangeTo);
        boolean anomaly = false;

        Telemetry<Integer> energyMeterConsumption = createTelemetryEnergyMeterConsumption(occupancy, level, startTime, anomaly);
        Telemetry<Integer> energyMeterConsAbsolute = createTelemetryEnergyMeterConsAbsolute(energyMeterConsumption);

        Telemetry<Integer> heatMeterTemperature = createTelemetryHeatMeterTemperature(occupancy, level, startTime, anomaly);
        Telemetry<Integer> heatMeterConsumption = createTelemetryHeatMeterConsumption(heatMeterTemperature);
        Telemetry<Integer> heatMeterConsAbsolute = createTelemetryHeatMeterConsAbsolute(heatMeterConsumption);

        EnergyMeter energyMeter = EnergyMeter.builder()
                .systemName("Energy Meter A302")
                .systemLabel("")
                .serialNumber(RandomUtils.getRandomNumber(serialRangeFrom, serialRangeTo))
                .installDate(startTime)
                .energyConsumption(energyMeterConsumption)
                .energyConsAbsolute(energyMeterConsAbsolute)
                .build();

        HeatMeter heatMeter = HeatMeter.builder()
                .systemName("Heat Meter A302")
                .systemLabel("")
                .serialNumber(RandomUtils.getRandomNumber(serialRangeFrom, serialRangeTo))
                .installDate(startTime)
                .temperature(heatMeterTemperature)
                .heatConsumption(heatMeterConsumption)
                .heatConsAbsolute(heatMeterConsAbsolute)
                .build();

        return Apartment.builder()
                .systemName("Apt 302 in Alpire")
                .systemLabel("Apartment label")
                .floor(3)
                .area(0)
                .roomNumber(6)
                .state("occupied")
                .energyMeter(energyMeter)
                .heatMeter(heatMeter)
                .build();
    }

    private Apartment makeAlpireApartment41() {
        boolean occupancy = true;
        int level = 3;
        long startTime = TS_2022_JANUARY + RandomUtils.getRandomNumber(dateRangeFrom, dateRangeTo);
        boolean anomaly = false;

        Telemetry<Integer> energyMeterConsumption = createTelemetryEnergyMeterConsumption(occupancy, level, startTime, anomaly);
        Telemetry<Integer> energyMeterConsAbsolute = createTelemetryEnergyMeterConsAbsolute(energyMeterConsumption);

        Telemetry<Integer> heatMeterTemperature = createTelemetryHeatMeterTemperature(occupancy, level, startTime, anomaly);
        Telemetry<Integer> heatMeterConsumption = createTelemetryHeatMeterConsumption(heatMeterTemperature);
        Telemetry<Integer> heatMeterConsAbsolute = createTelemetryHeatMeterConsAbsolute(heatMeterConsumption);

        EnergyMeter energyMeter = EnergyMeter.builder()
                .systemName("Energy Meter A401")
                .systemLabel("")
                .serialNumber(RandomUtils.getRandomNumber(serialRangeFrom, serialRangeTo))
                .installDate(startTime)
                .energyConsumption(energyMeterConsumption)
                .energyConsAbsolute(energyMeterConsAbsolute)
                .build();

        HeatMeter heatMeter = HeatMeter.builder()
                .systemName("Heat Meter A401")
                .systemLabel("")
                .serialNumber(RandomUtils.getRandomNumber(serialRangeFrom, serialRangeTo))
                .installDate(startTime)
                .temperature(heatMeterTemperature)
                .heatConsumption(heatMeterConsumption)
                .heatConsAbsolute(heatMeterConsAbsolute)
                .build();

        return Apartment.builder()
                .systemName("Apt 401 in Alpire")
                .systemLabel("Apartment label")
                .floor(4)
                .area(0)
                .roomNumber(7)
                .state("occupied")
                .energyMeter(energyMeter)
                .heatMeter(heatMeter)
                .build();
    }

    private Apartment makeAlpireApartment42() {
        boolean occupancy = true;
        int level = 3;
        long startTime = TS_2022_JANUARY + RandomUtils.getRandomNumber(dateRangeFrom, dateRangeTo);
        boolean anomaly = false;

        Telemetry<Integer> energyMeterConsumption = createTelemetryEnergyMeterConsumption(occupancy, level, startTime, anomaly);
        Telemetry<Integer> energyMeterConsAbsolute = createTelemetryEnergyMeterConsAbsolute(energyMeterConsumption);

        Telemetry<Integer> heatMeterTemperature = createTelemetryHeatMeterTemperature(occupancy, level, startTime, anomaly);
        Telemetry<Integer> heatMeterConsumption = createTelemetryHeatMeterConsumption(heatMeterTemperature);
        Telemetry<Integer> heatMeterConsAbsolute = createTelemetryHeatMeterConsAbsolute(heatMeterConsumption);

        EnergyMeter energyMeter = EnergyMeter.builder()
                .systemName("Energy Meter A402")
                .systemLabel("")
                .serialNumber(RandomUtils.getRandomNumber(serialRangeFrom, serialRangeTo))
                .installDate(startTime)
                .energyConsumption(energyMeterConsumption)
                .energyConsAbsolute(energyMeterConsAbsolute)
                .build();

        HeatMeter heatMeter = HeatMeter.builder()
                .systemName("Heat Meter A402")
                .systemLabel("")
                .serialNumber(RandomUtils.getRandomNumber(serialRangeFrom, serialRangeTo))
                .installDate(startTime)
                .temperature(heatMeterTemperature)
                .heatConsumption(heatMeterConsumption)
                .heatConsAbsolute(heatMeterConsAbsolute)
                .build();

        return Apartment.builder()
                .systemName("Apt 402 in Alpire")
                .systemLabel("Apartment label")
                .floor(4)
                .area(0)
                .roomNumber(8)
                .state("occupied")
                .energyMeter(energyMeter)
                .heatMeter(heatMeter)
                .build();
    }

    private Apartment makeAlpireApartment51() {
        boolean occupancy = false;
        int level = 0;
        long startTime = TS_2022_FEBRUARY + RandomUtils.getRandomNumber(dateRangeFrom, dateRangeTo);
        boolean anomaly = false;

        Telemetry<Integer> energyMeterConsumption = createTelemetryEnergyMeterConsumption(occupancy, level, startTime, anomaly);
        Telemetry<Integer> energyMeterConsAbsolute = createTelemetryEnergyMeterConsAbsolute(energyMeterConsumption);

        Telemetry<Integer> heatMeterTemperature = createTelemetryHeatMeterTemperature(occupancy, level, startTime, anomaly);
        Telemetry<Integer> heatMeterConsumption = createTelemetryHeatMeterConsumption(heatMeterTemperature);
        Telemetry<Integer> heatMeterConsAbsolute = createTelemetryHeatMeterConsAbsolute(heatMeterConsumption);

        EnergyMeter energyMeter = EnergyMeter.builder()
                .systemName("Energy Meter A501")
                .systemLabel("")
                .serialNumber(RandomUtils.getRandomNumber(serialRangeFrom, serialRangeTo))
                .installDate(startTime)
                .energyConsumption(energyMeterConsumption)
                .energyConsAbsolute(energyMeterConsAbsolute)
                .build();

        HeatMeter heatMeter = HeatMeter.builder()
                .systemName("Heat Meter A501")
                .systemLabel("")
                .serialNumber(RandomUtils.getRandomNumber(serialRangeFrom, serialRangeTo))
                .installDate(startTime)
                .temperature(heatMeterTemperature)
                .heatConsumption(heatMeterConsumption)
                .heatConsAbsolute(heatMeterConsAbsolute)
                .build();

        return Apartment.builder()
                .systemName("Apt 501 in Alpire")
                .systemLabel("Apartment label")
                .floor(5)
                .area(0)
                .roomNumber(9)
                .state("free")
                .energyMeter(energyMeter)
                .heatMeter(heatMeter)
                .build();
    }

    private Apartment makeAlpireApartment52() {
        boolean occupancy = false;
        int level = 0;
        long startTime = TS_2022_FEBRUARY + RandomUtils.getRandomNumber(dateRangeFrom, dateRangeTo);
        boolean anomaly = false;

        Telemetry<Integer> energyMeterConsumption = createTelemetryEnergyMeterConsumption(occupancy, level, startTime, anomaly);
        Telemetry<Integer> energyMeterConsAbsolute = createTelemetryEnergyMeterConsAbsolute(energyMeterConsumption);

        Telemetry<Integer> heatMeterTemperature = createTelemetryHeatMeterTemperature(occupancy, level, startTime, anomaly);
        Telemetry<Integer> heatMeterConsumption = createTelemetryHeatMeterConsumption(heatMeterTemperature);
        Telemetry<Integer> heatMeterConsAbsolute = createTelemetryHeatMeterConsAbsolute(heatMeterConsumption);

        EnergyMeter energyMeter = EnergyMeter.builder()
                .systemName("Energy Meter A502")
                .systemLabel("")
                .serialNumber(RandomUtils.getRandomNumber(serialRangeFrom, serialRangeTo))
                .installDate(startTime)
                .energyConsumption(energyMeterConsumption)
                .energyConsAbsolute(energyMeterConsAbsolute)
                .build();

        HeatMeter heatMeter = HeatMeter.builder()
                .systemName("Heat Meter A502")
                .systemLabel("")
                .serialNumber(RandomUtils.getRandomNumber(serialRangeFrom, serialRangeTo))
                .installDate(startTime)
                .temperature(heatMeterTemperature)
                .heatConsumption(heatMeterConsumption)
                .heatConsAbsolute(heatMeterConsAbsolute)
                .build();

        return Apartment.builder()
                .systemName("Apt 502 in Alpire")
                .systemLabel("Apartment label")
                .floor(5)
                .area(0)
                .roomNumber(10)
                .state("free")
                .energyMeter(energyMeter)
                .heatMeter(heatMeter)
                .build();
    }


    private Apartment makeFelineApartment11() {
        boolean occupancy = false;
        int level = 0;
        long startTime = TS_2022_MAY + RandomUtils.getRandomNumber(dateRangeFrom, dateRangeTo);
        boolean anomaly = true;

        Telemetry<Integer> energyMeterConsumption = createTelemetryEnergyMeterConsumption(occupancy, level, startTime, anomaly);
        Telemetry<Integer> energyMeterConsAbsolute = createTelemetryEnergyMeterConsAbsolute(energyMeterConsumption);

        Telemetry<Integer> heatMeterTemperature = createTelemetryHeatMeterTemperature(occupancy, level, startTime, anomaly);
        Telemetry<Integer> heatMeterConsumption = createTelemetryHeatMeterConsumption(heatMeterTemperature);
        Telemetry<Integer> heatMeterConsAbsolute = createTelemetryHeatMeterConsAbsolute(heatMeterConsumption);

        EnergyMeter energyMeter = EnergyMeter.builder()
                .systemName("Energy Meter F101")
                .systemLabel("")
                .serialNumber(RandomUtils.getRandomNumber(serialRangeFrom, serialRangeTo))
                .installDate(startTime)
                .energyConsumption(energyMeterConsumption)
                .energyConsAbsolute(energyMeterConsAbsolute)
                .build();

        HeatMeter heatMeter = HeatMeter.builder()
                .systemName("Heat Meter F101")
                .systemLabel("")
                .serialNumber(RandomUtils.getRandomNumber(serialRangeFrom, serialRangeTo))
                .installDate(startTime)
                .temperature(heatMeterTemperature)
                .heatConsumption(heatMeterConsumption)
                .heatConsAbsolute(heatMeterConsAbsolute)
                .build();

        return Apartment.builder()
                .systemName("Apt 101 in Feline")
                .systemLabel("")
                .floor(1)
                .area(0)
                .roomNumber(1)
                .state("free")
                .energyMeter(energyMeter)
                .heatMeter(heatMeter)
                .build();
    }

    private Apartment makeFelineApartment12() {
        boolean occupancy = true;
        int level = 1;
        long startTime = TS_2022_MAY + RandomUtils.getRandomNumber(dateRangeFrom, dateRangeTo);
        boolean anomaly = false;

        Telemetry<Integer> energyMeterConsumption = createTelemetryEnergyMeterConsumption(occupancy, level, startTime, anomaly);
        Telemetry<Integer> energyMeterConsAbsolute = createTelemetryEnergyMeterConsAbsolute(energyMeterConsumption);

        Telemetry<Integer> heatMeterTemperature = createTelemetryHeatMeterTemperature(occupancy, level, startTime, anomaly);
        Telemetry<Integer> heatMeterConsumption = createTelemetryHeatMeterConsumption(heatMeterTemperature);
        Telemetry<Integer> heatMeterConsAbsolute = createTelemetryHeatMeterConsAbsolute(heatMeterConsumption);

        EnergyMeter energyMeter = EnergyMeter.builder()
                .systemName("Energy Meter F102")
                .systemLabel("")
                .serialNumber(RandomUtils.getRandomNumber(serialRangeFrom, serialRangeTo))
                .installDate(startTime)
                .energyConsumption(energyMeterConsumption)
                .energyConsAbsolute(energyMeterConsAbsolute)
                .build();

        HeatMeter heatMeter = HeatMeter.builder()
                .systemName("Heat Meter F102")
                .systemLabel("")
                .serialNumber(RandomUtils.getRandomNumber(serialRangeFrom, serialRangeTo))
                .installDate(startTime)
                .temperature(heatMeterTemperature)
                .heatConsumption(heatMeterConsumption)
                .heatConsAbsolute(heatMeterConsAbsolute)
                .build();

        return Apartment.builder()
                .systemName("Apt 102 in Feline")
                .systemLabel("")
                .floor(1)
                .area(0)
                .roomNumber(2)
                .state("occupied")
                .energyMeter(energyMeter)
                .heatMeter(heatMeter)
                .build();
    }

    private Apartment makeFelineApartment13() {
        boolean occupancy = true;
        int level = 1;
        long startTime = TS_2022_MAY + RandomUtils.getRandomNumber(dateRangeFrom, dateRangeTo);
        boolean anomaly = false;

        Telemetry<Integer> energyMeterConsumption = createTelemetryEnergyMeterConsumption(occupancy, level, startTime, anomaly);
        Telemetry<Integer> energyMeterConsAbsolute = createTelemetryEnergyMeterConsAbsolute(energyMeterConsumption);

        Telemetry<Integer> heatMeterTemperature = createTelemetryHeatMeterTemperature(occupancy, level, startTime, anomaly);
        Telemetry<Integer> heatMeterConsumption = createTelemetryHeatMeterConsumption(heatMeterTemperature);
        Telemetry<Integer> heatMeterConsAbsolute = createTelemetryHeatMeterConsAbsolute(heatMeterConsumption);

        EnergyMeter energyMeter = EnergyMeter.builder()
                .systemName("Energy Meter F103")
                .systemLabel("")
                .serialNumber(RandomUtils.getRandomNumber(serialRangeFrom, serialRangeTo))
                .installDate(startTime)
                .energyConsumption(energyMeterConsumption)
                .energyConsAbsolute(energyMeterConsAbsolute)
                .build();

        HeatMeter heatMeter = HeatMeter.builder()
                .systemName("Heat Meter F103")
                .systemLabel("")
                .serialNumber(RandomUtils.getRandomNumber(serialRangeFrom, serialRangeTo))
                .installDate(startTime)
                .temperature(heatMeterTemperature)
                .heatConsumption(heatMeterConsumption)
                .heatConsAbsolute(heatMeterConsAbsolute)
                .build();

        return Apartment.builder()
                .systemName("Apt 103 in Feline")
                .systemLabel("")
                .floor(1)
                .area(0)
                .roomNumber(3)
                .state("occupied")
                .energyMeter(energyMeter)
                .heatMeter(heatMeter)
                .build();
    }

    private Apartment makeFelineApartment21() {
        boolean occupancy = true;
        int level = 2;
        long startTime = TS_2022_MAY + RandomUtils.getRandomNumber(dateRangeFrom, dateRangeTo);
        boolean anomaly = false;

        Telemetry<Integer> energyMeterConsumption = createTelemetryEnergyMeterConsumption(occupancy, level, startTime, anomaly);
        Telemetry<Integer> energyMeterConsAbsolute = createTelemetryEnergyMeterConsAbsolute(energyMeterConsumption);

        Telemetry<Integer> heatMeterTemperature = createTelemetryHeatMeterTemperature(occupancy, level, startTime, anomaly);
        Telemetry<Integer> heatMeterConsumption = createTelemetryHeatMeterConsumption(heatMeterTemperature);
        Telemetry<Integer> heatMeterConsAbsolute = createTelemetryHeatMeterConsAbsolute(heatMeterConsumption);

        EnergyMeter energyMeter = EnergyMeter.builder()
                .systemName("Energy Meter F201")
                .systemLabel("")
                .serialNumber(RandomUtils.getRandomNumber(serialRangeFrom, serialRangeTo))
                .installDate(startTime)
                .energyConsumption(energyMeterConsumption)
                .energyConsAbsolute(energyMeterConsAbsolute)
                .build();

        HeatMeter heatMeter = HeatMeter.builder()
                .systemName("Heat Meter F201")
                .systemLabel("")
                .serialNumber(RandomUtils.getRandomNumber(serialRangeFrom, serialRangeTo))
                .installDate(startTime)
                .temperature(heatMeterTemperature)
                .heatConsumption(heatMeterConsumption)
                .heatConsAbsolute(heatMeterConsAbsolute)
                .build();

        return Apartment.builder()
                .systemName("Apt 201 in Feline")
                .systemLabel("")
                .floor(2)
                .area(0)
                .roomNumber(4)
                .state("occupied")
                .energyMeter(energyMeter)
                .heatMeter(heatMeter)
                .build();
    }

    private Apartment makeFelineApartment22() {
        boolean occupancy = true;
        int level = 2;
        long startTime = TS_2022_MAY + RandomUtils.getRandomNumber(dateRangeFrom, dateRangeTo);
        boolean anomaly = false;

        Telemetry<Integer> energyMeterConsumption = createTelemetryEnergyMeterConsumption(occupancy, level, startTime, anomaly);
        Telemetry<Integer> energyMeterConsAbsolute = createTelemetryEnergyMeterConsAbsolute(energyMeterConsumption);

        Telemetry<Integer> heatMeterTemperature = createTelemetryHeatMeterTemperature(occupancy, level, startTime, anomaly);
        Telemetry<Integer> heatMeterConsumption = createTelemetryHeatMeterConsumption(heatMeterTemperature);
        Telemetry<Integer> heatMeterConsAbsolute = createTelemetryHeatMeterConsAbsolute(heatMeterConsumption);

        EnergyMeter energyMeter = EnergyMeter.builder()
                .systemName("Energy Meter F202")
                .systemLabel("")
                .serialNumber(RandomUtils.getRandomNumber(serialRangeFrom, serialRangeTo))
                .installDate(startTime)
                .energyConsumption(energyMeterConsumption)
                .energyConsAbsolute(energyMeterConsAbsolute)
                .build();

        HeatMeter heatMeter = HeatMeter.builder()
                .systemName("Heat Meter F202")
                .systemLabel("")
                .serialNumber(RandomUtils.getRandomNumber(serialRangeFrom, serialRangeTo))
                .installDate(startTime)
                .temperature(heatMeterTemperature)
                .heatConsumption(heatMeterConsumption)
                .heatConsAbsolute(heatMeterConsAbsolute)
                .build();

        return Apartment.builder()
                .systemName("Apt 202 in Feline")
                .systemLabel("")
                .floor(2)
                .area(0)
                .roomNumber(5)
                .state("occupied")
                .energyMeter(energyMeter)
                .heatMeter(heatMeter)
                .build();
    }

    private Apartment makeFelineApartment23() {
        boolean occupancy = true;
        int level = 2;
        long startTime = TS_2022_MAY + RandomUtils.getRandomNumber(dateRangeFrom, dateRangeTo);
        boolean anomaly = false;

        Telemetry<Integer> energyMeterConsumption = createTelemetryEnergyMeterConsumption(occupancy, level, startTime, anomaly);
        Telemetry<Integer> energyMeterConsAbsolute = createTelemetryEnergyMeterConsAbsolute(energyMeterConsumption);

        Telemetry<Integer> heatMeterTemperature = createTelemetryHeatMeterTemperature(occupancy, level, startTime, anomaly);
        Telemetry<Integer> heatMeterConsumption = createTelemetryHeatMeterConsumption(heatMeterTemperature);
        Telemetry<Integer> heatMeterConsAbsolute = createTelemetryHeatMeterConsAbsolute(heatMeterConsumption);

        EnergyMeter energyMeter = EnergyMeter.builder()
                .systemName("Energy Meter F203")
                .systemLabel("")
                .serialNumber(RandomUtils.getRandomNumber(serialRangeFrom, serialRangeTo))
                .installDate(startTime)
                .energyConsumption(energyMeterConsumption)
                .energyConsAbsolute(energyMeterConsAbsolute)
                .build();

        HeatMeter heatMeter = HeatMeter.builder()
                .systemName("Heat Meter F203")
                .systemLabel("")
                .serialNumber(RandomUtils.getRandomNumber(serialRangeFrom, serialRangeTo))
                .installDate(startTime)
                .temperature(heatMeterTemperature)
                .heatConsumption(heatMeterConsumption)
                .heatConsAbsolute(heatMeterConsAbsolute)
                .build();

        return Apartment.builder()
                .systemName("Apt 203 in Feline")
                .systemLabel("")
                .floor(2)
                .area(0)
                .roomNumber(6)
                .state("occupied")
                .energyMeter(energyMeter)
                .heatMeter(heatMeter)
                .build();
    }

    private Apartment makeFelineApartment31() {
        boolean occupancy = true;
        int level = 3;
        long startTime = TS_2022_MAY + RandomUtils.getRandomNumber(dateRangeFrom, dateRangeTo);
        boolean anomaly = false;

        Telemetry<Integer> energyMeterConsumption = createTelemetryEnergyMeterConsumption(occupancy, level, startTime, anomaly);
        Telemetry<Integer> energyMeterConsAbsolute = createTelemetryEnergyMeterConsAbsolute(energyMeterConsumption);

        Telemetry<Integer> heatMeterTemperature = createTelemetryHeatMeterTemperature(occupancy, level, startTime, anomaly);
        Telemetry<Integer> heatMeterConsumption = createTelemetryHeatMeterConsumption(heatMeterTemperature);
        Telemetry<Integer> heatMeterConsAbsolute = createTelemetryHeatMeterConsAbsolute(heatMeterConsumption);

        EnergyMeter energyMeter = EnergyMeter.builder()
                .systemName("Energy Meter F301")
                .systemLabel("")
                .serialNumber(RandomUtils.getRandomNumber(serialRangeFrom, serialRangeTo))
                .installDate(startTime)
                .energyConsumption(energyMeterConsumption)
                .energyConsAbsolute(energyMeterConsAbsolute)
                .build();

        HeatMeter heatMeter = HeatMeter.builder()
                .systemName("Heat Meter F301")
                .systemLabel("")
                .serialNumber(RandomUtils.getRandomNumber(serialRangeFrom, serialRangeTo))
                .installDate(startTime)
                .temperature(heatMeterTemperature)
                .heatConsumption(heatMeterConsumption)
                .heatConsAbsolute(heatMeterConsAbsolute)
                .build();

        return Apartment.builder()
                .systemName("Apt 301 in Feline")
                .systemLabel("")
                .floor(3)
                .area(0)
                .roomNumber(7)
                .state("occupied")
                .energyMeter(energyMeter)
                .heatMeter(heatMeter)
                .build();
    }

    private Apartment makeFelineApartment32() {
        boolean occupancy = true;
        int level = 3;
        long startTime = TS_2022_MAY + RandomUtils.getRandomNumber(dateRangeFrom, dateRangeTo);
        boolean anomaly = false;

        Telemetry<Integer> energyMeterConsumption = createTelemetryEnergyMeterConsumption(occupancy, level, startTime, anomaly);
        Telemetry<Integer> energyMeterConsAbsolute = createTelemetryEnergyMeterConsAbsolute(energyMeterConsumption);

        Telemetry<Integer> heatMeterTemperature = createTelemetryHeatMeterTemperature(occupancy, level, startTime, anomaly);
        Telemetry<Integer> heatMeterConsumption = createTelemetryHeatMeterConsumption(heatMeterTemperature);
        Telemetry<Integer> heatMeterConsAbsolute = createTelemetryHeatMeterConsAbsolute(heatMeterConsumption);

        EnergyMeter energyMeter = EnergyMeter.builder()
                .systemName("Energy Meter F302")
                .systemLabel("")
                .serialNumber(RandomUtils.getRandomNumber(serialRangeFrom, serialRangeTo))
                .installDate(startTime)
                .energyConsumption(energyMeterConsumption)
                .energyConsAbsolute(energyMeterConsAbsolute)
                .build();

        HeatMeter heatMeter = HeatMeter.builder()
                .systemName("Heat Meter F302")
                .systemLabel("")
                .serialNumber(RandomUtils.getRandomNumber(serialRangeFrom, serialRangeTo))
                .installDate(startTime)
                .temperature(heatMeterTemperature)
                .heatConsumption(heatMeterConsumption)
                .heatConsAbsolute(heatMeterConsAbsolute)
                .build();

        return Apartment.builder()
                .systemName("Apt 301 in Feline")
                .systemLabel("")
                .floor(3)
                .area(0)
                .roomNumber(8)
                .state("occupied")
                .energyMeter(energyMeter)
                .heatMeter(heatMeter)
                .build();
    }

    private Apartment makeFelineApartment33() {
        boolean occupancy = false;
        int level = 0;
        long startTime = TS_2022_MAY + RandomUtils.getRandomNumber(dateRangeFrom, dateRangeTo);
        boolean anomaly = false;

        Telemetry<Integer> energyMeterConsumption = createTelemetryEnergyMeterConsumption(occupancy, level, startTime, anomaly);
        Telemetry<Integer> energyMeterConsAbsolute = createTelemetryEnergyMeterConsAbsolute(energyMeterConsumption);

        Telemetry<Integer> heatMeterTemperature = createTelemetryHeatMeterTemperature(occupancy, level, startTime, anomaly);
        Telemetry<Integer> heatMeterConsumption = createTelemetryHeatMeterConsumption(heatMeterTemperature);
        Telemetry<Integer> heatMeterConsAbsolute = createTelemetryHeatMeterConsAbsolute(heatMeterConsumption);

        EnergyMeter energyMeter = EnergyMeter.builder()
                .systemName("Energy Meter F303")
                .systemLabel("")
                .serialNumber(RandomUtils.getRandomNumber(serialRangeFrom, serialRangeTo))
                .installDate(startTime)
                .energyConsumption(energyMeterConsumption)
                .energyConsAbsolute(energyMeterConsAbsolute)
                .build();

        HeatMeter heatMeter = HeatMeter.builder()
                .systemName("Heat Meter F303")
                .systemLabel("")
                .serialNumber(RandomUtils.getRandomNumber(serialRangeFrom, serialRangeTo))
                .installDate(startTime)
                .temperature(heatMeterTemperature)
                .heatConsumption(heatMeterConsumption)
                .heatConsAbsolute(heatMeterConsAbsolute)
                .build();

        return Apartment.builder()
                .systemName("Apt 303 in Feline")
                .systemLabel("")
                .floor(3)
                .area(0)
                .roomNumber(9)
                .state("free")
                .energyMeter(energyMeter)
                .heatMeter(heatMeter)
                .build();
    }


    private Apartment makeHogurityApartment11() {
        boolean occupancy = true;
        int level = 3;
        long startTime = TS_2022_JANUARY + RandomUtils.getRandomNumber(dateRangeFrom, dateRangeTo);
        boolean anomaly = false;

        Telemetry<Integer> energyMeterConsumption = createTelemetryEnergyMeterConsumption(occupancy, level, startTime, anomaly);
        Telemetry<Integer> energyMeterConsAbsolute = createTelemetryEnergyMeterConsAbsolute(energyMeterConsumption);

        Telemetry<Integer> heatMeterTemperature = createTelemetryHeatMeterTemperature(occupancy, level, startTime, anomaly);
        Telemetry<Integer> heatMeterConsumption = createTelemetryHeatMeterConsumption(heatMeterTemperature);
        Telemetry<Integer> heatMeterConsAbsolute = createTelemetryHeatMeterConsAbsolute(heatMeterConsumption);

        EnergyMeter energyMeter = EnergyMeter.builder()
                .systemName("Energy Meter H101")
                .systemLabel("")
                .serialNumber(RandomUtils.getRandomNumber(serialRangeFrom, serialRangeTo))
                .installDate(startTime)
                .energyConsumption(energyMeterConsumption)
                .energyConsAbsolute(energyMeterConsAbsolute)
                .build();

        HeatMeter heatMeter = HeatMeter.builder()
                .systemName("Heat Meter H101")
                .systemLabel("")
                .serialNumber(RandomUtils.getRandomNumber(serialRangeFrom, serialRangeTo))
                .installDate(startTime)
                .temperature(heatMeterTemperature)
                .heatConsumption(heatMeterConsumption)
                .heatConsAbsolute(heatMeterConsAbsolute)
                .build();

        return Apartment.builder()
                .systemName("Apt 101 in Hogurity")
                .systemLabel("")
                .floor(1)
                .area(0)
                .roomNumber(1)
                .state("occupied")
                .energyMeter(energyMeter)
                .heatMeter(heatMeter)
                .build();
    }

    private Apartment makeHogurityApartment12() {
        boolean occupancy = true;
        int level = 3;
        long startTime = TS_2022_JANUARY + RandomUtils.getRandomNumber(dateRangeFrom, dateRangeTo);
        boolean anomaly = false;

        Telemetry<Integer> energyMeterConsumption = createTelemetryEnergyMeterConsumption(occupancy, level, startTime, anomaly);
        Telemetry<Integer> energyMeterConsAbsolute = createTelemetryEnergyMeterConsAbsolute(energyMeterConsumption);

        Telemetry<Integer> heatMeterTemperature = createTelemetryHeatMeterTemperature(occupancy, level, startTime, anomaly);
        Telemetry<Integer> heatMeterConsumption = createTelemetryHeatMeterConsumption(heatMeterTemperature);
        Telemetry<Integer> heatMeterConsAbsolute = createTelemetryHeatMeterConsAbsolute(heatMeterConsumption);

        EnergyMeter energyMeter = EnergyMeter.builder()
                .systemName("Energy Meter H102")
                .systemLabel("")
                .serialNumber(RandomUtils.getRandomNumber(serialRangeFrom, serialRangeTo))
                .installDate(startTime)
                .energyConsumption(energyMeterConsumption)
                .energyConsAbsolute(energyMeterConsAbsolute)
                .build();

        HeatMeter heatMeter = HeatMeter.builder()
                .systemName("Heat Meter H102")
                .systemLabel("")
                .serialNumber(RandomUtils.getRandomNumber(serialRangeFrom, serialRangeTo))
                .installDate(startTime)
                .temperature(heatMeterTemperature)
                .heatConsumption(heatMeterConsumption)
                .heatConsAbsolute(heatMeterConsAbsolute)
                .build();

        return Apartment.builder()
                .systemName("Apt 102 in Hogurity")
                .systemLabel("")
                .floor(1)
                .area(0)
                .roomNumber(2)
                .state("occupied")
                .energyMeter(energyMeter)
                .heatMeter(heatMeter)
                .build();
    }

    private Apartment makeHogurityApartment13() {
        boolean occupancy = true;
        int level = 3;
        long startTime = TS_2022_MARCH;
        boolean anomaly = false;

        Telemetry<Integer> energyMeterConsumption = createTelemetryEnergyMeterConsumption(occupancy, level, startTime, anomaly);
        Telemetry<Integer> energyMeterConsAbsolute = createTelemetryEnergyMeterConsAbsolute(energyMeterConsumption);

        Telemetry<Integer> heatMeterTemperature = createTelemetryHeatMeterTemperature(occupancy, level, startTime, anomaly);
        Telemetry<Integer> heatMeterConsumption = createTelemetryHeatMeterConsumption(heatMeterTemperature);
        Telemetry<Integer> heatMeterConsAbsolute = createTelemetryHeatMeterConsAbsolute(heatMeterConsumption);

        EnergyMeter energyMeter = EnergyMeter.builder()
                .systemName("Energy Meter H103")
                .systemLabel("")
                .serialNumber(RandomUtils.getRandomNumber(serialRangeFrom, serialRangeTo))
                .installDate(startTime)
                .energyConsumption(energyMeterConsumption)
                .energyConsAbsolute(energyMeterConsAbsolute)
                .build();

        HeatMeter heatMeter = HeatMeter.builder()
                .systemName("Heat Meter H103")
                .systemLabel("")
                .serialNumber(RandomUtils.getRandomNumber(serialRangeFrom, serialRangeTo))
                .installDate(startTime)
                .temperature(heatMeterTemperature)
                .heatConsumption(heatMeterConsumption)
                .heatConsAbsolute(heatMeterConsAbsolute)
                .build();

        return Apartment.builder()
                .systemName("Apt 103 in Hogurity")
                .systemLabel("")
                .floor(1)
                .area(0)
                .roomNumber(3)
                .state("occupied")
                .energyMeter(energyMeter)
                .heatMeter(heatMeter)
                .build();
    }

    private Apartment makeHogurityApartment14() {
        boolean occupancy = true;
        int level = 3;
        long startTime = TS_2022_JANUARY + RandomUtils.getRandomNumber(dateRangeFrom, dateRangeTo);
        boolean anomaly = false;

        Telemetry<Integer> energyMeterConsumption = createTelemetryEnergyMeterConsumption(occupancy, level, startTime, anomaly);
        Telemetry<Integer> energyMeterConsAbsolute = createTelemetryEnergyMeterConsAbsolute(energyMeterConsumption);

        Telemetry<Integer> heatMeterTemperature = createTelemetryHeatMeterTemperature(occupancy, level, startTime, anomaly);
        Telemetry<Integer> heatMeterConsumption = createTelemetryHeatMeterConsumption(heatMeterTemperature);
        Telemetry<Integer> heatMeterConsAbsolute = createTelemetryHeatMeterConsAbsolute(heatMeterConsumption);

        EnergyMeter energyMeter = EnergyMeter.builder()
                .systemName("Energy Meter H104")
                .systemLabel("")
                .serialNumber(RandomUtils.getRandomNumber(serialRangeFrom, serialRangeTo))
                .installDate(startTime)
                .energyConsumption(energyMeterConsumption)
                .energyConsAbsolute(energyMeterConsAbsolute)
                .build();

        HeatMeter heatMeter = HeatMeter.builder()
                .systemName("Heat Meter H104")
                .systemLabel("")
                .serialNumber(RandomUtils.getRandomNumber(serialRangeFrom, serialRangeTo))
                .installDate(startTime)
                .temperature(heatMeterTemperature)
                .heatConsumption(heatMeterConsumption)
                .heatConsAbsolute(heatMeterConsAbsolute)
                .build();

        return Apartment.builder()
                .systemName("Apt 104 in Hogurity")
                .systemLabel("")
                .floor(1)
                .area(0)
                .roomNumber(4)
                .state("occupied")
                .energyMeter(energyMeter)
                .heatMeter(heatMeter)
                .build();
    }


    private Telemetry<Integer> createTelemetryEnergyMeterConsumption(boolean occupied, int level, long startTs, boolean hasAnomaly) {
        Telemetry<Integer> result = new Telemetry<>("energyConsumption");
        return result;
    }

    private Telemetry<Integer> createTelemetryEnergyMeterConsAbsolute(Telemetry<Integer> energyConsumptionTelemetry) {
        Telemetry<Integer> result = new Telemetry<>("energyConsAbsolute");
        return result;
    }

    private Telemetry<Integer> createTelemetryHeatMeterTemperature(boolean occupied, int level, long startTs, boolean hasAnomaly) {
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
        return device;
    }
}

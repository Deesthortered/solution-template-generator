package org.thingsboard.trendz.generator.solution.greenhouse;

import com.google.common.base.Functions;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.thingsboard.server.common.data.Customer;
import org.thingsboard.server.common.data.Device;
import org.thingsboard.server.common.data.EntityType;
import org.thingsboard.server.common.data.asset.Asset;
import org.thingsboard.server.common.data.group.EntityGroup;
import org.thingsboard.server.common.data.id.AssetId;
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
import org.thingsboard.trendz.generator.model.tb.Attribute;
import org.thingsboard.trendz.generator.model.tb.CustomerData;
import org.thingsboard.trendz.generator.model.tb.CustomerUser;
import org.thingsboard.trendz.generator.model.tb.RelationType;
import org.thingsboard.trendz.generator.model.tb.RuleNodeAdditionalInfo;
import org.thingsboard.trendz.generator.model.tb.Telemetry;
import org.thingsboard.trendz.generator.model.tb.Timestamp;
import org.thingsboard.trendz.generator.service.FileService;
import org.thingsboard.trendz.generator.service.anomaly.AnomalyService;
import org.thingsboard.trendz.generator.service.dashboard.DashboardService;
import org.thingsboard.trendz.generator.service.rest.TbRestClient;
import org.thingsboard.trendz.generator.service.roolchain.RuleChainBuildingService;
import org.thingsboard.trendz.generator.solution.SolutionTemplateGenerator;
import org.thingsboard.trendz.generator.solution.greenhouse.configuration.GreenhouseConfiguration;
import org.thingsboard.trendz.generator.solution.greenhouse.configuration.StationCity;
import org.thingsboard.trendz.generator.solution.greenhouse.configuration.WeatherData;
import org.thingsboard.trendz.generator.solution.greenhouse.model.*;
import org.thingsboard.trendz.generator.utils.DateTimeUtils;
import org.thingsboard.trendz.generator.utils.MySortedSet;
import org.thingsboard.trendz.generator.utils.RandomUtils;

import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.nio.file.Path;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@Slf4j
@Service
public class GreenhouseSolution implements SolutionTemplateGenerator {

    private static final String CUSTOMER_TITLE = "Greenhouse Customer";
    private static final String CUSTOMER_USER_EMAIL = "greenhouse@thingsboard.io";
    private static final String CUSTOMER_USER_PASSWORD = "password";
    private static final String CUSTOMER_USER_FIRST_NAME = "Greenhouse Solution";
    private static final String CUSTOMER_USER_LAST_NAME = "";

    private static final String ASSET_GROUP_NAME = "Greenhouse Asset Group";
    private static final String DEVICE_GROUP_NAME = "Greenhouse Device Group";
    private static final String RULE_CHAIN_NAME = "Greenhouse Rule Chain";

    private static final String WEATHER_API_TOKEN = "80b7f53404dd4f058b0b156ed5dedc3f";
    private static final String WEATHER_API_URL = "https://api.openweathermap.org/data/2.5/weather?lat=${latitude}&lon=${longitude}&appid=${appId}&units=metric";

    private final TbRestClient tbRestClient;
    private final FileService fileService;
    private final AnomalyService anomalyService;
    private final RuleChainBuildingService ruleChainBuildingService;
    private final DashboardService dashboardService;

    private final Map<Greenhouse, UUID> greenhouseToIdMap = new HashMap<>();
    private final Map<Plant, UUID> plantToIdMap = new HashMap<>();
    private final Map<SoilNpkSensor, UUID> soilNpkSensorToIdMap = new HashMap<>();
    private final Map<SoilWarmMoistureSensor, UUID> soilWarmMoistureSensorToIdMap = new HashMap<>();
    private final Map<SoilAciditySensor, UUID> soilAciditySensorToIdMap = new HashMap<>();
    private final Map<InsideAirWarmHumiditySensor, UUID> insideAirWarmHumiditySensorToIdMap = new HashMap<>();
    private final Map<InsideCO2Sensor, UUID> insideCO2SensorToIdMap = new HashMap<>();
    private final Map<InsideLightSensor, UUID> insideLightSensorToIdMap = new HashMap<>();
    private final Map<HarvestReporter, UUID> harvestReporterToIdMap = new HashMap<>();
    private final Map<EnergyMeter, UUID> energyMeterToIdMap = new HashMap<>();
    private final Map<WaterMeter, UUID> waterMeterToIdMap = new HashMap<>();
    private final Map<OutsideAirWarmHumiditySensor, UUID> outsideAirWarmHumiditySensorToIdMap = new HashMap<>();
    private final Map<OutsideLightSensor, UUID> outsideLightSensorToIdMap = new HashMap<>();


    @Autowired
    public GreenhouseSolution(
            TbRestClient tbRestClient,
            FileService fileService,
            AnomalyService anomalyService,
            RuleChainBuildingService ruleChainBuildingService,
            DashboardService dashboardService
    ) {
        this.tbRestClient = tbRestClient;
        this.fileService = fileService;
        this.anomalyService = anomalyService;
        this.ruleChainBuildingService = ruleChainBuildingService;
        this.dashboardService = dashboardService;
    }

    @Override
    public String getSolutionName() {
        return "Greenhouse";
    }

    @Override
    public void validate() {
        try {
            log.info("Greenhouse Solution - start validation");

            validateCustomerData();
            validateRuleChain();

            if (!tbRestClient.isPe()) {
                dashboardService.validateDashboardItems(getSolutionName(), null);
                ModelData data = makeData(true, ZonedDateTime.now());
                validateData(data);
            }

            log.info("Greenhouse Solution - validation is completed!");
        } catch (Exception e) {
            throw new SolutionValidationException(getSolutionName(), e);
        }
    }

    @Override
    public void generate(boolean skipTelemetry, ZonedDateTime startYear) {
        log.info("Greenhouse Solution - start generation");
        try {
            CustomerData customerData = createCustomerData();
            ModelData data = makeData(skipTelemetry, startYear);
            applyData(data, customerData);
            createRuleChain(data);
            dashboardService.createDashboardItems(getSolutionName(), customerData.getCustomer().getId());

            log.info("Greenhouse Solution - generation is completed!");
        } catch (Exception e) {
            log.error("Greenhouse Solution generate was failed, skipping...", e);
        }
    }

    @Override
    public void remove() {
        log.info("Greenhouse Solution - start removal");
        try {
            deleteCustomerData();
            deleteRuleChain();

            if (!tbRestClient.isPe()) {
                dashboardService.deleteDashboardItems(getSolutionName(), null);
                ModelData data = makeData(true, ZonedDateTime.now());
                deleteData(data);
            }

            log.info("Greenhouse Solution - removal is completed!");
        } catch (Exception e) {
            log.error("Greenhouse Solution removal was failed, skipping...", e);
        }
    }


    private Set<Greenhouse> mapToGreenhouses(ModelData data) {
        return data.getData().stream()
                .filter(modelEntity -> modelEntity instanceof Greenhouse)
                .map(modelEntity -> (Greenhouse) modelEntity)
                .collect(Collectors.toSet());
    }

    private Set<Plant> mapToPlants(ModelData data) {
        return data.getData().stream()
                .filter(modelEntity -> modelEntity instanceof Plant)
                .map(modelEntity -> (Plant) modelEntity)
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
        Set<Greenhouse> greenhouses = mapToGreenhouses(data);

        try {
            RuleChain ruleChain = this.tbRestClient.createRuleChain(RULE_CHAIN_NAME);
            RuleChainMetaData metaData = this.tbRestClient.getRuleChainMetadataByRuleChainId(ruleChain.getUuidId())
                    .orElseThrow();

            List<RuleNode> nodes = metaData.getNodes();
            List<NodeConnectionInfo> connections = new ArrayList<>();
            metaData.setConnections(connections);

            int greenhouseCounter = 0;
            for (Greenhouse greenhouse : greenhouses) {
                OutsideAirWarmHumiditySensor outsideAirWarmHumiditySensor = greenhouse.getOutsideAirWarmHumiditySensor();
                OutsideLightSensor outsideLightSensor = greenhouse.getOutsideLightSensor();

                EnergyMeter energyMeter = greenhouse.getEnergyMeter();
                WaterMeter waterMeter = greenhouse.getWaterMeter();

                UUID greenhouseId = this.greenhouseToIdMap.get(greenhouse);
                UUID outsideAirWarmHumiditySensorId = this.outsideAirWarmHumiditySensorToIdMap.get(outsideAirWarmHumiditySensor);
                UUID outsideLightSensorId = this.outsideLightSensorToIdMap.get(outsideLightSensor);

                String greenhouseGeneratorCode = getGreenhouseGeneratorCode(greenhouse.getLatitude(), greenhouse.getLongitude(), WEATHER_API_TOKEN);
                RuleNode greenhouseGeneratorNode = this.ruleChainBuildingService.createGeneratorNode(
                        "",
                        greenhouseId,
                        greenhouseGeneratorCode,
                        getNodePositionX(greenhouseCounter),
                        getNodePositionY(greenhouseCounter)
                );

                RuleNode greenhouseWeatherApiCallNode = this.ruleChainBuildingService.createRestApiCallNode(
                        "",
                        WEATHER_API_URL,
                        "GET",
                        getNodePositionX(greenhouseCounter),
                        getNodePositionY(greenhouseCounter)
                );

                RuleNode greenhouseMakeTempHumidityTelemetryNode = this.ruleChainBuildingService.createTransformationNode(
                        getSolutionName(),
                        "",
                        "raw_weather_to_temp_humidity.js",
                        getNodePositionX(greenhouseCounter),
                        getNodePositionY(greenhouseCounter)
                );

                RuleNode greenhouseMakeLightTelemetryNode = this.ruleChainBuildingService.createTransformationNode(
                        getSolutionName(),
                        "",
                        "raw_weather_to_light.js",
                        getNodePositionX(greenhouseCounter),
                        getNodePositionY(greenhouseCounter)
                );

                RuleNode outsideAirWamHumidityOriginatorNode = this.ruleChainBuildingService.createChangeOriginatorNode(
                        "",
                        outsideAirWarmHumiditySensor.getSystemName(),
                        EntityType.DEVICE,
                        getNodePositionX(greenhouseCounter),
                        getNodePositionY(greenhouseCounter)
                );

                RuleNode outsideLightOriginatorNode = this.ruleChainBuildingService.createChangeOriginatorNode(
                        "",
                        outsideLightSensor.getSystemName(),
                        EntityType.DEVICE,
                        getNodePositionX(greenhouseCounter),
                        getNodePositionY(greenhouseCounter)
                );

                RuleNode outsideSensorsSaveNode = this.ruleChainBuildingService.createSaveNode(
                        "",
                        getNodePositionX(greenhouseCounter),
                        getNodePositionY(greenhouseCounter)
                );

                for (Section section : greenhouse.getSections()) {
                    SoilWarmMoistureSensor soilWarmMoistureSensor = section.getSoilWarmMoistureSensor();
                    SoilAciditySensor soilAciditySensor = section.getSoilAciditySensor();
                    SoilNpkSensor soilNpkSensor = section.getSoilNpkSensor();
                    HarvestReporter harvestReporter = section.getHarvestReporter();


                }
                greenhouseCounter++;
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
        long startTs = DateTimeUtils.toTs(startYear);
        long now = System.currentTimeMillis();

        Map<StationCity, Map<Long, WeatherData>> weatherMap = Arrays.stream(StationCity.values())
                .map(city -> Pair.of(city, loadWeatherData(city, startYear, skipTelemetry)))
                .collect(Collectors.toMap(Pair::getKey, Pair::getValue));

        Set<Plant> plants = MySortedSet.of(
                Plant.builder()
                        .systemName("Tomato - Sungold")
                        .systemLabel("")
                        .name("Tomato")
                        .variety("Sungold")
                        .minRipeningCycleDays(28)
                        .maxRipeningCycleDays(42)
                        .dayMinTemperature(21)
                        .dayMaxTemperature(29)
                        .nightMinTemperature(15)
                        .nightMaxTemperature(21)
                        .minAirHumidity(40)
                        .maxAirHumidity(70)
                        .minSoilMoisture(20)
                        .maxSoilMoisture(60)
                        .minCo2Concentration(350)
                        .maxCo2Concentration(1000)
                        .minLight(7000)
                        .maxLight(120000)
                        .minPh(5.5)
                        .maxPh(7.5)
                        .build(),

                Plant.builder()
                        .systemName("Cucumber - English")
                        .systemLabel("")
                        .name("Cucumber")
                        .variety("English")
                        .minRipeningCycleDays(28)
                        .maxRipeningCycleDays(42)
                        .dayMinTemperature(22)
                        .dayMaxTemperature(27)
                        .nightMinTemperature(16)
                        .nightMaxTemperature(21)
                        .minAirHumidity(60)
                        .maxAirHumidity(70)
                        .minSoilMoisture(60)
                        .maxSoilMoisture(80)
                        .minCo2Concentration(300)
                        .maxCo2Concentration(1000)
                        .minLight(10000)
                        .maxLight(20000)
                        .minPh(6.0)
                        .maxPh(7.0)
                        .build(),

                Plant.builder()
                        .systemName("Onion - Sweet Spanish")
                        .systemLabel("")
                        .name("Onion")
                        .variety("Sweet Spanish")
                        .minRipeningCycleDays(28)
                        .maxRipeningCycleDays(42)
                        .dayMinTemperature(21)
                        .dayMaxTemperature(29)
                        .nightMinTemperature(13)
                        .nightMaxTemperature(21)
                        .minAirHumidity(50)
                        .maxAirHumidity(70)
                        .minSoilMoisture(60)
                        .maxSoilMoisture(80)
                        .minCo2Concentration(300)
                        .maxCo2Concentration(1000)
                        .minLight(10000)
                        .maxLight(20000)
                        .minPh(6.0)
                        .maxPh(7.0)
                        .build(),

                Plant.builder()
                        .systemName("Tomato - Cherry")
                        .systemLabel("")
                        .name("Tomato")
                        .variety("Cherry")
                        .minRipeningCycleDays(28)
                        .maxRipeningCycleDays(42)
                        .dayMinTemperature(21)
                        .dayMaxTemperature(29)
                        .nightMinTemperature(15)
                        .nightMaxTemperature(21)
                        .minAirHumidity(50)
                        .maxAirHumidity(70)
                        .minSoilMoisture(40)
                        .maxSoilMoisture(70)
                        .minCo2Concentration(350)
                        .maxCo2Concentration(1000)
                        .minLight(7000)
                        .maxLight(120000)
                        .minPh(5.5)
                        .maxPh(6.8)
                        .build()
        );

        Set<GreenhouseConfiguration> greenhouseConfigurations = MySortedSet.of(
                GreenhouseConfiguration.builder()
                        .order(1)
                        .startTs(startTs)
                        .endTs(now)
                        .name("Greenhouse in Kyiv")
                        .stationCity(StationCity.KYIV)
                        .address("Svyatoshyns'ka St, 34 к, Kyiv, 02000")
                        .latitude(50.446603)
                        .longitude(30.386447)
                        .plantType(PlantType.TOMATO)
                        .variety("Sungold")
                        .sectionHeight(5)
                        .sectionWidth(7)
                        .sectionArea(3)
                        .build(),

                GreenhouseConfiguration.builder()
                        .order(2)
                        .startTs(startTs)
                        .endTs(now)
                        .name("Greenhouse in Krakow")
                        .stationCity(StationCity.KRAKOW)
                        .address("Zielona 18, 32-087 Bibice, Poland")
                        .latitude(50.121765)
                        .longitude(19.946134)
                        .plantType(PlantType.CUCUMBER)
                        .variety("English")
                        .sectionHeight(3)
                        .sectionWidth(4)
                        .sectionArea(5)
                        .build(),

                GreenhouseConfiguration.builder()
                        .order(3)
                        .startTs(startTs)
                        .endTs(now)
                        .name("Greenhouse in Warszawa")
                        .stationCity(StationCity.WARSZAWA)
                        .address("Ojca Aniceta 28, 03-264 Warszawa, Poland")
                        .latitude(52.306237)
                        .longitude(21.039917)
                        .plantType(PlantType.ONION)
                        .variety("Sweet Spanish")
                        .sectionHeight(2)
                        .sectionWidth(6)
                        .sectionArea(4)
                        .build(),

                GreenhouseConfiguration.builder()
                        .order(4)
                        .startTs(startTs)
                        .endTs(now)
                        .name("Greenhouse in Stuttgart")
                        .stationCity(StationCity.STUTTGART)
                        .address("Augsburger Str. 500, 70327 Stuttgart, Germany")
                        .latitude(48.774252)
                        .longitude(9.259500)
                        .plantType(PlantType.TOMATO)
                        .variety("Cherry")
                        .sectionHeight(3)
                        .sectionWidth(5)
                        .sectionArea(3)
                        .build()
        );


        Set<ModelEntity> entities = MySortedSet.of();
        entities.addAll(plants);
        entities.addAll(
                greenhouseConfigurations
                        .stream()
                        .map(configuration -> makeGreenhouseByConfiguration(
                                configuration, weatherMap.get(configuration.getStationCity()), skipTelemetry
                        ))
                        .collect(Collectors.toList())
        );


        return ModelData.builder()
                .data(entities)
                .build();
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

        Set<Plant> plants = mapToPlants(data);

        Map<String, Map<String, Plant>> plantsMap = plants
                .stream()
                .collect(Collectors.groupingBy(
                        Plant::getName, Collectors.toMap(Plant::getVariety, i -> i)
                ));

        for (Plant plant : plants) {
            Asset plantAsset = createPlant(plant, ownerId, assetGroupId);
        }

        Set<Greenhouse> greenhouses = mapToGreenhouses(data);
        for (Greenhouse greenhouse : greenhouses) {
            Asset greenhouseAsset = createGreenhouse(greenhouse, ownerId, assetGroupId);

            for (Section section : greenhouse.getSections()) {
                Asset sectionAsset = createSection(section, ownerId, assetGroupId);
                this.tbRestClient.createRelation(RelationType.CONTAINS.getType(), greenhouseAsset.getId(), sectionAsset.getId());

                Device soilWarmMoistureSensorDevice = createSoilWarmMoistureSensor(section.getSoilWarmMoistureSensor(), ownerId, deviceGroupId);
                Device soilAciditySensorDevice = createSoilAciditySensor(section.getSoilAciditySensor(), ownerId, deviceGroupId);
                Device soilNpkSensorDevice = createSoilNpkSensor(section.getSoilNpkSensor(), ownerId, deviceGroupId);
                Device harvestReporterDevice = createHarvestReporter(section.getHarvestReporter(), ownerId, deviceGroupId);

                this.tbRestClient.createRelation(RelationType.CONTAINS.getType(), sectionAsset.getId(), soilWarmMoistureSensorDevice.getId());
                this.tbRestClient.createRelation(RelationType.CONTAINS.getType(), sectionAsset.getId(), soilAciditySensorDevice.getId());
                this.tbRestClient.createRelation(RelationType.CONTAINS.getType(), sectionAsset.getId(), soilNpkSensorDevice.getId());
                this.tbRestClient.createRelation(RelationType.CONTAINS.getType(), sectionAsset.getId(), harvestReporterDevice.getId());
            }

            Device insideAirWarmHumiditySensorDevice = createInsideAirWarmHumiditySensor(greenhouse.getInsideAirWarmHumiditySensor(), ownerId, deviceGroupId);
            Device insideLightSensorDevice = createInsideLightSensor(greenhouse.getInsideLightSensor(), ownerId, deviceGroupId);
            Device insideCO2SensorDevice = createInsideCO2Sensor(greenhouse.getInsideCO2Sensor(), ownerId, deviceGroupId);
            Device outsideAirWarmHumiditySensorDevice = createOutsideAirWarmHumiditySensor(greenhouse.getOutsideAirWarmHumiditySensor(), ownerId, deviceGroupId);
            Device outsideLightSensorDevice = createOutsideLightSensor(greenhouse.getOutsideLightSensor(), ownerId, deviceGroupId);
            Device energyMeter = createEnergyMeter(greenhouse.getEnergyMeter(), ownerId, deviceGroupId);
            Device waterMeter = createWaterMeter(greenhouse.getWaterMeter(), ownerId, deviceGroupId);

            this.tbRestClient.createRelation(RelationType.CONTAINS.getType(), greenhouseAsset.getId(), insideAirWarmHumiditySensorDevice.getId());
            this.tbRestClient.createRelation(RelationType.CONTAINS.getType(), greenhouseAsset.getId(), insideLightSensorDevice.getId());
            this.tbRestClient.createRelation(RelationType.CONTAINS.getType(), greenhouseAsset.getId(), insideCO2SensorDevice.getId());
            this.tbRestClient.createRelation(RelationType.CONTAINS.getType(), greenhouseAsset.getId(), outsideAirWarmHumiditySensorDevice.getId());
            this.tbRestClient.createRelation(RelationType.CONTAINS.getType(), greenhouseAsset.getId(), outsideLightSensorDevice.getId());
            this.tbRestClient.createRelation(RelationType.CONTAINS.getType(), greenhouseAsset.getId(), energyMeter.getId());
            this.tbRestClient.createRelation(RelationType.CONTAINS.getType(), greenhouseAsset.getId(), waterMeter.getId());

            if (plantsMap.containsKey(greenhouse.getPlantType().toString())) {
                Map<String, Plant> varieties = plantsMap.get(greenhouse.getPlantType().toString());
                if (varieties.containsKey(greenhouse.getVariety())) {
                    Plant plant = varieties.get(greenhouse.getVariety());
                    UUID plantUuid = this.plantToIdMap.get(plant);
                    this.tbRestClient.createRelation(RelationType.CONTAINS.getType(), new AssetId(plantUuid), greenhouseAsset.getId());
                }
            }
        }
    }

    private void validateData(ModelData data) {
        Set<Plant> plants = mapToPlants(data);
        Set<Greenhouse> greenhouses = mapToGreenhouses(data);

        Set<EnergyMeter> energyMeters = greenhouses.stream()
                .map(Greenhouse::getEnergyMeter)
                .collect(Collectors.toSet());

        Set<WaterMeter> waterMeters = greenhouses.stream()
                .map(Greenhouse::getWaterMeter)
                .collect(Collectors.toSet());

        Set<InsideAirWarmHumiditySensor> insideAirWarmHumiditySensors = greenhouses.stream()
                .map(Greenhouse::getInsideAirWarmHumiditySensor)
                .collect(Collectors.toSet());

        Set<InsideLightSensor> insideLightSensors = greenhouses.stream()
                .map(Greenhouse::getInsideLightSensor)
                .collect(Collectors.toSet());

        Set<InsideCO2Sensor> insideCO2Sensors = greenhouses.stream()
                .map(Greenhouse::getInsideCO2Sensor)
                .collect(Collectors.toSet());

        Set<OutsideAirWarmHumiditySensor> outsideAirWarmHumiditySensors = greenhouses.stream()
                .map(Greenhouse::getOutsideAirWarmHumiditySensor)
                .collect(Collectors.toSet());

        Set<OutsideLightSensor> outsideLightSensors = greenhouses.stream()
                .map(Greenhouse::getOutsideLightSensor)
                .collect(Collectors.toSet());


        Set<Section> sections = greenhouses.stream()
                .map(Greenhouse::getSections)
                .flatMap(Collection::stream)
                .collect(Collectors.toSet());

        Set<SoilWarmMoistureSensor> soilWarmMoistureSensors = sections.stream()
                .map(Section::getSoilWarmMoistureSensor)
                .collect(Collectors.toSet());

        Set<SoilAciditySensor> soilAciditySensors = sections.stream()
                .map(Section::getSoilAciditySensor)
                .collect(Collectors.toSet());

        Set<SoilNpkSensor> soilNpkSensors = sections.stream()
                .map(Section::getSoilNpkSensor)
                .collect(Collectors.toSet());

        Set<HarvestReporter> harvestReporters = sections.stream()
                .map(Section::getHarvestReporter)
                .collect(Collectors.toSet());

        Set<ModelEntity> allAssets = new TreeSet<>();
        allAssets.addAll(plants);
        allAssets.addAll(greenhouses);
        allAssets.addAll(sections);

        Set<ModelEntity> allDevices = new TreeSet<>();
        allDevices.addAll(energyMeters);
        allDevices.addAll(waterMeters);
        allDevices.addAll(insideAirWarmHumiditySensors);
        allDevices.addAll(insideLightSensors);
        allDevices.addAll(insideCO2Sensors);
        allDevices.addAll(outsideAirWarmHumiditySensors);
        allDevices.addAll(outsideLightSensors);
        allDevices.addAll(soilWarmMoistureSensors);
        allDevices.addAll(soilAciditySensors);
        allDevices.addAll(soilNpkSensors);
        allDevices.addAll(harvestReporters);

        Set<String> assets = allAssets
                .stream()
                .map(ModelEntity::getSystemName)
                .collect(Collectors.toSet());

        Set<String> devices = allDevices
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
        Set<Plant> plants = mapToPlants(data);
        Set<Greenhouse> greenhouses = mapToGreenhouses(data);

        Set<EnergyMeter> energyMeters = greenhouses.stream()
                .map(Greenhouse::getEnergyMeter)
                .collect(Collectors.toSet());

        Set<WaterMeter> waterMeters = greenhouses.stream()
                .map(Greenhouse::getWaterMeter)
                .collect(Collectors.toSet());

        Set<InsideAirWarmHumiditySensor> insideAirWarmHumiditySensors = greenhouses.stream()
                .map(Greenhouse::getInsideAirWarmHumiditySensor)
                .collect(Collectors.toSet());

        Set<InsideLightSensor> insideLightSensors = greenhouses.stream()
                .map(Greenhouse::getInsideLightSensor)
                .collect(Collectors.toSet());

        Set<InsideCO2Sensor> insideCO2Sensors = greenhouses.stream()
                .map(Greenhouse::getInsideCO2Sensor)
                .collect(Collectors.toSet());

        Set<OutsideAirWarmHumiditySensor> outsideAirWarmHumiditySensors = greenhouses.stream()
                .map(Greenhouse::getOutsideAirWarmHumiditySensor)
                .collect(Collectors.toSet());

        Set<OutsideLightSensor> outsideLightSensors = greenhouses.stream()
                .map(Greenhouse::getOutsideLightSensor)
                .collect(Collectors.toSet());


        Set<Section> sections = greenhouses.stream()
                .map(Greenhouse::getSections)
                .flatMap(Collection::stream)
                .collect(Collectors.toSet());

        Set<SoilWarmMoistureSensor> soilWarmMoistureSensors = sections.stream()
                .map(Section::getSoilWarmMoistureSensor)
                .collect(Collectors.toSet());

        Set<SoilAciditySensor> soilAciditySensors = sections.stream()
                .map(Section::getSoilAciditySensor)
                .collect(Collectors.toSet());

        Set<SoilNpkSensor> soilNpkSensors = sections.stream()
                .map(Section::getSoilNpkSensor)
                .collect(Collectors.toSet());

        Set<HarvestReporter> harvestReporters = sections.stream()
                .map(Section::getHarvestReporter)
                .collect(Collectors.toSet());

        Set<ModelEntity> allAssets = new TreeSet<>();
        allAssets.addAll(plants);
        allAssets.addAll(greenhouses);
        allAssets.addAll(sections);

        Set<ModelEntity> allDevices = new TreeSet<>();
        allDevices.addAll(energyMeters);
        allDevices.addAll(waterMeters);
        allDevices.addAll(insideAirWarmHumiditySensors);
        allDevices.addAll(insideLightSensors);
        allDevices.addAll(insideCO2Sensors);
        allDevices.addAll(outsideAirWarmHumiditySensors);
        allDevices.addAll(outsideLightSensors);
        allDevices.addAll(soilWarmMoistureSensors);
        allDevices.addAll(soilAciditySensors);
        allDevices.addAll(soilNpkSensors);
        allDevices.addAll(harvestReporters);

        Set<String> assets = allAssets
                .stream()
                .map(ModelEntity::getSystemName)
                .collect(Collectors.toSet());

        Set<String> devices = allDevices
                .stream()
                .map(ModelEntity::getSystemName)
                .collect(Collectors.toSet());

        AtomicInteger deviceCounter = new AtomicInteger(0);
        this.tbRestClient.getAllDevices()
                .stream()
                .filter(device -> devices.contains(device.getName()))
                .forEach(device -> {
                    log.debug("Device deleted {}/{}", deviceCounter.incrementAndGet(), devices.size());
                    this.tbRestClient.deleteDevice(device.getUuidId());
                });

        AtomicInteger assetCounter = new AtomicInteger(0);
        this.tbRestClient.getAllAssets()
                .stream()
                .filter(asset -> assets.contains(asset.getName()))
                .forEach(asset -> {
                    log.debug("Asset deleted {}/{}", assetCounter.incrementAndGet(), assets.size());
                    this.tbRestClient.deleteAsset(asset.getUuidId());
                });
    }


    private Greenhouse makeGreenhouseByConfiguration(GreenhouseConfiguration configuration, Map<Long, WeatherData> weatherDataMap, boolean skipTelemetry) {

        Telemetry<Integer> outsideTemperatureTelemetry = createOutsideTemperatureTelemetry(weatherDataMap, configuration, skipTelemetry);
        Telemetry<Integer> outsideHumidityTelemetry = createOutsideHumidityTelemetry(weatherDataMap, configuration, skipTelemetry);
        Telemetry<Integer> outsideLightTelemetry = createOutsideLightTelemetry(weatherDataMap, configuration, skipTelemetry);

        Telemetry<Integer> insideLightTelemetry = createInsideLightTelemetry(outsideLightTelemetry, configuration, skipTelemetry);


        Set<Section> sections = new TreeSet<>();
        for (int height = 1; height <= configuration.getSectionHeight(); height++) {
            for (int width = 1; width <= configuration.getSectionWidth(); width++) {

                Telemetry<Double> nitrogenLevelTelemetry = createTelemetrySoilNitrogenLevel(configuration, skipTelemetry);
                Telemetry<Double> phosphorusLevelTelemetry = createTelemetrySoilPhosphorusLevel(configuration, skipTelemetry);
                Telemetry<Double> potassiumLevelTelemetry = createTelemetrySoilPotassiumLevel(configuration, skipTelemetry);

                SoilNpkSensor soilNpkSensor = SoilNpkSensor.builder()
                        .systemName("Soil NPK Sensor: " + configuration.getName() + ", " + String.format("%s-%s", height, width))
                        .systemLabel("")
                        .nitrogen(nitrogenLevelTelemetry)
                        .phosphorus(phosphorusLevelTelemetry)
                        .potassium(potassiumLevelTelemetry)
                        .build();


                SoilWarmMoistureSensor soilWarmMoistureSensor = SoilWarmMoistureSensor.builder()
                        .systemName("Soil Warm-Moisture Sensor: " + configuration.getName() + ", " + String.format("%s-%s", height, width))
                        .systemLabel("")
                        .temperature(new Telemetry<>("temperature", MySortedSet.of(new Telemetry.Point<>(Timestamp.of(0), 0))))
                        .moisture(new Telemetry<>("moisture", MySortedSet.of(new Telemetry.Point<>(Timestamp.of(0), 0))))
                        .build();

                SoilAciditySensor soilAciditySensor = SoilAciditySensor.builder()
                        .systemName("Soil Acidity: " + configuration.getName() + ", " + String.format("%s-%s", height, width))
                        .systemLabel("")
                        .acidity(new Telemetry<>("acidity", MySortedSet.of(new Telemetry.Point<>(Timestamp.of(0), 0))))
                        .build();


                HarvestReporter harvestReporter = HarvestReporter.builder()
                        .systemName("Harvester: " + configuration.getName() + ", " + String.format("%s-%s", height, width))
                        .systemLabel("")
                        .cropWeight(new Telemetry<>("cropWeight", MySortedSet.of(new Telemetry.Point<>(Timestamp.of(0), 0))))
                        .workerInCharge(new Telemetry<>("workerInCharge", MySortedSet.of(new Telemetry.Point<>(Timestamp.of(0), "1"))))
                        .build();


                Section section = Section.builder()
                        .systemName(String.format(configuration.getName() + ", section %s-%s", height, width))
                        .systemLabel("")
                        .positionHeight(height)
                        .positionWidth(width)
                        .area(configuration.getSectionArea())
                        .soilWarmMoistureSensor(soilWarmMoistureSensor)
                        .soilAciditySensor(soilAciditySensor)
                        .soilNpkSensor(soilNpkSensor)
                        .harvestReporter(harvestReporter)
                        .build();

                sections.add(section);
            }
        }

        OutsideAirWarmHumiditySensor outsideAirWarmHumiditySensor = OutsideAirWarmHumiditySensor.builder()
                .systemName(configuration.getName() + ": Air Warm-Humidity Sensor (Outside)")
                .systemLabel("")
                .temperature(outsideTemperatureTelemetry)
                .humidity(outsideHumidityTelemetry)
                .build();

        OutsideLightSensor outsideLightSensor = OutsideLightSensor.builder()
                .systemName(configuration.getName() + ": Light Sensor (Outside)")
                .systemLabel("")
                .light(outsideLightTelemetry)
                .build();

        InsideAirWarmHumiditySensor insideAirWarmHumiditySensor = InsideAirWarmHumiditySensor.builder()
                .systemName(configuration.getName() + ": Air Warm-Humidity Sensor (Inside)")
                .systemLabel("")
                .temperature(new Telemetry<>("temperature_in", MySortedSet.of(new Telemetry.Point<>(Timestamp.of(0), 0))))
                .humidity(new Telemetry<>("humidity_in", MySortedSet.of(new Telemetry.Point<>(Timestamp.of(0), 0))))
                .build();

        InsideLightSensor insideLightSensor = InsideLightSensor.builder()
                .systemName(configuration.getName() + ": Light Sensor (Inside)")
                .systemLabel("")
                .light(insideLightTelemetry)
                .build();

        InsideCO2Sensor insideCO2Sensor = InsideCO2Sensor.builder()
                .systemName(configuration.getName() + ": CO2 Sensor (Inside)")
                .systemLabel("")
                .concentration(new Telemetry<>("concentration", MySortedSet.of(new Telemetry.Point<>(Timestamp.of(0), 0))))
                .build();

        EnergyMeter energyMeter = EnergyMeter.builder()
                .systemName(configuration.getName() + ": Energy Meter")
                .systemLabel("")
                .consumptionEnergy(new Telemetry<>("consumptionEnergy", MySortedSet.of(new Telemetry.Point<>(Timestamp.of(0), 0))))
                .build();

        WaterMeter waterMeter = WaterMeter.builder()
                .systemName(configuration.getName() + ": Water Meter")
                .systemLabel("")
                .consumptionWater(new Telemetry<>("consumptionWater", MySortedSet.of(new Telemetry.Point<>(Timestamp.of(0), 0))))
                .build();

        return Greenhouse.builder()
                .systemName("Greenhouse " + configuration.getName())
                .systemLabel("")
                .plantType(configuration.getPlantType())
                .variety(configuration.getVariety())
                .sections(sections)
                .insideAirWarmHumiditySensor(insideAirWarmHumiditySensor)
                .insideLightSensor(insideLightSensor)
                .insideCO2Sensor(insideCO2Sensor)
                .outsideAirWarmHumiditySensor(outsideAirWarmHumiditySensor)
                .outsideLightSensor(outsideLightSensor)
                .energyMeter(energyMeter)
                .waterMeter(waterMeter)
                .build();
    }


    private Map<Long, WeatherData> loadWeatherData(StationCity city, ZonedDateTime startYear, boolean skipTelemetry) {
        if (skipTelemetry) {
            return new HashMap<>();
        }

        try {
            Path filePath = Path.of("data", "greenhouse_weather", getWeatherFileName(city));
            Reader in = new FileReader(filePath.toFile());
            CSVParser parser = CSVFormat.DEFAULT.withFirstRecordAsHeader().parse(in);

            List<WeatherData> weatherDataList = parser.stream()
                    .map(record -> {
                        long ts = Long.parseLong(record.get("ts"));
                        ZonedDateTime dateTime = DateTimeUtils.fromTs(ts);
                        dateTime = dateTime.truncatedTo(ChronoUnit.HOURS);

                        long newTs = DateTimeUtils.toTs(dateTime.plusYears(2));

                        return WeatherData.builder()
                                        .ts(newTs)
                                        .pressure(Double.parseDouble(record.get("pressure")))
                                        .temperatureFahrenheit(Double.parseDouble(record.get("temperatureFahrenheit")))
                                        .temperatureCelsius(Double.parseDouble(record.get("temperatureCelsius").replace(',', '.')))
                                        .dewPointFahrenheit(Double.parseDouble(record.get("dewPointFahrenheit")))
                                        .dewPointCelsius(Double.parseDouble(record.get("dewPointCelsius").replace(',', '.')))
                                        .humidity(Double.parseDouble(record.get("humidity")))
                                        .windSpeed(Double.parseDouble(record.get("windSpeed")))
                                        .windGust(Double.parseDouble(record.get("windGust")))
                                        .windDirectionDegrees(Double.parseDouble(record.get("windDirectionDegrees")))
                                        .windDirectionWords(record.get("windDirectionWords"))
                                        .condition(record.get("condition"))
                                        .build();
                            }
                    )
                    .collect(Collectors.toList());

            Map<Long, WeatherData> tsToWeatherMap = weatherDataList.stream()
                    .collect(Collectors.toMap(
                            WeatherData::getTs,
                            i -> i,
                            (w1, w2) -> w1
                    ));

            long now = System.currentTimeMillis();
            ZonedDateTime startDate = startYear.truncatedTo(ChronoUnit.HOURS);
            ZonedDateTime nowDate = DateTimeUtils.fromTs(now).truncatedTo(ChronoUnit.HOURS);
            ZonedDateTime iteratedDate = startDate;
            WeatherData last = weatherDataList.get(0);
            while (iteratedDate.isBefore(nowDate)) {
                long iteratedTs = DateTimeUtils.toTs(iteratedDate);
                WeatherData weatherData = tsToWeatherMap.get(iteratedTs);
                if (weatherData == null) {
                    String message = String.format(
                            "No weather data in city %s, time = %s, will used data from %s",
                            city, iteratedDate, DateTimeUtils.fromTs(last.getTs())
                    );
//                    log.warn(message);
                    weatherData = last;
                    tsToWeatherMap.put(iteratedTs, weatherData);
                }

                last = weatherData;
                iteratedDate = iteratedDate.plus(1, ChronoUnit.HOURS);
            }

            return tsToWeatherMap;
        } catch (Exception e) {
            throw new RuntimeException("Can not read weather file for city " + city.toString(), e);
        }
    }

    private String getWeatherFileName(StationCity city) {
        switch (city) {
            case KYIV:
                return "kyiv.csv";
            case KRAKOW:
                return "krakow.csv";
            case WARSZAWA:
                return "warszawa.csv";
            case STUTTGART:
                return "stuttgart.csv";
            default:
                throw new IllegalArgumentException("Unsupported city: " + city);
        }
    }


    private Telemetry<Integer> createOutsideTemperatureTelemetry(Map<Long, WeatherData> tsToWeatherMap, GreenhouseConfiguration configuration, boolean skipTelemetry) {
        if (skipTelemetry) {
            return new Telemetry<>("skip");
        }

        Telemetry<Integer> result = new Telemetry<>("temperature_out");
        long startTs = configuration.getStartTs();
        long endTs = configuration.getEndTs();

        ZonedDateTime startDate = DateTimeUtils.fromTs(startTs).truncatedTo(ChronoUnit.HOURS);
        ZonedDateTime endDate = DateTimeUtils.fromTs(endTs).truncatedTo(ChronoUnit.HOURS);
        ZonedDateTime iteratedDate = startDate;
        while (iteratedDate.isBefore(endDate)) {
            long iteratedTs = DateTimeUtils.toTs(iteratedDate);
            WeatherData weatherData = tsToWeatherMap.get(iteratedTs);

            result.add(iteratedTs, (int) weatherData.getTemperatureCelsius());
            iteratedDate = iteratedDate.plus(1, ChronoUnit.HOURS);
        }

        return result;
    }

    private Telemetry<Integer> createOutsideHumidityTelemetry(Map<Long, WeatherData> tsToWeatherMap, GreenhouseConfiguration configuration, boolean skipTelemetry) {
        if (skipTelemetry) {
            return new Telemetry<>("skip");
        }

        Telemetry<Integer> result = new Telemetry<>("humidity_out");
        long startTs = configuration.getStartTs();
        long endTs = configuration.getEndTs();

        ZonedDateTime startDate = DateTimeUtils.fromTs(startTs).truncatedTo(ChronoUnit.HOURS);
        ZonedDateTime endDate = DateTimeUtils.fromTs(endTs).truncatedTo(ChronoUnit.HOURS);
        ZonedDateTime iteratedDate = startDate;
        while (iteratedDate.isBefore(endDate)) {
            long iteratedTs = DateTimeUtils.toTs(iteratedDate);
            WeatherData weatherData = tsToWeatherMap.get(iteratedTs);

            result.add(iteratedTs, (int) weatherData.getHumidity());
            iteratedDate = iteratedDate.plus(1, ChronoUnit.HOURS);
        }

        return result;
    }

    private Telemetry<Integer> createOutsideLightTelemetry(Map<Long, WeatherData> tsToWeatherMap, GreenhouseConfiguration configuration, boolean skipTelemetry) {
        if (skipTelemetry) {
            return new Telemetry<>("skip");
        }

        Telemetry<Integer> result = new Telemetry<>("light_out");
        long startTs = configuration.getStartTs();
        long endTs = configuration.getEndTs();

        ZonedDateTime startDate = DateTimeUtils.fromTs(startTs).truncatedTo(ChronoUnit.HOURS);
        ZonedDateTime endDate = DateTimeUtils.fromTs(endTs).truncatedTo(ChronoUnit.HOURS);
        ZonedDateTime iteratedDate = startDate;
        while (iteratedDate.isBefore(endDate)) {
            long iteratedTs = DateTimeUtils.toTs(iteratedDate);
            int hour = iteratedDate.getHour();
            int day = iteratedDate.getDayOfYear();

            WeatherData weatherData = tsToWeatherMap.get(iteratedTs);
            String condition = weatherData.getCondition();

            int hourLux = getHourLuxValues(hour);
            int yearLux = getYearLuxCycleValue(day);
            double percents = mapWeatherConditionToLuxValuesInPercents(condition);
            long noise = RandomUtils.getRandomNumber(-1000, 1000);
            int value = (int) ((hourLux + yearLux) * percents + noise);
            value = Math.max(0, value);

            result.add(iteratedTs, value);
            iteratedDate = iteratedDate.plus(1, ChronoUnit.HOURS);
        }

        return result;
    }

    private int getHourLuxValues(int hour) {
        /// As summer day [0 - 17_000]
        switch (hour) {
            case 0:
            case 1:
            case 2:
            case 3:
                return 0;
            case 4:
                return 1_000;
            case 5:
                return 2_000;
            case 6:
                return 4_000;
            case 7:
                return 6_000;
            case 8:
                return 9_000;
            case 9:
                return 12_000;
            case 10:
                return 14_000;
            case 11:
                return 16_000;
            case 12:
            case 13:
                return 17_000;
            case 14:
            case 15:
                return 16_000;
            case 16:
                return 15_000;
            case 17:
                return 13_000;
            case 18:
                return 11_000;
            case 19:
                return 8_000;
            case 20:
                return 6_000;
            case 21:
                return 4_000;
            case 22:
                return 1_000;
            case 23:
                return 0;
            default:
                throw new IllegalArgumentException("Unsupported hour = " + hour);
        }
    }

    private int getYearLuxCycleValue(int day) {
        /// Max summer luxes = 17000 (172 day), max winter luxes (356 day) = 5000, diff = 12000
        // return 0 value if max, return -12000 value if min
        int diff = 12000;
        if (172 <= day && day < 356) {
            return (-diff * (day - 172)) / (356 - 172);
        } else if (356 <= day) {
            return (diff * (day - 356)) / ((365 - 356) + 172) - diff;
        } else  {
            return (diff * (day + (365 - 356))) / ((365 - 356) + 172) - diff;
        }
    }

    private double mapWeatherConditionToLuxValuesInPercents(String condition) {
        switch (condition) {
            case "Heavy Drizzle":
            case "Heavy Drizzle / Windy":
            case "Heavy Snow":
            case "Heavy Snow / Windy":
            case "Heavy Snow Shower":
            case "Heavy Snow Shower / Windy":
            case "Heavy Rain":
            case "Heavy Rain / Windy":
            case "Heavy Rain Shower":
            case "Heavy T-Storm":
            case "Heavy T-Storm / Windy":
            case "Squalls":
            case "Squalls / Windy":
            case "T-Storm":
            case "T-Storm / Windy":
                return 0.40;

            case "Smoke":
            case "Fog":
            case "Patches of Fog":
            case "Partial Fog":
            case "Shallow Fog":
            case "Widespread Dust":
                return 0.50;

            case "Mostly Cloudy":
            case "Mostly Cloudy / Windy":
            case "Light Snow Shower":
            case "Thunder / Windy":
            case "Thunder":
            case "Wintry Mix":
            case "Wintry Mix / Windy":
                return 0.60;

            case "Partly Cloudy / Windy":
            case "Partly Cloudy":
            case "Blowing Dust / Windy":
            case "Cloudy / Windy":
            case "Cloudy":
            case "Rain":
            case "Rain / Windy":
            case "Rain Shower":
            case "Rain Shower / Windy":
            case "Small Hail":
            case "Small Hail / Windy":
            case "Snow":
            case "Snow / Windy":
            case "Snow Shower":
            case "Snow Shower / Windy":
            case "Snow and Sleet":
            case "Snow and Sleet / Windy":
            case "Drifting Snow":
            case "Drifting Snow / Windy":
            case "Drizzle":
            case "Freezing Rain":
            case "Freezing Rain / Windy":
            case "Snow Grains":
            case "Snow Grains / Windy":
            case "Thunder in the Vicinity":
            case "Showers in the Vicinity":
            case "Mist":
                return 0.70;

            case "Light Sleet":
            case "Light Sleet / Windy":
            case "Light Snow and Sleet":
            case "Light Snow":
            case "Light Snow / Windy":
            case "Light Snow Shower / Windy":
            case "Light Freezing Rain":
            case "Light Freezing Drizzle":
            case "Light Rain":
            case "Light Rain / Windy":
            case "Light Rain Shower":
            case "Light Rain Shower / Windy":
            case "Light Rain with Thunder":
            case "Light Drizzle":
            case "Light Drizzle / Windy":
            case "Light Snow Grains":
                return 0.85;

            case "Fair":
            case "Fair / Windy":
                return 1.0;
            default: throw new IllegalArgumentException("Unsupported condition: " + condition);
        }
    }


    private Telemetry<Integer> createInsideLightTelemetry(Telemetry<Integer> outsideLightTelemetry, GreenhouseConfiguration configuration, boolean skipTelemetry) {
        if (skipTelemetry) {
            return new Telemetry<>("skip");
        }
        Telemetry<Integer> result = new Telemetry<>("light_in");

        Map<Timestamp, Telemetry.Point<Integer>> outsideLightTelemetryMap = outsideLightTelemetry.getPoints()
                .stream()
                .collect(Collectors.toMap(Telemetry.Point::getTs, Functions.identity()));

        int dayModeStartHour = 8;
        int nightModeStartHour = 20;
        int dayLevel = 14000;
        int nightLevel = 3000;

        ZonedDateTime startDate = DateTimeUtils.fromTs(configuration.getStartTs()).truncatedTo(ChronoUnit.HOURS);
        ZonedDateTime endDate = DateTimeUtils.fromTs(configuration.getEndTs()).truncatedTo(ChronoUnit.HOURS);
        ZonedDateTime iteratedDate = startDate;
        while (iteratedDate.isBefore(endDate)) {
            long iteratedTs = DateTimeUtils.toTs(iteratedDate);
            int hour = iteratedDate.getHour();

            Telemetry.Point<Integer> outsideLightPoint = outsideLightTelemetryMap.get(Timestamp.of(iteratedTs));
            int outsideValue = outsideLightPoint.getValue();

            int currentNeededLevel = (dayModeStartHour <= hour && hour < nightModeStartHour)
                    ? dayLevel
                    : nightLevel;

            int diff = Math.max(0, currentNeededLevel - outsideValue);

            result.add(new Telemetry.Point<>(Timestamp.of(iteratedTs), diff));
            iteratedDate = iteratedDate.plus(1, ChronoUnit.HOURS);
        }

        return result;
    }


    private Telemetry<Double> createTelemetrySoilNitrogenLevel(GreenhouseConfiguration configuration, boolean skipTelemetry) {
        if (skipTelemetry) {
            return new Telemetry<>("skip");
        }
        PlantType plantType = configuration.getPlantType();
        String variety = configuration.getVariety();
        ZonedDateTime startDate = DateTimeUtils.fromTs(configuration.getStartTs());
        ZonedDateTime endDate = DateTimeUtils.fromTs(configuration.getEndTs());
        Telemetry<Double> consumption = createTemporalTelemetryPlantNitrogenConsumption(plantType, variety, startDate, endDate);

        String name = "nitrogen";
        double startLevel = RandomUtils.getRandomNumber(150, 200);
        double minLevel = 50;
        double raiseValue = 150;

        return createSoilLevel(name, startLevel, minLevel, raiseValue, consumption);
    }

    private Telemetry<Double> createTelemetrySoilPhosphorusLevel(GreenhouseConfiguration configuration, boolean skipTelemetry) {
        if (skipTelemetry) {
            return new Telemetry<>("skip");
        }
        PlantType plantType = configuration.getPlantType();
        String variety = configuration.getVariety();
        ZonedDateTime startDate = DateTimeUtils.fromTs(configuration.getStartTs());
        ZonedDateTime endDate = DateTimeUtils.fromTs(configuration.getEndTs());
        Telemetry<Double> consumption = createTemporalTelemetryPlantPhosphorusConsumption(plantType, variety, startDate, endDate);

        String name = "phosphorus";
        double startLevel = RandomUtils.getRandomNumber(40, 140) * 0.1;
        double minLevel = 4;
        double raiseValue = 8;

        return createSoilLevel(name, startLevel, minLevel, raiseValue, consumption);
    }

    private Telemetry<Double> createTelemetrySoilPotassiumLevel(GreenhouseConfiguration configuration, boolean skipTelemetry) {
        if (skipTelemetry) {
            return new Telemetry<>("skip");
        }
        PlantType plantType = configuration.getPlantType();
        String variety = configuration.getVariety();
        ZonedDateTime startDate = DateTimeUtils.fromTs(configuration.getStartTs());
        ZonedDateTime endDate = DateTimeUtils.fromTs(configuration.getEndTs());
        Telemetry<Double> consumption = createTemporalTelemetryPlantPotassiumConsumption(plantType, variety, startDate, endDate);

        String name = "potassium";
        double startLevel = RandomUtils.getRandomNumber(150, 200);
        double minLevel = 50;
        double raiseValue = 200;

        return createSoilLevel(name, startLevel, minLevel, raiseValue, consumption);
    }

    private Telemetry<Double> createSoilLevel(String name, double startLevel, double minLevel, double raiseValue, Telemetry<Double> consumption) {
        Telemetry<Double> result = new Telemetry<>(name);
        double currentLevel = startLevel;
        double prevValue = 0;
        for (Telemetry.Point<Double> point : consumption.getPoints()) {
            Timestamp ts = point.getTs();
            double value = point.getValue();

            double delta = value - prevValue;
            if (delta < 0) {
                delta = value;
            }

            currentLevel -= delta;

            if (currentLevel <= minLevel) {
                currentLevel += raiseValue;
            }

            prevValue = value;
            Telemetry.Point<Double> newPoint = new Telemetry.Point<>(ts, currentLevel);
            result.add(newPoint);
        }

        return result;
    }


    private Telemetry<Double> createTemporalTelemetryPlantNitrogenConsumption(PlantType plantType, String variety, ZonedDateTime startDate, ZonedDateTime endDate) {
        switch (plantType) {
            case TOMATO:
                switch (variety) {
                    case "Sungold":
                        return createTemporalTelemetryPlantNitrogenConsumptionTomatoSungold(startDate, endDate);
                    case "Cherry":
                        return createTemporalTelemetryPlantNitrogenConsumptionTomatoCherry(startDate, endDate);
                    default: throw new IllegalArgumentException("Unsupported plant variety: " + plantType + ", " + variety);
                }
            case CUCUMBER:
                switch (variety) {
                    case "English":
                        return createTemporalTelemetryPlantNitrogenConsumptionCucumberEnglish(startDate, endDate);
                    default: throw new IllegalArgumentException("Unsupported plant variety: " + plantType + ", " + variety);
                }
            case ONION:
                switch (variety) {
                    case "Sweet Spanish":
                        return createTemporalTelemetryPlantNitrogenConsumptionOnionSweetSpanish(startDate, endDate);
                    default: throw new IllegalArgumentException("Unsupported plant variety: " + plantType + ", " + variety);
                }
            default: throw new IllegalArgumentException("Unsupported plant: " + plantType);
        }
    }

    private Telemetry<Double> createTemporalTelemetryPlantPhosphorusConsumption(PlantType plantType, String variety, ZonedDateTime startDate, ZonedDateTime endDate) {
        switch (plantType) {
            case TOMATO:
                switch (variety) {
                    case "Sungold":
                        return createTemporalTelemetryPlantPhosphorusConsumptionTomatoSungold(startDate, endDate);
                    case "Cherry":
                        return createTemporalTelemetryPlantPhosphorusConsumptionTomatoCherry(startDate, endDate);
                    default: throw new IllegalArgumentException("Unsupported plant variety: " + plantType + ", " + variety);
                }
            case CUCUMBER:
                switch (variety) {
                    case "English":
                        return createTemporalTelemetryPlantPhosphorusConsumptionCucumberEnglish(startDate, endDate);
                    default: throw new IllegalArgumentException("Unsupported plant variety: " + plantType + ", " + variety);
                }
            case ONION:
                switch (variety) {
                    case "Sweet Spanish":
                        return createTemporalTelemetryPlantPhosphorusConsumptionOnionSweetSpanish(startDate, endDate);
                    default: throw new IllegalArgumentException("Unsupported plant variety: " + plantType + ", " + variety);
                }
            default: throw new IllegalArgumentException("Unsupported plant: " + plantType);
        }
    }

    private Telemetry<Double> createTemporalTelemetryPlantPotassiumConsumption(PlantType plantType, String variety, ZonedDateTime startDate, ZonedDateTime endDate) {
        switch (plantType) {
            case TOMATO:
                switch (variety) {
                    case "Sungold":
                        return createTemporalTelemetryPlantPotassiumConsumptionTomatoSungold(startDate, endDate);
                    case "Cherry":
                        return createTemporalTelemetryPlantPotassiumConsumptionTomatoCherry(startDate, endDate);
                    default: throw new IllegalArgumentException("Unsupported plant variety: " + plantType + ", " + variety);
                }
            case CUCUMBER:
                switch (variety) {
                    case "English":
                        return createTemporalTelemetryPlantPotassiumConsumptionCucumberEnglish(startDate, endDate);
                    default: throw new IllegalArgumentException("Unsupported plant variety: " + plantType + ", " + variety);
                }
            case ONION:
                switch (variety) {
                    case "Sweet Spanish":
                        return createTemporalTelemetryPlantPotassiumConsumptionOnionSweetSpanish(startDate, endDate);
                    default: throw new IllegalArgumentException("Unsupported plant variety: " + plantType + ", " + variety);
                }
            default: throw new IllegalArgumentException("Unsupported plant: " + plantType);
        }
    }


    private Telemetry<Double> createTemporalTelemetryPlantNitrogenConsumptionTomatoSungold(ZonedDateTime startDate, ZonedDateTime endDate) {
        int noiseAmplitude = 3;
        double noiseCoefficient = 1.0;
        int totalPeriodDays = 100;
        List<Integer> periodDays = List.of(30, 60, 100);
        List<Double> periodValues = List.of(150.0, 230.0, 300.0);

        return createTemporalTelemetryPlantConsumption("temporal__nitrogen_consumption__tomato_sungold", startDate, endDate, noiseAmplitude, noiseCoefficient, totalPeriodDays, periodDays, periodValues);
    }

    private Telemetry<Double> createTemporalTelemetryPlantNitrogenConsumptionTomatoCherry(ZonedDateTime startDate, ZonedDateTime endDate) {
        int noiseAmplitude = 3;
        double noiseCoefficient = 1.0;
        int totalPeriodDays = 70;
        List<Integer> periodDays = List.of(30, 50, 70);
        List<Double> periodValues = List.of(150.0, 200.0, 260.0);

        return createTemporalTelemetryPlantConsumption("temporal__nitrogen_consumption__tomato_cherry", startDate, endDate, noiseAmplitude, noiseCoefficient, totalPeriodDays, periodDays, periodValues);
    }

    private Telemetry<Double> createTemporalTelemetryPlantNitrogenConsumptionCucumberEnglish(ZonedDateTime startDate, ZonedDateTime endDate) {
        int noiseAmplitude = 3;
        double noiseCoefficient = 1.0;
        int totalPeriodDays = 60;
        List<Integer> periodDays = List.of(15, 30, 45, 60);
        List<Double> periodValues = List.of(100.0, 220.0, 300.0, 360.0);

        return createTemporalTelemetryPlantConsumption("temporal__nitrogen_consumption__cucumber_english", startDate, endDate, noiseAmplitude, noiseCoefficient, totalPeriodDays, periodDays, periodValues);
    }

    private Telemetry<Double> createTemporalTelemetryPlantNitrogenConsumptionOnionSweetSpanish(ZonedDateTime startDate, ZonedDateTime endDate) {
        int noiseAmplitude = 3;
        double noiseCoefficient = 1.0;
        int totalPeriodDays = 90;
        List<Integer> periodDays = List.of(15, 30, 60, 90);
        List<Double> periodValues = List.of(75.0, 190.0, 309.0, 400.0);

        return createTemporalTelemetryPlantConsumption("temporal__nitrogen_consumption__sweet_spanish", startDate, endDate, noiseAmplitude, noiseCoefficient, totalPeriodDays, periodDays, periodValues);
    }


    private Telemetry<Double> createTemporalTelemetryPlantPhosphorusConsumptionTomatoSungold(ZonedDateTime startDate, ZonedDateTime endDate) {
        int noiseAmplitude = 1;
        double noiseCoefficient = 0.01;
        int totalPeriodDays = 100;
        List<Integer> periodDays = List.of(30, 60, 100);
        List<Double> periodValues = List.of(8.0, 12.0, 22.0);

        return createTemporalTelemetryPlantConsumption("temporal__phosphorus_consumption__tomato_sungold", startDate, endDate, noiseAmplitude, noiseCoefficient, totalPeriodDays, periodDays, periodValues);
    }

    private Telemetry<Double> createTemporalTelemetryPlantPhosphorusConsumptionTomatoCherry(ZonedDateTime startDate, ZonedDateTime endDate) {
        int noiseAmplitude = 1;
        double noiseCoefficient = 0.01;
        int totalPeriodDays = 70;
        List<Integer> periodDays = List.of(30, 50, 70);
        List<Double> periodValues = List.of(5.0, 15.0, 23.0);

        return createTemporalTelemetryPlantConsumption("temporal__phosphorus_consumption__tomato_cherry", startDate, endDate, noiseAmplitude, noiseCoefficient, totalPeriodDays, periodDays, periodValues);
    }

    private Telemetry<Double> createTemporalTelemetryPlantPhosphorusConsumptionCucumberEnglish(ZonedDateTime startDate, ZonedDateTime endDate) {
        int noiseAmplitude = 1;
        double noiseCoefficient = 0.01;
        int totalPeriodDays = 60;
        List<Integer> periodDays = List.of(15, 30, 45, 60);
        List<Double> periodValues = List.of(3.0, 8.0, 20.0, 28.0);

        return createTemporalTelemetryPlantConsumption("temporal__phosphorus_consumption__cucumber_english", startDate, endDate, noiseAmplitude, noiseCoefficient, totalPeriodDays, periodDays, periodValues);
    }

    private Telemetry<Double> createTemporalTelemetryPlantPhosphorusConsumptionOnionSweetSpanish(ZonedDateTime startDate, ZonedDateTime endDate) {
        int noiseAmplitude = 1;
        double noiseCoefficient = 0.01;
        int totalPeriodDays = 90;
        List<Integer> periodDays = List.of(15, 30, 60, 90);
        List<Double> periodValues = List.of(4.0, 10.0, 25.0, 33.0);

        return createTemporalTelemetryPlantConsumption("temporal__phosphorus_consumption__sweet_spanish", startDate, endDate, noiseAmplitude, noiseCoefficient, totalPeriodDays, periodDays, periodValues);
    }


    private Telemetry<Double> createTemporalTelemetryPlantPotassiumConsumptionTomatoSungold(ZonedDateTime startDate, ZonedDateTime endDate) {
        int noiseAmplitude = 3;
        double noiseCoefficient = 1.0;
        int totalPeriodDays = 100;
        List<Integer> periodDays = List.of(30, 60, 100);
        List<Double> periodValues = List.of(80.0, 200.0, 350.0);

        return createTemporalTelemetryPlantConsumption("temporal__potassium_consumption__tomato_sungold", startDate, endDate, noiseAmplitude, noiseCoefficient, totalPeriodDays, periodDays, periodValues);
    }

    private Telemetry<Double> createTemporalTelemetryPlantPotassiumConsumptionTomatoCherry(ZonedDateTime startDate, ZonedDateTime endDate) {
        int noiseAmplitude = 3;
        double noiseCoefficient = 1.0;
        int totalPeriodDays = 70;
        List<Integer> periodDays = List.of(30, 50, 70);
        List<Double> periodValues = List.of(90.0, 250.0, 340.0);

        return createTemporalTelemetryPlantConsumption("temporal__potassium_consumption__tomato_cherry", startDate, endDate, noiseAmplitude, noiseCoefficient, totalPeriodDays, periodDays, periodValues);
    }

    private Telemetry<Double> createTemporalTelemetryPlantPotassiumConsumptionCucumberEnglish(ZonedDateTime startDate, ZonedDateTime endDate) {
        int noiseAmplitude = 3;
        double noiseCoefficient = 1.0;
        int totalPeriodDays = 60;
        List<Integer> periodDays = List.of(15, 30, 45, 60);
        List<Double> periodValues = List.of(80.0, 120.0, 250.0, 380.0);

        return createTemporalTelemetryPlantConsumption("temporal__potassium_consumption__cucumber_english", startDate, endDate, noiseAmplitude, noiseCoefficient, totalPeriodDays, periodDays, periodValues);
    }

    private Telemetry<Double> createTemporalTelemetryPlantPotassiumConsumptionOnionSweetSpanish(ZonedDateTime startDate, ZonedDateTime endDate) {
        int noiseAmplitude = 3;
        double noiseCoefficient = 1.0;
        int totalPeriodDays = 90;
        List<Integer> periodDays = List.of(15, 30, 60, 90);
        List<Double> periodValues = List.of(39.0, 59.0, 158.0, 360.0);

        return createTemporalTelemetryPlantConsumption("temporal__potassium_consumption__sweet_spanish", startDate, endDate, noiseAmplitude, noiseCoefficient, totalPeriodDays, periodDays, periodValues);
    }


    private Telemetry<Double> createTemporalTelemetryPlantConsumption(String name, ZonedDateTime startDate, ZonedDateTime endDate, int noiseAmplitude, double noiseCoefficient, int totalPeriodDays, List<Integer> periodDays, List<Double> periodValues) {
        Telemetry<Double> result = new Telemetry<>(name);

        startDate = startDate.truncatedTo(ChronoUnit.HOURS);
        endDate = endDate.truncatedTo(ChronoUnit.HOURS);
        ZonedDateTime iteratedDate = startDate;
        double prevValue = 0;
        while (iteratedDate.isBefore(endDate)) {
            long iteratedTs = DateTimeUtils.toTs(iteratedDate);
            long daysBetween = ChronoUnit.DAYS.between(startDate, iteratedDate);

            long currentDayCycle = daysBetween % totalPeriodDays;
            if (currentDayCycle == 0) {
                prevValue = 0;
            }
            int periodDayPrev = 0;
            double periodValuePrev = 0;
            for (int i = 0; i < periodDays.size(); i++) {
                int periodDay = periodDays.get(i);
                double periodValue = periodValues.get(i);
                if (currentDayCycle < periodDay) {
                    double value = periodValuePrev + (1.0 * (currentDayCycle - periodDayPrev) * (periodValue - periodValuePrev)) / (periodDay - periodDayPrev);
                    value += RandomUtils.getRandomNumber(0, noiseAmplitude) * noiseCoefficient;
                    value = Math.max(prevValue, value);

                    result.add(new Telemetry.Point<>(Timestamp.of(iteratedTs), value));
                    prevValue = value;
                    break;
                }
                periodDayPrev = periodDay;
                periodValuePrev = periodValue;
            }

            iteratedDate = iteratedDate.plus(1, ChronoUnit.DAYS);
        }
        return result;
    }



    private Asset createPlant(Plant plant, UUID ownerId, UUID assetGroupId) {
        String name = plant.getSystemName();
        String entityType = plant.entityType();

        Asset asset;
        if (tbRestClient.isPe()) {
            asset = tbRestClient.createAsset(name, entityType, new CustomerId(ownerId));
            tbRestClient.addEntitiesToTheGroup(assetGroupId, Set.of(asset.getUuidId()));
        } else {
            asset = tbRestClient.createAsset(name, entityType);
            tbRestClient.assignAssetToCustomer(ownerId, asset.getUuidId());
        }

        Set<Attribute<?>> attributes = Set.of(
                new Attribute<>("name", plant.getName()),
                new Attribute<>("variety", plant.getVariety()),
                new Attribute<>("minRipeningPeriodDay", plant.getMinRipeningCycleDays()),
                new Attribute<>("maxRipeningPeriodDay", plant.getMaxRipeningCycleDays()),
                new Attribute<>("dayMinTemperature", plant.getDayMinTemperature()),
                new Attribute<>("dayMaxTemperature", plant.getDayMaxTemperature()),
                new Attribute<>("nightMinTemperature", plant.getNightMinTemperature()),
                new Attribute<>("nightMaxTemperature", plant.getNightMaxTemperature()),
                new Attribute<>("minMoisture", plant.getMinAirHumidity()),
                new Attribute<>("maxMoisture", plant.getMaxAirHumidity()),
                new Attribute<>("minCo2Concentration", plant.getMinCo2Concentration()),
                new Attribute<>("maxCo2Concentration", plant.getMaxCo2Concentration()),
                new Attribute<>("minLight", plant.getMinLight()),
                new Attribute<>("maxLight", plant.getMaxLight()),
                new Attribute<>("minPh", plant.getMinPh()),
                new Attribute<>("maxPh", plant.getMaxPh())
        );
        tbRestClient.setEntityAttributes(asset.getUuidId(), EntityType.ASSET, Attribute.Scope.SERVER_SCOPE, attributes);

        this.plantToIdMap.put(plant, asset.getUuidId());
        return asset;
    }

    private Asset createGreenhouse(Greenhouse greenhouse, UUID ownerId, UUID assetGroupId) {
        String name = greenhouse.getSystemName();
        String entityType = greenhouse.entityType();

        Asset asset;
        if (tbRestClient.isPe()) {
            asset = tbRestClient.createAsset(name, entityType, new CustomerId(ownerId));
            tbRestClient.addEntitiesToTheGroup(assetGroupId, Set.of(asset.getUuidId()));
        } else {
            asset = tbRestClient.createAsset(name, entityType);
            tbRestClient.assignAssetToCustomer(ownerId, asset.getUuidId());
        }

        Set<Attribute<?>> attributes = Set.of(
                new Attribute<>("plant_type", greenhouse.getPlantType().toString())
        );
        tbRestClient.setEntityAttributes(asset.getUuidId(), EntityType.ASSET, Attribute.Scope.SERVER_SCOPE, attributes);

        this.greenhouseToIdMap.put(greenhouse, asset.getUuidId());
        return asset;
    }

    private Asset createSection(Section section, UUID ownerId, UUID assetGroupId) {
        String name = section.getSystemName();
        String entityType = section.entityType();

        Asset asset;
        if (tbRestClient.isPe()) {
            asset = tbRestClient.createAsset(name, entityType, new CustomerId(ownerId));
            tbRestClient.addEntitiesToTheGroup(assetGroupId, Set.of(asset.getUuidId()));
        } else {
            asset = tbRestClient.createAsset(name, entityType);
            tbRestClient.assignAssetToCustomer(ownerId, asset.getUuidId());
        }

        Set<Attribute<?>> attributes = Set.of(
                new Attribute<>("position_height", section.getPositionHeight()),
                new Attribute<>("position_width", section.getPositionWidth())
        );
        tbRestClient.setEntityAttributes(asset.getUuidId(), EntityType.ASSET, Attribute.Scope.SERVER_SCOPE, attributes);

        return asset;
    }

    private Device createSoilNpkSensor(SoilNpkSensor soilNpkSensor, UUID ownerId, UUID deviceGroupId) {
        String name = soilNpkSensor.getSystemName();
        String entityType = soilNpkSensor.entityType();

        Device device;
        if (tbRestClient.isPe()) {
            device = tbRestClient.createDevice(name, entityType, new CustomerId(ownerId));
            tbRestClient.addEntitiesToTheGroup(deviceGroupId, Set.of(device.getUuidId()));
        } else {
            device = tbRestClient.createDevice(name, entityType);
            tbRestClient.assignDeviceToCustomer(ownerId, device.getUuidId());
        }
        DeviceCredentials deviceCredentials = tbRestClient.getDeviceCredentials(device.getUuidId());


        tbRestClient.pushTelemetry(deviceCredentials.getCredentialsId(), soilNpkSensor.getNitrogen());
        tbRestClient.pushTelemetry(deviceCredentials.getCredentialsId(), soilNpkSensor.getPotassium());
        tbRestClient.pushTelemetry(deviceCredentials.getCredentialsId(), soilNpkSensor.getPhosphorus());

        this.soilNpkSensorToIdMap.put(soilNpkSensor, device.getUuidId());
        return device;
    }

    private Device createSoilWarmMoistureSensor(SoilWarmMoistureSensor soilWarmMoistureSensor, UUID ownerId, UUID deviceGroupId) {
        String name = soilWarmMoistureSensor.getSystemName();
        String entityType = soilWarmMoistureSensor.entityType();

        Device device;
        if (tbRestClient.isPe()) {
            device = tbRestClient.createDevice(name, entityType, new CustomerId(ownerId));
            tbRestClient.addEntitiesToTheGroup(deviceGroupId, Set.of(device.getUuidId()));
        } else {
            device = tbRestClient.createDevice(name, entityType);
            tbRestClient.assignDeviceToCustomer(ownerId, device.getUuidId());
        }
        DeviceCredentials deviceCredentials = tbRestClient.getDeviceCredentials(device.getUuidId());


        tbRestClient.pushTelemetry(deviceCredentials.getCredentialsId(), soilWarmMoistureSensor.getTemperature());
        tbRestClient.pushTelemetry(deviceCredentials.getCredentialsId(), soilWarmMoistureSensor.getMoisture());

        this.soilWarmMoistureSensorToIdMap.put(soilWarmMoistureSensor, device.getUuidId());
        return device;
    }

    private Device createSoilAciditySensor(SoilAciditySensor soilAciditySensor, UUID ownerId, UUID deviceGroupId) {
        String name = soilAciditySensor.getSystemName();
        String entityType = soilAciditySensor.entityType();

        Device device;
        if (tbRestClient.isPe()) {
            device = tbRestClient.createDevice(name, entityType, new CustomerId(ownerId));
            tbRestClient.addEntitiesToTheGroup(deviceGroupId, Set.of(device.getUuidId()));
        } else {
            device = tbRestClient.createDevice(name, entityType);
            tbRestClient.assignDeviceToCustomer(ownerId, device.getUuidId());
        }
        DeviceCredentials deviceCredentials = tbRestClient.getDeviceCredentials(device.getUuidId());

        tbRestClient.pushTelemetry(deviceCredentials.getCredentialsId(), soilAciditySensor.getAcidity());

        this.soilAciditySensorToIdMap.put(soilAciditySensor, device.getUuidId());
        return device;
    }

    private Device createInsideAirWarmHumiditySensor(InsideAirWarmHumiditySensor insideAirWarmHumiditySensor, UUID ownerId, UUID deviceGroupId) {
        String name = insideAirWarmHumiditySensor.getSystemName();
        String entityType = insideAirWarmHumiditySensor.entityType();

        Device device;
        if (tbRestClient.isPe()) {
            device = tbRestClient.createDevice(name, entityType, new CustomerId(ownerId));
            tbRestClient.addEntitiesToTheGroup(deviceGroupId, Set.of(device.getUuidId()));
        } else {
            device = tbRestClient.createDevice(name, entityType);
            tbRestClient.assignDeviceToCustomer(ownerId, device.getUuidId());
        }
        DeviceCredentials deviceCredentials = tbRestClient.getDeviceCredentials(device.getUuidId());


        tbRestClient.pushTelemetry(deviceCredentials.getCredentialsId(), insideAirWarmHumiditySensor.getTemperature());
        tbRestClient.pushTelemetry(deviceCredentials.getCredentialsId(), insideAirWarmHumiditySensor.getHumidity());

        this.insideAirWarmHumiditySensorToIdMap.put(insideAirWarmHumiditySensor, device.getUuidId());
        return device;
    }

    private Device createInsideCO2Sensor(InsideCO2Sensor insideCO2Sensor, UUID ownerId, UUID deviceGroupId) {
        String name = insideCO2Sensor.getSystemName();
        String entityType = insideCO2Sensor.entityType();

        Device device;
        if (tbRestClient.isPe()) {
            device = tbRestClient.createDevice(name, entityType, new CustomerId(ownerId));
            tbRestClient.addEntitiesToTheGroup(deviceGroupId, Set.of(device.getUuidId()));
        } else {
            device = tbRestClient.createDevice(name, entityType);
            tbRestClient.assignDeviceToCustomer(ownerId, device.getUuidId());
        }
        DeviceCredentials deviceCredentials = tbRestClient.getDeviceCredentials(device.getUuidId());


        tbRestClient.pushTelemetry(deviceCredentials.getCredentialsId(), insideCO2Sensor.getConcentration());

        this.insideCO2SensorToIdMap.put(insideCO2Sensor, device.getUuidId());
        return device;
    }

    private Device createInsideLightSensor(InsideLightSensor insideLightSensor, UUID ownerId, UUID deviceGroupId) {
        String name = insideLightSensor.getSystemName();
        String entityType = insideLightSensor.entityType();

        Device device;
        if (tbRestClient.isPe()) {
            device = tbRestClient.createDevice(name, entityType, new CustomerId(ownerId));
            tbRestClient.addEntitiesToTheGroup(deviceGroupId, Set.of(device.getUuidId()));
        } else {
            device = tbRestClient.createDevice(name, entityType);
            tbRestClient.assignDeviceToCustomer(ownerId, device.getUuidId());
        }
        DeviceCredentials deviceCredentials = tbRestClient.getDeviceCredentials(device.getUuidId());


        tbRestClient.pushTelemetry(deviceCredentials.getCredentialsId(), insideLightSensor.getLight());

        this.insideLightSensorToIdMap.put(insideLightSensor, device.getUuidId());
        return device;
    }

    private Device createHarvestReporter(HarvestReporter harvestReporter, UUID ownerId, UUID deviceGroupId) {
        String name = harvestReporter.getSystemName();
        String entityType = harvestReporter.entityType();

        Device device;
        if (tbRestClient.isPe()) {
            device = tbRestClient.createDevice(name, entityType, new CustomerId(ownerId));
            tbRestClient.addEntitiesToTheGroup(deviceGroupId, Set.of(device.getUuidId()));
        } else {
            device = tbRestClient.createDevice(name, entityType);
            tbRestClient.assignDeviceToCustomer(ownerId, device.getUuidId());
        }
        DeviceCredentials deviceCredentials = tbRestClient.getDeviceCredentials(device.getUuidId());


        tbRestClient.pushTelemetry(deviceCredentials.getCredentialsId(), harvestReporter.getCropWeight());
        tbRestClient.pushTelemetry(deviceCredentials.getCredentialsId(), harvestReporter.getWorkerInCharge());

        this.harvestReporterToIdMap.put(harvestReporter, device.getUuidId());
        return device;
    }

    private Device createEnergyMeter(EnergyMeter energyMeter, UUID ownerId, UUID deviceGroupId) {
        String name = energyMeter.getSystemName();
        String entityType = energyMeter.entityType();

        Device device;
        if (tbRestClient.isPe()) {
            device = tbRestClient.createDevice(name, entityType, new CustomerId(ownerId));
            tbRestClient.addEntitiesToTheGroup(deviceGroupId, Set.of(device.getUuidId()));
        } else {
            device = tbRestClient.createDevice(name, entityType);
            tbRestClient.assignDeviceToCustomer(ownerId, device.getUuidId());
        }
        DeviceCredentials deviceCredentials = tbRestClient.getDeviceCredentials(device.getUuidId());


        tbRestClient.pushTelemetry(deviceCredentials.getCredentialsId(), energyMeter.getConsumptionEnergy());

        this.energyMeterToIdMap.put(energyMeter, device.getUuidId());
        return device;
    }

    private Device createWaterMeter(WaterMeter waterMeter, UUID ownerId, UUID deviceGroupId) {
        String name = waterMeter.getSystemName();
        String entityType = waterMeter.entityType();

        Device device;
        if (tbRestClient.isPe()) {
            device = tbRestClient.createDevice(name, entityType, new CustomerId(ownerId));
            tbRestClient.addEntitiesToTheGroup(deviceGroupId, Set.of(device.getUuidId()));
        } else {
            device = tbRestClient.createDevice(name, entityType);
            tbRestClient.assignDeviceToCustomer(ownerId, device.getUuidId());
        }
        DeviceCredentials deviceCredentials = tbRestClient.getDeviceCredentials(device.getUuidId());


        tbRestClient.pushTelemetry(deviceCredentials.getCredentialsId(), waterMeter.getConsumptionWater());

        this.waterMeterToIdMap.put(waterMeter, device.getUuidId());
        return device;
    }

    private Device createOutsideAirWarmHumiditySensor(OutsideAirWarmHumiditySensor outsideAirWarmHumiditySensor, UUID ownerId, UUID deviceGroupId) {
        String name = outsideAirWarmHumiditySensor.getSystemName();
        String entityType = outsideAirWarmHumiditySensor.entityType();

        Device device;
        if (tbRestClient.isPe()) {
            device = tbRestClient.createDevice(name, entityType, new CustomerId(ownerId));
            tbRestClient.addEntitiesToTheGroup(deviceGroupId, Set.of(device.getUuidId()));
        } else {
            device = tbRestClient.createDevice(name, entityType);
            tbRestClient.assignDeviceToCustomer(ownerId, device.getUuidId());
        }
        DeviceCredentials deviceCredentials = tbRestClient.getDeviceCredentials(device.getUuidId());


        tbRestClient.pushTelemetry(deviceCredentials.getCredentialsId(), outsideAirWarmHumiditySensor.getTemperature());
        tbRestClient.pushTelemetry(deviceCredentials.getCredentialsId(), outsideAirWarmHumiditySensor.getHumidity());

        this.outsideAirWarmHumiditySensorToIdMap.put(outsideAirWarmHumiditySensor, device.getUuidId());
        return device;
    }

    private Device createOutsideLightSensor(OutsideLightSensor outsideLightSensor, UUID ownerId, UUID deviceGroupId) {
        String name = outsideLightSensor.getSystemName();
        String entityType = outsideLightSensor.entityType();

        Device device;
        if (tbRestClient.isPe()) {
            device = tbRestClient.createDevice(name, entityType, new CustomerId(ownerId));
            tbRestClient.addEntitiesToTheGroup(deviceGroupId, Set.of(device.getUuidId()));
        } else {
            device = tbRestClient.createDevice(name, entityType);
            tbRestClient.assignDeviceToCustomer(ownerId, device.getUuidId());
        }
        DeviceCredentials deviceCredentials = tbRestClient.getDeviceCredentials(device.getUuidId());


        tbRestClient.pushTelemetry(deviceCredentials.getCredentialsId(), outsideLightSensor.getLight());

        this.outsideLightSensorToIdMap.put(outsideLightSensor, device.getUuidId());
        return device;
    }


    private double getNodePositionX(int greenhouseCounter) {
        return RuleNodeAdditionalInfo.CELL_SIZE;
    }

    private double getNodePositionY(int greenhouseCounter) {
        return RuleNodeAdditionalInfo.CELL_SIZE;
    }

    private String getGreenhouseGeneratorCode(double latitude, double longitude, String apiId) throws IOException {
        String fileContent = this.fileService.getFileContent(getSolutionName(), "greenhouse_generator.js");
        fileContent = fileContent.replace("PUT_LATITUDE", String.valueOf(latitude));
        fileContent = fileContent.replace("PUT_LONGITUDE", String.valueOf(longitude));
        fileContent = fileContent.replace("PUT_API_ID", apiId);
        return fileContent;
    }
}

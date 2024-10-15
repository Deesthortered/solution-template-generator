package org.thingsboard.trendz.generator.solution.greenhouse;

import com.google.common.base.Functions;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
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
import org.thingsboard.trendz.generator.solution.greenhouse.configuration.PlantConfiguration;
import org.thingsboard.trendz.generator.solution.greenhouse.configuration.StationCity;
import org.thingsboard.trendz.generator.solution.greenhouse.configuration.WeatherData;
import org.thingsboard.trendz.generator.solution.greenhouse.configuration.WorkerInChargeName;
import org.thingsboard.trendz.generator.solution.greenhouse.model.EnergyMeter;
import org.thingsboard.trendz.generator.solution.greenhouse.model.Greenhouse;
import org.thingsboard.trendz.generator.solution.greenhouse.model.HarvestReporter;
import org.thingsboard.trendz.generator.solution.greenhouse.model.InsideAirWarmHumiditySensor;
import org.thingsboard.trendz.generator.solution.greenhouse.model.InsideCO2Sensor;
import org.thingsboard.trendz.generator.solution.greenhouse.model.InsideLightSensor;
import org.thingsboard.trendz.generator.solution.greenhouse.model.OutsideAirWarmHumiditySensor;
import org.thingsboard.trendz.generator.solution.greenhouse.model.OutsideLightSensor;
import org.thingsboard.trendz.generator.solution.greenhouse.model.Plant;
import org.thingsboard.trendz.generator.solution.greenhouse.model.PlantName;
import org.thingsboard.trendz.generator.solution.greenhouse.model.Section;
import org.thingsboard.trendz.generator.solution.greenhouse.model.SoilAciditySensor;
import org.thingsboard.trendz.generator.solution.greenhouse.model.SoilNpkSensor;
import org.thingsboard.trendz.generator.solution.greenhouse.model.SoilWarmMoistureSensor;
import org.thingsboard.trendz.generator.solution.greenhouse.model.WaterMeter;
import org.thingsboard.trendz.generator.utils.DateTimeUtils;
import org.thingsboard.trendz.generator.utils.JsonUtils;
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
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
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

    private static final int DAY_START_HOUR = 8;
    private static final int NIGHT_START_HOUR = 20;
    private static final int MIN_WORD_CO2_CONCENTRATION = 400;

    private final TbRestClient tbRestClient;
    private final FileService fileService;
    private final AnomalyService anomalyService;
    private final RuleChainBuildingService ruleChainBuildingService;
    private final DashboardService dashboardService;

    private final Map<PlantConfiguration, Plant> configurationToPlantMap = new HashMap<>();

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
                ModelData data = makeData(true, ZonedDateTime.now(), true, 0L, 0L);
                validateData(data);
            }

            log.info("Greenhouse Solution - validation is completed!");
        } catch (Exception e) {
            throw new SolutionValidationException(getSolutionName(), e);
        }
    }

    @Override
    public void generate(boolean skipTelemetry, ZonedDateTime startYear, boolean strictGeneration, boolean fullTelemetryGeneration, long startGenerationTime, long endGenerationTime) {
        log.info("Greenhouse Solution - start generation");
        try {
            CustomerData customerData = createCustomerData(strictGeneration);
            ModelData data = makeData(skipTelemetry, startYear, fullTelemetryGeneration, startGenerationTime, endGenerationTime);
            applyData(data, customerData, strictGeneration);
            createRuleChain(data, strictGeneration);
            dashboardService.createDashboardItems(getSolutionName(), customerData.getCustomer().getId(), strictGeneration);

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
                ModelData data = makeData(true, ZonedDateTime.now(), true, 0L, 0L);
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


    private CustomerData createCustomerData(boolean strictGeneration) {
        var customer = strictGeneration
                ? tbRestClient.createCustomer(CUSTOMER_TITLE)
                : tbRestClient.createCustomerIfNotExists(CUSTOMER_TITLE);
        var customerUser = this.tbRestClient.createCustomerUser(
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


    private void createRuleChain(ModelData data, boolean strictGeneration) {
        if (!strictGeneration) {
            deleteRuleChain();
        }
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
                int index = nodes.size();

                UUID greenhouseId = this.greenhouseToIdMap.get(greenhouse);
                String greenhouseName = greenhouse.getSystemName();

                Plant plant = greenhouse.getPlant();
                OutsideAirWarmHumiditySensor outsideAirWarmHumiditySensor = greenhouse.getOutsideAirWarmHumiditySensor();
                OutsideLightSensor outsideLightSensor = greenhouse.getOutsideLightSensor();
                InsideAirWarmHumiditySensor insideAirWarmHumiditySensor = greenhouse.getInsideAirWarmHumiditySensor();
                InsideLightSensor insideLightSensor = greenhouse.getInsideLightSensor();
                InsideCO2Sensor insideCO2Sensor = greenhouse.getInsideCO2Sensor();
                EnergyMeter energyMeter = greenhouse.getEnergyMeter();
                WaterMeter waterMeter = greenhouse.getWaterMeter();

                String greenhouseGeneratorCode = getGreenhouseGeneratorCode(greenhouse.getLatitude(), greenhouse.getLongitude(), WEATHER_API_TOKEN);
                RuleNode greenhouseGeneratorNode = this.ruleChainBuildingService.createGeneratorNode(
                        String.format("%s, %s: Generator", greenhouseName, plant.getSystemName()),
                        greenhouseId,
                        EntityType.ASSET,
                        greenhouseGeneratorCode,
                        getNodePositionX(greenhouseCounter, 0, 0),
                        getNodePositionY(greenhouseCounter, 0, 0)
                );

                RuleNode greenhouseWeatherApiCallNode = this.ruleChainBuildingService.createRestApiCallNode(
                        String.format("%s: Get Weather", greenhouseName),
                        WEATHER_API_URL,
                        "GET",
                        getNodePositionX(greenhouseCounter, 0, 1),
                        getNodePositionY(greenhouseCounter, 0, 1)
                );

                String scriptClearRestMetadata = this.fileService.getFileContent(getSolutionName(), "clear_rest_metadata.js");
                RuleNode clearRestMatadataNode = this.ruleChainBuildingService.createTransformationNode(
                        String.format("%s: Clear REST Metadata", greenhouseName),
                        scriptClearRestMetadata,
                        getNodePositionX(greenhouseCounter, 0, 2),
                        getNodePositionY(greenhouseCounter, 0, 2)
                );

                ///
                RuleNode greenhouseToPlantOriginatorNode = this.ruleChainBuildingService.createChangeOriginatorNode(
                        String.format("%s: To Plant", greenhouseName),
                        plant.getSystemName(),
                        EntityType.ASSET,
                        getNodePositionX(greenhouseCounter, 0, 3),
                        getNodePositionY(greenhouseCounter, 0, 3)
                );

                RuleNode greenhousePlantAttributesNode = this.ruleChainBuildingService.createOriginatorAttributesNode(
                        String.format("%s: Get Plant Attributes", greenhouseName),
                        Collections.emptyList(),
                        Collections.emptyList(),
                        List.of(
                                "dayMinTemperature",
                                "dayMaxTemperature",
                                "nightMinTemperature",
                                "nightMaxTemperature",
                                "dayMinLight",
                                "dayMaxLight",
                                "nightMinLight",
                                "nightMaxLight",
                                "minAirHumidity",
                                "maxAirHumidity",
                                "minSoilMoisture",
                                "maxSoilMoisture",
                                "minSoilTemperature",
                                "maxSoilTemperature",
                                "minCo2Concentration",
                                "maxCo2Concentration",
                                "minPh",
                                "maxPh",
                                "minRipeningPeriodDays",
                                "maxRipeningPeriodDays",
                                "minNitrogenLevel",
                                "maxNitrogenLevel",
                                "minPhosphorusLevel",
                                "maxPhosphorusLevel",
                                "minPotassiumLevel",
                                "maxPotassiumLevel",
                                "averageCropWeight",
                                "growthPeriodsDayList",
                                "growthPeriodsNitrogenConsumption",
                                "growthPeriodsPhosphorusConsumption",
                                "growthPeriodsPotassiumConsumption"
                        ),
                        Collections.emptyList(),
                        false,
                        getNodePositionX(greenhouseCounter, 0, 4),
                        getNodePositionY(greenhouseCounter, 0, 4)
                );

                ///
                RuleNode toCo2SensorOriginatorNode = this.ruleChainBuildingService.createChangeOriginatorNode(
                        String.format("%s: To CO2 Sensor", greenhouseName),
                        insideCO2Sensor.getSystemName(),
                        EntityType.DEVICE,
                        getNodePositionX(greenhouseCounter, 0, 5),
                        getNodePositionY(greenhouseCounter, 0, 5)
                );

                RuleNode Co2SensorAttributesNode = this.ruleChainBuildingService.createOriginatorAttributesNode(
                        String.format("%s: Get CO2 Sensor Attributes", greenhouseName),
                        Collections.emptyList(),
                        Collections.emptyList(),
                        Collections.emptyList(),
                        List.of("concentration"),
                        false,
                        getNodePositionX(greenhouseCounter, 0, 6),
                        getNodePositionY(greenhouseCounter, 0, 6)
                );

                ///
                RuleNode toWarmHumidityInSensorOriginatorNode = this.ruleChainBuildingService.createChangeOriginatorNode(
                        String.format("%s: To Warm-Humidity In Sensor", greenhouseName),
                        insideAirWarmHumiditySensor.getSystemName(),
                        EntityType.DEVICE,
                        getNodePositionX(greenhouseCounter, 0, 7),
                        getNodePositionY(greenhouseCounter, 0, 7)
                );

                RuleNode WarmHumidityInSensorAttributesNode = this.ruleChainBuildingService.createOriginatorAttributesNode(
                        String.format("%s: Get Warm-Humidity In Attributes", greenhouseName),
                        Collections.emptyList(),
                        Collections.emptyList(),
                        Collections.emptyList(),
                        List.of("temperature_in", "humidity_in", "temp_heatingMode", "temp_coolingMode", "temp_humidificationMode", "temp_dehumidificationMode"),
                        false,
                        getNodePositionX(greenhouseCounter, 0, 8),
                        getNodePositionY(greenhouseCounter, 0, 8)
                );

                ///
                String scriptWeatherToHumidityTemperature = this.fileService.getFileContent(getSolutionName(), "raw_weather_to_temp_humidity.js");
                RuleNode outsideAirTempHumidityTelemetryNode = this.ruleChainBuildingService.createTransformationNode(
                        String.format("%s: Map To Temp+Humidity Out", greenhouseName),
                        scriptWeatherToHumidityTemperature,
                        getNodePositionX(greenhouseCounter, 0, 9),
                        getNodePositionY(greenhouseCounter, 0, 9)
                );

                String scriptWeatherToLight = this.fileService.getFileContent(getSolutionName(), "raw_weather_to_light.js");
                RuleNode outsideLightTelemetryNode = this.ruleChainBuildingService.createTransformationNode(
                        String.format("%s: Map To Light-Out", greenhouseName),
                        scriptWeatherToLight,
                        getNodePositionX(greenhouseCounter, 0, 10),
                        getNodePositionY(greenhouseCounter, 0, 10)
                );

                String scriptLightIn = this.fileService.getFileContent(getSolutionName(), "light_in.js");
                RuleNode insideLightTelemetryNode = this.ruleChainBuildingService.createTransformationNode(
                        String.format("%s: Map To Light-In", greenhouseName),
                        scriptLightIn,
                        getNodePositionX(greenhouseCounter, 0, 11),
                        getNodePositionY(greenhouseCounter, 0, 11)
                );

                String scriptCo2 = this.fileService.getFileContent(getSolutionName(), "co2.js");
                RuleNode co2TelemetryNode = this.ruleChainBuildingService.createTransformationNode(
                        String.format("%s: Map To CO2", greenhouseName),
                        scriptCo2,
                        getNodePositionX(greenhouseCounter, 0, 12),
                        getNodePositionY(greenhouseCounter, 0, 12)
                );

                String scriptTemperatureIn = this.fileService.getFileContent(getSolutionName(), "temperature_in.js");
                RuleNode temperatureInTelemetryNode = this.ruleChainBuildingService.createTransformationNode(
                        String.format("%s: Map To Temperature In", greenhouseName),
                        scriptTemperatureIn,
                        getNodePositionX(greenhouseCounter, 0, 13),
                        getNodePositionY(greenhouseCounter, 0, 13)
                );

                String scriptHumidityIn = this.fileService.getFileContent(getSolutionName(), "humidity_in.js");
                RuleNode humidityInTelemetryNode = this.ruleChainBuildingService.createTransformationNode(
                        String.format("%s: Map To Humidity In", greenhouseName),
                        scriptHumidityIn,
                        getNodePositionX(greenhouseCounter, 0, 14),
                        getNodePositionY(greenhouseCounter, 0, 14)
                );

                ///
                String scriptFinishWarmHumidityInSensor = this.fileService.getFileContent(getSolutionName(), "finish_warm_humidity_in.js");
                RuleNode finishWarmHumidityInSensorNode = this.ruleChainBuildingService.createTransformationNode(
                        String.format("%s: Finish - Map Warm-Humidity In Sensor", greenhouseName),
                        scriptFinishWarmHumidityInSensor,
                        getNodePositionX(greenhouseCounter, 0, 15),
                        getNodePositionY(greenhouseCounter, 0, 15)
                );

                ///
                RuleNode finishWarmHumidityOutSensorOriginatorNode = this.ruleChainBuildingService.createChangeOriginatorNode(
                        String.format("%s: Finish - To Warm-Humidity Out Sensor", greenhouseName),
                        outsideAirWarmHumiditySensor.getSystemName(),
                        EntityType.DEVICE,
                        getNodePositionX(greenhouseCounter, 0, 16),
                        getNodePositionY(greenhouseCounter, 0, 16)
                );

                String scriptFinishWarmHumidityOutSensor = this.fileService.getFileContent(getSolutionName(), "finish_warm_humidity_out.js");
                RuleNode finishWarmHumidityOutSensorNode = this.ruleChainBuildingService.createTransformationNode(
                        String.format("%s: Finish - Map Warm-Humidity Out Sensor", greenhouseName),
                        scriptFinishWarmHumidityOutSensor,
                        getNodePositionX(greenhouseCounter, 0, 17),
                        getNodePositionY(greenhouseCounter, 0, 17)
                );

                ///
                RuleNode finishLightOutSensorOriginatorNode = this.ruleChainBuildingService.createChangeOriginatorNode(
                        String.format("%s: Finish - To Light Out Sensor", greenhouseName),
                        outsideLightSensor.getSystemName(),
                        EntityType.DEVICE,
                        getNodePositionX(greenhouseCounter, 0, 18),
                        getNodePositionY(greenhouseCounter, 0, 18)
                );

                String scriptFinishLightOutSensor = this.fileService.getFileContent(getSolutionName(), "finish_light_out.js");
                RuleNode finishLightOutSensorNode = this.ruleChainBuildingService.createTransformationNode(
                        String.format("%s: Finish - Map Light Out Sensor", greenhouseName),
                        scriptFinishLightOutSensor,
                        getNodePositionX(greenhouseCounter, 0, 19),
                        getNodePositionY(greenhouseCounter, 0, 19)
                );

                ///
                RuleNode finishLightInSensorOriginatorNode = this.ruleChainBuildingService.createChangeOriginatorNode(
                        String.format("%s: Finish - To Light In Sensor", greenhouseName),
                        insideLightSensor.getSystemName(),
                        EntityType.DEVICE,
                        getNodePositionX(greenhouseCounter, 0, 20),
                        getNodePositionY(greenhouseCounter, 0, 20)
                );

                String scriptFinishLightInSensor = this.fileService.getFileContent(getSolutionName(), "finish_light_in.js");
                RuleNode finishLightInSensorNode = this.ruleChainBuildingService.createTransformationNode(
                        String.format("%s: Finish - Map Light In Sensor", greenhouseName),
                        scriptFinishLightInSensor,
                        getNodePositionX(greenhouseCounter, 0, 21),
                        getNodePositionY(greenhouseCounter, 0, 21)
                );

                ///
                RuleNode finishCo2SensorOriginatorNode = this.ruleChainBuildingService.createChangeOriginatorNode(
                        String.format("%s: Finish - To Co2 Sensor", greenhouseName),
                        insideCO2Sensor.getSystemName(),
                        EntityType.DEVICE,
                        getNodePositionX(greenhouseCounter, 0, 22),
                        getNodePositionY(greenhouseCounter, 0, 22)
                );

                String scriptFinishCo2Sensor = this.fileService.getFileContent(getSolutionName(), "finish_co2.js");
                RuleNode finishCo2SensorNode = this.ruleChainBuildingService.createTransformationNode(
                        String.format("%s: Finish - Map Co2 Sensor", greenhouseName),
                        scriptFinishCo2Sensor,
                        getNodePositionX(greenhouseCounter, 0, 23),
                        getNodePositionY(greenhouseCounter, 0, 23)
                );

                ///
                RuleNode greenhouseSaveNode = this.ruleChainBuildingService.createSaveNode(
                        String.format("%s: Save Greenhouse Telemetry", greenhouseName),
                        getNodePositionX(greenhouseCounter, 0, 24),
                        getNodePositionY(greenhouseCounter, 0, 24)
                );

                ///
                String scriptMapper = this.fileService.getFileContent(getSolutionName(), "mapper.js");
                RuleNode mapToSectionsNode = this.ruleChainBuildingService.createTransformationNode(
                        String.format("%s: Go to Sections", greenhouseName),
                        scriptMapper,
                        getNodePositionX(greenhouseCounter, 1, 0),
                        getNodePositionY(greenhouseCounter, 1, 0)
                );

                RuleNode toWaterMeterOriginatorNode = this.ruleChainBuildingService.createChangeOriginatorNode(
                        String.format("%s: To Water Meter", greenhouseName),
                        waterMeter.getSystemName(),
                        EntityType.DEVICE,
                        getNodePositionX(greenhouseCounter, 1, 2),
                        getNodePositionY(greenhouseCounter, 1, 2)
                );

                RuleNode waterMeterNode = this.ruleChainBuildingService.createOriginatorAttributesNode(
                        String.format("%s: Get Water Meter Temp Telemetry", greenhouseName),
                        Collections.emptyList(),
                        Collections.emptyList(),
                        Collections.emptyList(),
                        List.of(
                                "temp_irrigations",
                                "temp_ts"
                        ),
                        false,
                        getNodePositionX(greenhouseCounter, 1, 3),
                        getNodePositionY(greenhouseCounter, 1, 3)
                );


                String scriptEnergyWaterMeter = this.fileService.getFileContent(getSolutionName(), "energy_water_meter.js");
                RuleNode mapToEnergyWaterMeterTelemetryNode = this.ruleChainBuildingService.createTransformationNode(
                        String.format("%s: Map To Energy & Water Meter", greenhouseName),
                        scriptEnergyWaterMeter,
                        getNodePositionX(greenhouseCounter, 1, 4),
                        getNodePositionY(greenhouseCounter, 1, 4)
                );

                String scriptFinishWaterMeter = this.fileService.getFileContent(getSolutionName(), "finish_water_meter.js");
                RuleNode finishWaterMeterTelemetryNode = this.ruleChainBuildingService.createTransformationNode(
                        String.format("%s: Finish - Water Meter", greenhouseName),
                        scriptFinishWaterMeter,
                        getNodePositionX(greenhouseCounter, 1, 5),
                        getNodePositionY(greenhouseCounter, 1, 5)
                );

                RuleNode toEnergyMeterOriginatorNode = this.ruleChainBuildingService.createChangeOriginatorNode(
                        String.format("%s: To Energy Meter", greenhouseName),
                        energyMeter.getSystemName(),
                        EntityType.DEVICE,
                        getNodePositionX(greenhouseCounter, 1, 6),
                        getNodePositionY(greenhouseCounter, 1, 6)
                );

                String scriptFinishEnergyMeter = this.fileService.getFileContent(getSolutionName(), "finish_energy_meter.js");
                RuleNode finishEnergyMeterTelemetryNode = this.ruleChainBuildingService.createTransformationNode(
                        String.format("%s: Finish - Energy Meter", greenhouseName),
                        scriptFinishEnergyMeter,
                        getNodePositionX(greenhouseCounter, 1, 7),
                        getNodePositionY(greenhouseCounter, 1, 7)
                );

                RuleNode metersSaveNode = this.ruleChainBuildingService.createSaveNode(
                        String.format("%s: Save Meter Telemetry", greenhouseName),
                        getNodePositionX(greenhouseCounter, 1, 8),
                        getNodePositionY(greenhouseCounter, 1, 8)
                );

                ///
                nodes.add(greenhouseGeneratorNode);                     // 0
                nodes.add(greenhouseWeatherApiCallNode);                // 1
                nodes.add(clearRestMatadataNode);                       // 2
                nodes.add(greenhouseToPlantOriginatorNode);             // 3
                nodes.add(greenhousePlantAttributesNode);               // 4
                nodes.add(toCo2SensorOriginatorNode);                   // 5
                nodes.add(Co2SensorAttributesNode);                     // 6
                nodes.add(toWarmHumidityInSensorOriginatorNode);        // 7
                nodes.add(WarmHumidityInSensorAttributesNode);          // 8
                nodes.add(outsideAirTempHumidityTelemetryNode);         // 9
                nodes.add(outsideLightTelemetryNode);                   // 10
                nodes.add(insideLightTelemetryNode);                    // 11
                nodes.add(co2TelemetryNode);                            // 12
                nodes.add(temperatureInTelemetryNode);                  // 13
                nodes.add(humidityInTelemetryNode);                     // 14

                nodes.add(finishWarmHumidityInSensorNode);              // 15

                nodes.add(finishWarmHumidityOutSensorOriginatorNode);   // 16
                nodes.add(finishWarmHumidityOutSensorNode);             // 17

                nodes.add(finishLightOutSensorOriginatorNode);          // 18
                nodes.add(finishLightOutSensorNode);                    // 19

                nodes.add(finishLightInSensorOriginatorNode);           // 20
                nodes.add(finishLightInSensorNode);                     // 21

                nodes.add(finishCo2SensorOriginatorNode);               // 22
                nodes.add(finishCo2SensorNode);                         // 23

                nodes.add(greenhouseSaveNode);                          // 24
                nodes.add(mapToSectionsNode);                           // 25

                ///
                nodes.add(toWaterMeterOriginatorNode);                  // 26
                nodes.add(waterMeterNode);                              // 27
                nodes.add(mapToEnergyWaterMeterTelemetryNode);          // 28
                nodes.add(finishWaterMeterTelemetryNode);               // 29
                nodes.add(toEnergyMeterOriginatorNode);                 // 30
                nodes.add(finishEnergyMeterTelemetryNode);              // 31
                nodes.add(metersSaveNode);                              // 32

                connections.add(ruleChainBuildingService.createRuleConnection(index, index + 1));
                connections.add(ruleChainBuildingService.createRuleConnection(index + 1, index + 2));
                connections.add(ruleChainBuildingService.createRuleConnection(index + 2, index + 3));
                connections.add(ruleChainBuildingService.createRuleConnection(index + 2, index + 3));
                connections.add(ruleChainBuildingService.createRuleConnection(index + 3, index + 4));
                connections.add(ruleChainBuildingService.createRuleConnection(index + 4, index + 5));
                connections.add(ruleChainBuildingService.createRuleConnection(index + 5, index + 6));
                connections.add(ruleChainBuildingService.createRuleConnection(index + 6, index + 7));
                connections.add(ruleChainBuildingService.createRuleConnection(index + 7, index + 8));
                connections.add(ruleChainBuildingService.createRuleConnection(index + 8, index + 9));
                connections.add(ruleChainBuildingService.createRuleConnection(index + 9, index + 10));
                connections.add(ruleChainBuildingService.createRuleConnection(index + 10, index + 11));
                connections.add(ruleChainBuildingService.createRuleConnection(index + 11, index + 12));
                connections.add(ruleChainBuildingService.createRuleConnection(index + 12, index + 13));
                connections.add(ruleChainBuildingService.createRuleConnection(index + 13, index + 14));

                ///
                connections.add(ruleChainBuildingService.createRuleConnection(index + 14, index + 15));
                connections.add(ruleChainBuildingService.createRuleConnection(index + 14, index + 16));
                connections.add(ruleChainBuildingService.createRuleConnection(index + 14, index + 18));
                connections.add(ruleChainBuildingService.createRuleConnection(index + 14, index + 20));
                connections.add(ruleChainBuildingService.createRuleConnection(index + 14, index + 22));
                connections.add(ruleChainBuildingService.createRuleConnection(index + 14, index + 25));

                connections.add(ruleChainBuildingService.createRuleConnection(index + 15, index + 24));

                connections.add(ruleChainBuildingService.createRuleConnection(index + 16, index + 17));
                connections.add(ruleChainBuildingService.createRuleConnection(index + 17, index + 24));

                connections.add(ruleChainBuildingService.createRuleConnection(index + 18, index + 19));
                connections.add(ruleChainBuildingService.createRuleConnection(index + 19, index + 24));

                connections.add(ruleChainBuildingService.createRuleConnection(index + 20, index + 21));
                connections.add(ruleChainBuildingService.createRuleConnection(index + 21, index + 24));

                connections.add(ruleChainBuildingService.createRuleConnection(index + 22, index + 23));
                connections.add(ruleChainBuildingService.createRuleConnection(index + 23, index + 24));

                ///
                connections.add(ruleChainBuildingService.createRuleConnection(index + 26, index + 27));
                connections.add(ruleChainBuildingService.createRuleConnection(index + 27, index + 28));

                connections.add(ruleChainBuildingService.createRuleConnection(index + 28, index + 29));
                connections.add(ruleChainBuildingService.createRuleConnection(index + 29, index + 32));

                connections.add(ruleChainBuildingService.createRuleConnection(index + 28, index + 30));
                connections.add(ruleChainBuildingService.createRuleConnection(index + 30, index + 31));
                connections.add(ruleChainBuildingService.createRuleConnection(index + 31, index + 32));

                int sectionCounter = 0;
                for (Section section : greenhouse.getSections()) {
                    SoilWarmMoistureSensor soilWarmMoistureSensor = section.getSoilWarmMoistureSensor();
                    SoilAciditySensor soilAciditySensor = section.getSoilAciditySensor();
                    SoilNpkSensor soilNpkSensor = section.getSoilNpkSensor();
                    HarvestReporter harvestReporter = section.getHarvestReporter();

                    List<RuleNode> sectionNodes = new ArrayList<>();

                    int sectionQuantity = greenhouse.getSections().size();
                    RuleNode sectionDelayNode = this.ruleChainBuildingService.createDelayNode(
                            String.format("%s: Finish - Delay Node", greenhouseName),
                            sectionCounter * (3600 - 20) / sectionQuantity,
                            getNodePositionX(greenhouseCounter, 2 + sectionCounter, 1),
                            getNodePositionY(greenhouseCounter, 2 + sectionCounter, 1)
                    );

                    RuleNode toSoilWarmMoistureSensorOriginatorNode = this.ruleChainBuildingService.createChangeOriginatorNode(
                            String.format("%s: To Soil Warm-Moisture Sensor", greenhouseName),
                            soilWarmMoistureSensor.getSystemName(),
                            EntityType.DEVICE,
                            getNodePositionX(greenhouseCounter, 2 + sectionCounter, 2),
                            getNodePositionY(greenhouseCounter, 2 + sectionCounter, 2)
                    );

                    RuleNode soilWarmMoistureSensorAttributesNode = this.ruleChainBuildingService.createOriginatorAttributesNode(
                            String.format("%s: Get Soil Warm-Moisture Attributes", greenhouseName),
                            Collections.emptyList(),
                            Collections.emptyList(),
                            Collections.emptyList(),
                            List.of("temperature", "moisture"),
                            false,
                            getNodePositionX(greenhouseCounter, 2 + sectionCounter, 3),
                            getNodePositionY(greenhouseCounter, 2 + sectionCounter, 3)
                    );

                    RuleNode toSoilAciditySensorOriginatorNode = this.ruleChainBuildingService.createChangeOriginatorNode(
                            String.format("%s: To Soil Acidity Sensor", greenhouseName),
                            soilAciditySensor.getSystemName(),
                            EntityType.DEVICE,
                            getNodePositionX(greenhouseCounter, 2 + sectionCounter, 4),
                            getNodePositionY(greenhouseCounter, 2 + sectionCounter, 4)
                    );

                    RuleNode soilAciditySensorAttributesNode = this.ruleChainBuildingService.createOriginatorAttributesNode(
                            String.format("%s: Get Soil Acidity Attributes", greenhouseName),
                            Collections.emptyList(),
                            Collections.emptyList(),
                            Collections.emptyList(),
                            List.of("acidity"),
                            false,
                            getNodePositionX(greenhouseCounter, 2 + sectionCounter, 5),
                            getNodePositionY(greenhouseCounter, 2 + sectionCounter, 5)
                    );

                    RuleNode toSoilNpkSensorOriginatorNode = this.ruleChainBuildingService.createChangeOriginatorNode(
                            String.format("%s: To Soil NPK Sensor", greenhouseName),
                            soilNpkSensor.getSystemName(),
                            EntityType.DEVICE,
                            getNodePositionX(greenhouseCounter, 2 + sectionCounter, 6),
                            getNodePositionY(greenhouseCounter, 2 + sectionCounter, 6)
                    );

                    RuleNode soilNpkSensorAttributesNode = this.ruleChainBuildingService.createOriginatorAttributesNode(
                            String.format("%s: Get Soil NPK Attributes", greenhouseName),
                            Collections.emptyList(),
                            Collections.emptyList(),
                            Collections.emptyList(),
                            List.of("nitrogen", "phosphorus", "potassium", "nitrogen_consumption", "phosphorus_consumption", "potassium_consumption"),
                            false,
                            getNodePositionX(greenhouseCounter, 2 + sectionCounter, 7),
                            getNodePositionY(greenhouseCounter, 2 + sectionCounter, 7)
                    );

                    String scriptSoilTemperatureMoisture = this.fileService.getFileContent(getSolutionName(), "soil_temperature_moisture.js");
                    RuleNode soilTemperatureMoistureTelemetryNode = this.ruleChainBuildingService.createTransformationNode(
                            String.format("%s: Map To Soil Warm-Moisture", greenhouseName),
                            scriptSoilTemperatureMoisture,
                            getNodePositionX(greenhouseCounter, 2 + sectionCounter, 8),
                            getNodePositionY(greenhouseCounter, 2 + sectionCounter, 8)
                    );

                    String scriptSoilAcidity = this.fileService.getFileContent(getSolutionName(), "soil_acidity.js");
                    RuleNode soilAcidityTelemetryNode = this.ruleChainBuildingService.createTransformationNode(
                            String.format("%s: Map To Soil Acidity", greenhouseName),
                            scriptSoilAcidity,
                            getNodePositionX(greenhouseCounter, 2 + sectionCounter, 9),
                            getNodePositionY(greenhouseCounter, 2 + sectionCounter, 9)
                    );

                    String scriptSoilNpk = this.fileService.getFileContent(getSolutionName(), "soil_npk.js");
                    RuleNode soilNpkTelemetryNode = this.ruleChainBuildingService.createTransformationNode(
                            String.format("%s: Map To Soil NPK", greenhouseName),
                            scriptSoilNpk,
                            getNodePositionX(greenhouseCounter, 2 + sectionCounter, 10),
                            getNodePositionY(greenhouseCounter, 2 + sectionCounter, 10)
                    );

                    String scriptHarvestReporter = this.fileService.getFileContent(getSolutionName(), "harvest_reporter.js");
                    scriptHarvestReporter = scriptHarvestReporter.replaceAll("\"WORKER_IN_CHARGE_PLACEHOLDER\"", greenhouse.getWorkersInCharge());
                    RuleNode harvestReporterTelemetryNode = this.ruleChainBuildingService.createTransformationNode(
                            String.format("%s: Map To Harvest Reporter In", greenhouseName),
                            scriptHarvestReporter,
                            getNodePositionX(greenhouseCounter, 2 + sectionCounter, 11),
                            getNodePositionY(greenhouseCounter, 2 + sectionCounter, 11)
                    );

                    ///

                    String scriptFinishSoilNpk = this.fileService.getFileContent(getSolutionName(), "finish_soil_npk.js");
                    RuleNode finishSoilNpkTelemetryNode = this.ruleChainBuildingService.createTransformationNode(
                            String.format("%s: Finish - Map Soil NPK", greenhouseName),
                            scriptFinishSoilNpk,
                            getNodePositionX(greenhouseCounter, 2 + sectionCounter, 12),
                            getNodePositionY(greenhouseCounter, 2 + sectionCounter, 12)
                    );


                    RuleNode finishSoilWarmMoistureSensorOriginatorNode = this.ruleChainBuildingService.createChangeOriginatorNode(
                            String.format("%s: Finish - To Soil Warm-Moisture", greenhouseName),
                            soilWarmMoistureSensor.getSystemName(),
                            EntityType.DEVICE,
                            getNodePositionX(greenhouseCounter, 2 + sectionCounter, 13),
                            getNodePositionY(greenhouseCounter, 2 + sectionCounter, 13)
                    );

                    String scriptFinishSoilWarmMoisture = this.fileService.getFileContent(getSolutionName(), "finish_soil_temperature_moisture.js");
                    RuleNode finishSoilWarmMoistureTelemetryNode = this.ruleChainBuildingService.createTransformationNode(
                            String.format("%s: Finish - Map To Soil Warm-Moisture", greenhouseName),
                            scriptFinishSoilWarmMoisture,
                            getNodePositionX(greenhouseCounter, 2 + sectionCounter, 14),
                            getNodePositionY(greenhouseCounter, 2 + sectionCounter, 14)
                    );


                    RuleNode finishSoilAciditySensorOriginatorNode = this.ruleChainBuildingService.createChangeOriginatorNode(
                            String.format("%s: Finish - To Soil Acidity Sensor", greenhouseName),
                            soilAciditySensor.getSystemName(),
                            EntityType.DEVICE,
                            getNodePositionX(greenhouseCounter, 2 + sectionCounter, 15),
                            getNodePositionY(greenhouseCounter, 2 + sectionCounter, 15)
                    );

                    String scriptFinishSoilAcidity = this.fileService.getFileContent(getSolutionName(), "finish_soil_acidity.js");
                    RuleNode finishSoilAcidityTelemetryNode = this.ruleChainBuildingService.createTransformationNode(
                            String.format("%s: Finish - Map To Soil Acidity", greenhouseName),
                            scriptFinishSoilAcidity,
                            getNodePositionX(greenhouseCounter, 2 + sectionCounter, 16),
                            getNodePositionY(greenhouseCounter, 2 + sectionCounter, 16)
                    );


                    RuleNode finishHarvestReporterSensorOriginatorNode = this.ruleChainBuildingService.createChangeOriginatorNode(
                            String.format("%s: Finish - To Harvest Reporter Sensor", greenhouseName),
                            harvestReporter.getSystemName(),
                            EntityType.DEVICE,
                            getNodePositionX(greenhouseCounter, 2 + sectionCounter, 17),
                            getNodePositionY(greenhouseCounter, 2 + sectionCounter, 17)
                    );

                    String scriptFinishHarvestReporter = this.fileService.getFileContent(getSolutionName(), "finish_harvest_reporter.js");
                    RuleNode finishHarvestReporterTelemetryNode = this.ruleChainBuildingService.createTransformationNode(
                            String.format("%s: Finish - Map To Harvest Reporter", greenhouseName),
                            scriptFinishHarvestReporter,
                            getNodePositionX(greenhouseCounter, 2 + sectionCounter, 18),
                            getNodePositionY(greenhouseCounter, 2 + sectionCounter, 18)
                    );


                    RuleNode sectionSaveNode = this.ruleChainBuildingService.createSaveNode(
                            String.format("%s: Save Telemetry (Section %s)", greenhouseName, sectionCounter + 1),
                            getNodePositionX(greenhouseCounter, 2 + sectionCounter, 19),
                            getNodePositionY(greenhouseCounter, 2 + sectionCounter, 19)
                    );

                    sectionNodes.add(sectionDelayNode);                             // 1
                    sectionNodes.add(toSoilWarmMoistureSensorOriginatorNode);       // 2
                    sectionNodes.add(soilWarmMoistureSensorAttributesNode);         // 3
                    sectionNodes.add(toSoilAciditySensorOriginatorNode);            // 4
                    sectionNodes.add(soilAciditySensorAttributesNode);              // 5
                    sectionNodes.add(toSoilNpkSensorOriginatorNode);                // 6
                    sectionNodes.add(soilNpkSensorAttributesNode);                  // 7
                    sectionNodes.add(soilTemperatureMoistureTelemetryNode);         // 8
                    sectionNodes.add(soilAcidityTelemetryNode);                     // 9
                    sectionNodes.add(soilNpkTelemetryNode);                         // 10
                    sectionNodes.add(harvestReporterTelemetryNode);                 // 11

                    sectionNodes.add(finishSoilNpkTelemetryNode);                   // 12

                    sectionNodes.add(finishSoilWarmMoistureSensorOriginatorNode);   // 13
                    sectionNodes.add(finishSoilWarmMoistureTelemetryNode);          // 14

                    sectionNodes.add(finishSoilAciditySensorOriginatorNode);        // 15
                    sectionNodes.add(finishSoilAcidityTelemetryNode);               // 16

                    sectionNodes.add(finishHarvestReporterSensorOriginatorNode);    // 17
                    sectionNodes.add(finishHarvestReporterTelemetryNode);           // 18

                    sectionNodes.add(sectionSaveNode);                              // 19
                    nodes.addAll(sectionNodes);

                    int sectionIndex = index + 32 + sectionCounter * sectionNodes.size();
                    connections.add(ruleChainBuildingService.createRuleConnection(index + 25, sectionIndex + 1));
                    connections.add(ruleChainBuildingService.createRuleConnection(sectionIndex + 1, sectionIndex + 2));
                    connections.add(ruleChainBuildingService.createRuleConnection(sectionIndex + 2, sectionIndex + 3));
                    connections.add(ruleChainBuildingService.createRuleConnection(sectionIndex + 3, sectionIndex + 4));
                    connections.add(ruleChainBuildingService.createRuleConnection(sectionIndex + 4, sectionIndex + 5));
                    connections.add(ruleChainBuildingService.createRuleConnection(sectionIndex + 5, sectionIndex + 6));
                    connections.add(ruleChainBuildingService.createRuleConnection(sectionIndex + 6, sectionIndex + 7));
                    connections.add(ruleChainBuildingService.createRuleConnection(sectionIndex + 7, sectionIndex + 8));
                    connections.add(ruleChainBuildingService.createRuleConnection(sectionIndex + 8, sectionIndex + 9));
                    connections.add(ruleChainBuildingService.createRuleConnection(sectionIndex + 9, sectionIndex + 10));
                    connections.add(ruleChainBuildingService.createRuleConnection(sectionIndex + 10, sectionIndex + 11));

                    connections.add(ruleChainBuildingService.createRuleConnection(sectionIndex + 11, index + 26));

                    connections.add(ruleChainBuildingService.createRuleConnection(sectionIndex + 11, sectionIndex + 12));
                    connections.add(ruleChainBuildingService.createRuleConnection(sectionIndex + 12, sectionIndex + 19));

                    connections.add(ruleChainBuildingService.createRuleConnection(sectionIndex + 11, sectionIndex + 13));
                    connections.add(ruleChainBuildingService.createRuleConnection(sectionIndex + 13, sectionIndex + 14));
                    connections.add(ruleChainBuildingService.createRuleConnection(sectionIndex + 14, sectionIndex + 19));

                    connections.add(ruleChainBuildingService.createRuleConnection(sectionIndex + 11, sectionIndex + 15));
                    connections.add(ruleChainBuildingService.createRuleConnection(sectionIndex + 15, sectionIndex + 16));
                    connections.add(ruleChainBuildingService.createRuleConnection(sectionIndex + 16, sectionIndex + 19));

                    connections.add(ruleChainBuildingService.createRuleConnection(sectionIndex + 11, sectionIndex + 17));
                    connections.add(ruleChainBuildingService.createRuleConnection(sectionIndex + 17, sectionIndex + 18));
                    connections.add(ruleChainBuildingService.createRuleConnection(sectionIndex + 18, sectionIndex + 19));
                    sectionCounter++;
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


    private ModelData makeData(boolean skipTelemetry, ZonedDateTime startYear, boolean fullTelemetryGeneration, long startGenerationTime, long endGenerationTime) {
        long startTs = DateTimeUtils.toTs(startYear);
        long now = System.currentTimeMillis();

        Map<StationCity, Map<Long, WeatherData>> weatherMap = Arrays.stream(StationCity.values())
                .map(city -> Pair.of(city, loadWeatherData(city, startYear, skipTelemetry, fullTelemetryGeneration, startGenerationTime, endGenerationTime)))
                .collect(Collectors.toMap(Pair::getKey, Pair::getValue));

        int order = 0;

        PlantConfiguration plantConfigurationTomatoSungold = createPlantTomatoSungold(order++);
        PlantConfiguration plantConfigurationTomatoCherry = createPlantTomatoCherry(order++);
        PlantConfiguration plantConfigurationCucumberEnglish = createPlantCucumberEnglish(order++);
        PlantConfiguration plantConfigurationOnionSweetSpanish = createPlantOnionSweetSpanish(order++);

        Set<PlantConfiguration> plantConfigurations = MySortedSet.of(
                plantConfigurationTomatoSungold,
                plantConfigurationTomatoCherry,
                plantConfigurationCucumberEnglish,
                plantConfigurationOnionSweetSpanish
        );


        GreenhouseConfiguration greenhouseConfigurationKyiv = makeKyivGreenhouse(startTs, now, plantConfigurationTomatoSungold, order++);
        GreenhouseConfiguration greenhouseConfigurationStuttgart = makeStuttgartGreenhouse(startTs, now, plantConfigurationTomatoCherry, order++);
        GreenhouseConfiguration greenhouseConfigurationKrakow = makeKrakowGreenhouse(startTs, now, plantConfigurationCucumberEnglish, order++);
        GreenhouseConfiguration greenhouseConfigurationWarszawa = makeWarszawaGreenhouse(startTs, now, plantConfigurationOnionSweetSpanish, order++);

        Set<GreenhouseConfiguration> greenhouseConfigurations = MySortedSet.of(
                greenhouseConfigurationKyiv,
                greenhouseConfigurationStuttgart,
                greenhouseConfigurationKrakow,
                greenhouseConfigurationWarszawa
        );


        Set<ModelEntity> entities = MySortedSet.of();
        entities.addAll(
                plantConfigurations
                        .stream()
                        .map(configuration -> {
                            Plant plant = makePlantByConfiguration(configuration);
                            this.configurationToPlantMap.put(configuration, plant);
                            return plant;
                        })
                        .collect(Collectors.toList())
        );
        entities.addAll(
                greenhouseConfigurations
                        .stream()
                        .map(configuration -> makeGreenhouseByConfiguration(
                                configuration, weatherMap.get(configuration.getStationCity()), skipTelemetry,
                                fullTelemetryGeneration, startGenerationTime, endGenerationTime
                        ))
                        .collect(Collectors.toList())
        );

        return ModelData.builder()
                .data(entities)
                .build();
    }

    private void applyData(ModelData data, CustomerData customerData, boolean strictGeneration) {
        CustomerUser customerUser = customerData.getUser();
        UUID ownerId = customerUser.getCustomerId().getId();

        UUID assetGroupId = null;
        UUID deviceGroupId = null;
        if (tbRestClient.isPe()) {
            EntityGroup assetGroup = strictGeneration
                    ? tbRestClient.createEntityGroup(ASSET_GROUP_NAME, EntityType.ASSET, ownerId, true)
                    : tbRestClient.createEntityGroupIfNotExists(ASSET_GROUP_NAME, EntityType.ASSET, ownerId, true);
            EntityGroup deviceGroup = strictGeneration
                    ? tbRestClient.createEntityGroup(DEVICE_GROUP_NAME, EntityType.DEVICE, ownerId, true)
                    : tbRestClient.createEntityGroupIfNotExists(DEVICE_GROUP_NAME, EntityType.DEVICE, ownerId, true);
            assetGroupId = assetGroup.getUuidId();
            deviceGroupId = deviceGroup.getUuidId();
        }

        Set<Plant> plants = mapToPlants(data);
        for (Plant plant : plants) {
            Asset plantAsset = createPlant(plant, ownerId, assetGroupId, strictGeneration);
        }

        Set<Greenhouse> greenhouses = mapToGreenhouses(data);
        for (Greenhouse greenhouse : greenhouses) {
            Asset greenhouseAsset = createGreenhouse(greenhouse, ownerId, assetGroupId, strictGeneration);

            for (Section section : greenhouse.getSections()) {
                Asset sectionAsset = createSection(section, ownerId, assetGroupId, strictGeneration);
                this.tbRestClient.createRelation(RelationType.CONTAINS.getType(), greenhouseAsset.getId(), sectionAsset.getId());

                Device soilWarmMoistureSensorDevice = createSoilWarmMoistureSensor(section.getSoilWarmMoistureSensor(), ownerId, deviceGroupId, strictGeneration);
                Device soilAciditySensorDevice = createSoilAciditySensor(section.getSoilAciditySensor(), ownerId, deviceGroupId, strictGeneration);
                Device soilNpkSensorDevice = createSoilNpkSensor(section.getSoilNpkSensor(), ownerId, deviceGroupId, strictGeneration);
                Device harvestReporterDevice = createHarvestReporter(section.getHarvestReporter(), ownerId, deviceGroupId, strictGeneration);

                this.tbRestClient.createRelation(RelationType.CONTAINS.getType(), sectionAsset.getId(), soilWarmMoistureSensorDevice.getId());
                this.tbRestClient.createRelation(RelationType.CONTAINS.getType(), sectionAsset.getId(), soilAciditySensorDevice.getId());
                this.tbRestClient.createRelation(RelationType.CONTAINS.getType(), sectionAsset.getId(), soilNpkSensorDevice.getId());
                this.tbRestClient.createRelation(RelationType.CONTAINS.getType(), sectionAsset.getId(), harvestReporterDevice.getId());
            }

            Device insideAirWarmHumiditySensorDevice = createInsideAirWarmHumiditySensor(greenhouse.getInsideAirWarmHumiditySensor(), ownerId, deviceGroupId, strictGeneration);
            Device insideLightSensorDevice = createInsideLightSensor(greenhouse.getInsideLightSensor(), ownerId, deviceGroupId, strictGeneration);
            Device insideCO2SensorDevice = createInsideCO2Sensor(greenhouse.getInsideCO2Sensor(), ownerId, deviceGroupId, strictGeneration);
            Device outsideAirWarmHumiditySensorDevice = createOutsideAirWarmHumiditySensor(greenhouse.getOutsideAirWarmHumiditySensor(), ownerId, deviceGroupId, strictGeneration);
            Device outsideLightSensorDevice = createOutsideLightSensor(greenhouse.getOutsideLightSensor(), ownerId, deviceGroupId, strictGeneration);
            Device energyMeter = createEnergyMeter(greenhouse.getEnergyMeter(), ownerId, deviceGroupId, strictGeneration);
            Device waterMeter = createWaterMeter(greenhouse.getWaterMeter(), ownerId, deviceGroupId, strictGeneration);

            this.tbRestClient.createRelation(RelationType.CONTAINS.getType(), greenhouseAsset.getId(), insideAirWarmHumiditySensorDevice.getId());
            this.tbRestClient.createRelation(RelationType.CONTAINS.getType(), greenhouseAsset.getId(), insideLightSensorDevice.getId());
            this.tbRestClient.createRelation(RelationType.CONTAINS.getType(), greenhouseAsset.getId(), insideCO2SensorDevice.getId());
            this.tbRestClient.createRelation(RelationType.CONTAINS.getType(), greenhouseAsset.getId(), outsideAirWarmHumiditySensorDevice.getId());
            this.tbRestClient.createRelation(RelationType.CONTAINS.getType(), greenhouseAsset.getId(), outsideLightSensorDevice.getId());
            this.tbRestClient.createRelation(RelationType.CONTAINS.getType(), greenhouseAsset.getId(), energyMeter.getId());
            this.tbRestClient.createRelation(RelationType.CONTAINS.getType(), greenhouseAsset.getId(), waterMeter.getId());

            UUID plantUuid = this.plantToIdMap.get(greenhouse.getPlant());
            this.tbRestClient.createRelation(RelationType.CONTAINS.getType(), new AssetId(plantUuid), greenhouseAsset.getId());
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


    private PlantConfiguration createPlantTomatoSungold(int order) {
        return PlantConfiguration.builder()
                .order(order)
                .name(PlantName.TOMATO)
                .variety("Sungold")
                .dayMinTemperature(21)
                .dayMaxTemperature(29)
                .nightMinTemperature(15)
                .nightMaxTemperature(21)
                .dayMinLight(8000)
                .dayMaxLight(10000)
                .nightMinLight(0)
                .nightMaxLight(20)
                .minAirHumidity(40)
                .maxAirHumidity(70)
                .minSoilMoisture(40)
                .maxSoilMoisture(70)
                .minSoilTemperature(15)
                .maxSoilTemperature(25)
                .minCo2Concentration(350)
                .maxCo2Concentration(1000)
                .minPh(5.5)
                .maxPh(7.5)
                .minRipeningPeriodDays(90)
                .maxRipeningPeriodDays(110)
                .minNitrogenLevel(150)
                .maxNitrogenLevel(250)
                .minPhosphorusLevel(30)
                .maxPhosphorusLevel(60)
                .minPotassiumLevel(250)
                .maxPotassiumLevel(450)
                .growthPeriodsDayList(List.of(30, 60, 100))
                .growthPeriodsNitrogenConsumption(List.of(150.0, 230.0, 300.0))
                .growthPeriodsPhosphorusConsumption(List.of(8.0, 12.0, 22.0))
                .growthPeriodsPotassiumConsumption(List.of(80.0, 200.0, 350.0))
                .averageCropWeight(15)
                .build();
    }

    private PlantConfiguration createPlantTomatoCherry(int order) {
        return PlantConfiguration.builder()
                .order(order)
                .name(PlantName.TOMATO)
                .variety("Cherry")
                .dayMinTemperature(21)
                .dayMaxTemperature(29)
                .nightMinTemperature(15)
                .nightMaxTemperature(21)
                .dayMinLight(7000)
                .dayMaxLight(8000)
                .nightMinLight(1000)
                .nightMaxLight(2000)
                .minAirHumidity(50)
                .maxAirHumidity(70)
                .minSoilMoisture(40)
                .maxSoilMoisture(70)
                .minSoilTemperature(15)
                .maxSoilTemperature(30)
                .minCo2Concentration(350)
                .maxCo2Concentration(1000)
                .minPh(5.5)
                .maxPh(6.8)
                .minRipeningPeriodDays(65)
                .maxRipeningPeriodDays(75)
                .minNitrogenLevel(150)
                .maxNitrogenLevel(250)
                .minPhosphorusLevel(70)
                .maxPhosphorusLevel(120)
                .minPotassiumLevel(200)
                .maxPotassiumLevel(300)
                .growthPeriodsDayList(List.of(30, 50, 70))
                .growthPeriodsNitrogenConsumption(List.of(150.0, 200.0, 260.0))
                .growthPeriodsPhosphorusConsumption(List.of(5.0, 15.0, 23.0))
                .growthPeriodsPotassiumConsumption(List.of(90.0, 250.0, 340.0))
                .averageCropWeight(8)
                .build();
    }

    private PlantConfiguration createPlantCucumberEnglish(int order) {
        return PlantConfiguration.builder()
                .order(order)
                .name(PlantName.CUCUMBER)
                .variety("English")
                .dayMinTemperature(22)
                .dayMaxTemperature(27)
                .nightMinTemperature(16)
                .nightMaxTemperature(21)
                .dayMinLight(12000)
                .dayMaxLight(15000)
                .nightMinLight(1000)
                .nightMaxLight(1500)
                .minAirHumidity(60)
                .maxAirHumidity(70)
                .minSoilMoisture(60)
                .maxSoilMoisture(80)
                .minSoilTemperature(10)
                .maxSoilTemperature(30)
                .minCo2Concentration(300)
                .maxCo2Concentration(1000)
                .minPh(6.0)
                .maxPh(7.0)
                .minRipeningPeriodDays(55)
                .maxRipeningPeriodDays(65)
                .minNitrogenLevel(100)
                .maxNitrogenLevel(200)
                .minPhosphorusLevel(20)
                .maxPhosphorusLevel(50)
                .minPotassiumLevel(200)
                .maxPotassiumLevel(400)
                .growthPeriodsDayList(List.of(15, 30, 45, 60))
                .growthPeriodsNitrogenConsumption(List.of(100.0, 220.0, 300.0, 360.0))
                .growthPeriodsPhosphorusConsumption(List.of(3.0, 8.0, 20.0, 28.0))
                .growthPeriodsPotassiumConsumption(List.of(80.0, 120.0, 250.0, 380.0))
                .averageCropWeight(10)
                .build();
    }

    private PlantConfiguration createPlantOnionSweetSpanish(int order) {
        return PlantConfiguration.builder()
                .order(order)
                .name(PlantName.ONION)
                .variety("Sweet Spanish")
                .dayMinTemperature(21)
                .dayMaxTemperature(29)
                .nightMinTemperature(13)
                .nightMaxTemperature(21)
                .dayMinLight(10000)
                .dayMaxLight(12000)
                .nightMinLight(0)
                .nightMaxLight(2)
                .minAirHumidity(50)
                .maxAirHumidity(70)
                .minSoilMoisture(60)
                .maxSoilMoisture(80)
                .minSoilTemperature(10)
                .maxSoilTemperature(30)
                .minCo2Concentration(300)
                .maxCo2Concentration(1000)
                .minPh(6.0)
                .maxPh(7.0)
                .minRipeningPeriodDays(80)
                .maxRipeningPeriodDays(100)
                .minNitrogenLevel(150)
                .maxNitrogenLevel(250)
                .minPhosphorusLevel(35)
                .maxPhosphorusLevel(70)
                .minPotassiumLevel(200)
                .maxPotassiumLevel(400)
                .growthPeriodsDayList(List.of(15, 30, 60, 90))
                .growthPeriodsNitrogenConsumption(List.of(75.0, 190.0, 309.0, 400.0))
                .growthPeriodsPhosphorusConsumption(List.of(4.0, 10.0, 25.0, 33.0))
                .growthPeriodsPotassiumConsumption(List.of(39.0, 59.0, 158.0, 360.0))
                .averageCropWeight(5)
                .build();
    }


    private GreenhouseConfiguration makeKyivGreenhouse(long startTs, long endTs, PlantConfiguration plantConfiguration, int order) {
        return GreenhouseConfiguration.builder()
                .order(order)
                .startTs(startTs)
                .endTs(endTs)
                .name("Kyiv")
                .stationCity(StationCity.KYIV)
                .address("Svyatoshyns'ka St, 34 к, Kyiv, 02000")
                .latitude(50.446603)
                .longitude(30.386447)
                .plantConfiguration(plantConfiguration)
                .sectionHeight(5)
                .sectionWidth(7)
                .sectionArea(3)
                .workersInCharge(List.of(
                        WorkerInChargeName.IGOR_PETROVICH,
                        WorkerInChargeName.PETRO_VYNNYCHENKO,
                        WorkerInChargeName.KYRYLLO_BONDARENKO
                ))
                .build();
    }

    private GreenhouseConfiguration makeStuttgartGreenhouse(long startTs, long endTs, PlantConfiguration plantConfiguration, int order) {
        return GreenhouseConfiguration.builder()
                .order(order)
                .startTs(startTs)
                .endTs(endTs)
                .name("Stuttgart")
                .stationCity(StationCity.STUTTGART)
                .address("Augsburger Str. 500, 70327 Stuttgart, Germany")
                .latitude(48.774252)
                .longitude(9.259500)
                .plantConfiguration(plantConfiguration)
                .sectionHeight(3)
                .sectionWidth(5)
                .sectionArea(3)
                .workersInCharge(List.of(
                        WorkerInChargeName.DANIEL_KRUGGER,
                        WorkerInChargeName.MARK_ZELTER,
                        WorkerInChargeName.LUIS_WITT
                ))
                .build();
    }

    private GreenhouseConfiguration makeKrakowGreenhouse(long startTs, long endTs, PlantConfiguration plantConfiguration, int order) {
        return GreenhouseConfiguration.builder()
                .order(order)
                .startTs(startTs)
                .endTs(endTs)
                .name("Krakow")
                .stationCity(StationCity.KRAKOW)
                .address("Zielona 18, 32-087 Bibice, Poland")
                .latitude(50.121765)
                .longitude(19.946134)
                .plantConfiguration(plantConfiguration)
                .sectionHeight(3)
                .sectionWidth(4)
                .sectionArea(5)
                .workersInCharge(List.of(
                        WorkerInChargeName.ANJEY_MANISKII,
                        WorkerInChargeName.LECH_PAWLOWSKI,
                        WorkerInChargeName.BOGUSLAW_VISHNEVSKII
                ))
                .build();
    }

    private GreenhouseConfiguration makeWarszawaGreenhouse(long startTs, long endTs, PlantConfiguration plantConfiguration, int order) {
        return GreenhouseConfiguration.builder()
                .order(order)
                .startTs(startTs)
                .endTs(endTs)
                .name("Warszawa")
                .stationCity(StationCity.WARSZAWA)
                .address("Ojca Aniceta 28, 03-264 Warszawa, Poland")
                .latitude(52.306237)
                .longitude(21.039917)
                .plantConfiguration(plantConfiguration)
                .sectionHeight(2)
                .sectionWidth(6)
                .sectionArea(4)
                .workersInCharge(List.of(
                        WorkerInChargeName.MIROSLAW_MORACHEVSKII,
                        WorkerInChargeName.ZIEMOWIT_YANKOVSKII,
                        WorkerInChargeName.WOJCIECH_DUNAEVSKII
                ))
                .build();
    }


    private Plant makePlantByConfiguration(PlantConfiguration configuration) {
        return Plant.builder()
                .systemName(configuration.getName() + " - " + configuration.getVariety())
                .systemLabel("")
                .name(configuration.getName().toString())
                .variety(configuration.getVariety())
                .dayMinTemperature(configuration.getDayMinTemperature())
                .dayMaxTemperature(configuration.getDayMaxTemperature())
                .nightMinTemperature(configuration.getNightMinTemperature())
                .nightMaxTemperature(configuration.getNightMaxTemperature())
                .dayMinLight(configuration.getDayMinLight())
                .dayMaxLight(configuration.getDayMaxLight())
                .nightMinLight(configuration.getNightMinLight())
                .nightMaxLight(configuration.getNightMaxLight())
                .minAirHumidity(configuration.getMinAirHumidity())
                .maxAirHumidity(configuration.getMaxAirHumidity())
                .minSoilMoisture(configuration.getMinSoilMoisture())
                .maxSoilMoisture(configuration.getMaxSoilMoisture())
                .minSoilTemperature(configuration.getMinSoilTemperature())
                .maxSoilTemperature(configuration.getMaxSoilTemperature())
                .minCo2Concentration(configuration.getMinCo2Concentration())
                .maxCo2Concentration(configuration.getMaxCo2Concentration())
                .minPh(configuration.getMinPh())
                .maxPh(configuration.getMaxPh())
                .minRipeningPeriodDays(configuration.getMinRipeningPeriodDays())
                .maxRipeningPeriodDays(configuration.getMaxRipeningPeriodDays())
                .minNitrogenLevel(configuration.getMinNitrogenLevel())
                .maxNitrogenLevel(configuration.getMaxNitrogenLevel())
                .minPhosphorusLevel(configuration.getMinPhosphorusLevel())
                .maxPhosphorusLevel(configuration.getMaxPhosphorusLevel())
                .minPotassiumLevel(configuration.getMinPotassiumLevel())
                .maxPotassiumLevel(configuration.getMaxPotassiumLevel())
                .averageCropWeight(configuration.getAverageCropWeight())
                .growthPeriodsDayList(JsonUtils.toJson(configuration.getGrowthPeriodsDayList()))
                .growthPeriodsNitrogenConsumption(JsonUtils.toJson(configuration.getGrowthPeriodsNitrogenConsumption()))
                .growthPeriodsPhosphorusConsumption(JsonUtils.toJson(configuration.getGrowthPeriodsPhosphorusConsumption()))
                .growthPeriodsPotassiumConsumption(JsonUtils.toJson(configuration.getGrowthPeriodsPotassiumConsumption()))
                .build();
    }

    private Greenhouse makeGreenhouseByConfiguration(GreenhouseConfiguration configuration, Map<Long, WeatherData> weatherDataMap, boolean skipTelemetry, boolean fullTelemetryGeneration, long startGenerationTime, long endGenerationTime) {

        Telemetry<Integer> outsideLightTelemetry = createOutsideLightTelemetry(weatherDataMap, configuration, skipTelemetry, fullTelemetryGeneration, startGenerationTime, endGenerationTime);
        Telemetry<Integer> outsideTemperatureTelemetry = createOutsideTemperatureTelemetry(weatherDataMap, configuration, skipTelemetry, fullTelemetryGeneration, startGenerationTime, endGenerationTime);
        Telemetry<Integer> outsideHumidityTelemetry = createOutsideHumidityTelemetry(weatherDataMap, configuration, skipTelemetry, fullTelemetryGeneration, startGenerationTime, endGenerationTime);

        Telemetry<Integer> insideLightTelemetry = createInsideLightTelemetry(outsideLightTelemetry, configuration, skipTelemetry, fullTelemetryGeneration, startGenerationTime, endGenerationTime);

        Set<Long> aerations = new HashSet<>();
        Telemetry<Integer> temporalTelemetryCo2Generation = createTemporalTelemetryCo2Generation(
                outsideLightTelemetry, insideLightTelemetry, configuration, skipTelemetry, fullTelemetryGeneration, startGenerationTime, endGenerationTime);
        Telemetry<Integer> co2ConcetracionTelemetry = createCo2ConcentrationTelemetryAndInterruption(
                aerations, temporalTelemetryCo2Generation, configuration, skipTelemetry, fullTelemetryGeneration, startGenerationTime, endGenerationTime);

        Set<Long> heatings = new HashSet<>();
        Set<Long> coolings = new HashSet<>();
        Set<Long> humidifications = new HashSet<>();
        Set<Long> dehumidifications = new HashSet<>();
        Telemetry<Integer> insideTemperatureTelemetry = createInsideTemperatureTelemetry(
                aerations, heatings, coolings, outsideTemperatureTelemetry, configuration, skipTelemetry, fullTelemetryGeneration, startGenerationTime, endGenerationTime);
        Telemetry<Integer> insideHumidityTelemetry = createInsideHumidityTelemetry(
                aerations, heatings, coolings, humidifications, dehumidifications, outsideHumidityTelemetry, configuration, skipTelemetry, fullTelemetryGeneration, startGenerationTime, endGenerationTime);

        Map<String, Set<Long>> irrigations = new HashMap<>();

        Set<Section> sections = new TreeSet<>();
        for (int height = 1; height <= configuration.getSectionHeight(); height++) {
            for (int width = 1; width <= configuration.getSectionWidth(); width++) {

                String sectionName = String.format(configuration.getName() + ", section %s-%s", height, width);
                Set<Long> sectionIrrigations = irrigations.computeIfAbsent(sectionName, key -> new HashSet<>());

                Telemetry<Double> temporalTelemetrySoilWaterConsumption = createTemporalTelemetrySoilWaterConsumption(
                        configuration, skipTelemetry, fullTelemetryGeneration, startGenerationTime, endGenerationTime);
                Telemetry<Double> soilMoisture = createTelemetrySoilMoisture(
                        configuration, temporalTelemetrySoilWaterConsumption, sectionIrrigations, skipTelemetry, fullTelemetryGeneration, startGenerationTime, endGenerationTime);

                Telemetry<Integer> telemetrySoilTemperature = createTelemetrySoilTemperature(
                        configuration, insideTemperatureTelemetry, sectionIrrigations, skipTelemetry, fullTelemetryGeneration, startGenerationTime, endGenerationTime);

                Set<Long> acidification = new HashSet<>();
                Telemetry<Double> telemetrySoilAcidity = createTelemetrySoilAcidity(
                        configuration, sectionIrrigations, acidification, skipTelemetry, fullTelemetryGeneration, startGenerationTime, endGenerationTime);

                Telemetry<Double> nitrogenLevelTelemetry = createTelemetrySoilNitrogenLevel(
                        configuration, skipTelemetry, fullTelemetryGeneration, startGenerationTime, endGenerationTime);
                Telemetry<Double> phosphorusLevelTelemetry = createTelemetrySoilPhosphorusLevel(
                        configuration, skipTelemetry, fullTelemetryGeneration, startGenerationTime, endGenerationTime);
                Telemetry<Double> potassiumLevelTelemetry = createTelemetrySoilPotassiumLevel(
                        configuration, skipTelemetry, fullTelemetryGeneration, startGenerationTime, endGenerationTime);

                Telemetry<Double> telemetryCropWeight = new Telemetry<>("cropWeight");
                Telemetry<String> telemetryWorkerInCharge = new Telemetry<>("workerInCharge");
                createTelemetryHarvestReporter(
                        telemetryCropWeight, telemetryWorkerInCharge, configuration, skipTelemetry, fullTelemetryGeneration, startGenerationTime, endGenerationTime);


                SoilNpkSensor soilNpkSensor = SoilNpkSensor.builder()
                        .systemName("Soil NPK Sensor: " + configuration.getName() + ", " + String.format("%s-%s", height, width))
                        .systemLabel("")
                        .fromGreenhouse(configuration.getName())
                        .nitrogen(nitrogenLevelTelemetry)
                        .phosphorus(phosphorusLevelTelemetry)
                        .potassium(potassiumLevelTelemetry)
                        .build();

                SoilWarmMoistureSensor soilWarmMoistureSensor = SoilWarmMoistureSensor.builder()
                        .systemName("Soil Warm-Moisture Sensor: " + configuration.getName() + ", " + String.format("%s-%s", height, width))
                        .systemLabel("")
                        .fromGreenhouse(configuration.getName())
                        .temperature(telemetrySoilTemperature)
                        .moisture(soilMoisture)
                        .build();

                SoilAciditySensor soilAciditySensor = SoilAciditySensor.builder()
                        .systemName("Soil Acidity: " + configuration.getName() + ", " + String.format("%s-%s", height, width))
                        .systemLabel("")
                        .fromGreenhouse(configuration.getName())
                        .acidity(telemetrySoilAcidity)
                        .build();

                HarvestReporter harvestReporter = HarvestReporter.builder()
                        .systemName("Harvester: " + configuration.getName() + ", " + String.format("%s-%s", height, width))
                        .systemLabel("")
                        .fromGreenhouse(configuration.getName())
                        .cropWeight(telemetryCropWeight)
                        .workerInCharge(telemetryWorkerInCharge)
                        .build();

                Section section = Section.builder()
                        .systemName(sectionName)
                        .systemLabel("")
                        .fromGreenhouse(configuration.getName())
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

        Telemetry<Double> energyConsumptionLight = new Telemetry<>("energyConsumptionLight");
        Telemetry<Double> energyConsumptionHeating = new Telemetry<>("energyConsumptionHeating");
        Telemetry<Double> energyConsumptionCooling = new Telemetry<>("energyConsumptionCooling");
        Telemetry<Double> energyConsumptionAirControl = new Telemetry<>("energyConsumptionAirControl");
        Telemetry<Double> energyConsumptionIrrigation = new Telemetry<>("energyConsumptionIrrigation");
        createTelemetryConsumptionEnergy(
                insideLightTelemetry, aerations, heatings, coolings, humidifications, dehumidifications, irrigations, configuration, skipTelemetry, fullTelemetryGeneration,
                startGenerationTime, endGenerationTime, energyConsumptionLight, energyConsumptionHeating, energyConsumptionCooling, energyConsumptionAirControl,
                energyConsumptionIrrigation
        );
        Telemetry<Double> telemetryConsumptionWater = createTelemetryConsumptionWater(
                humidifications, irrigations, configuration, skipTelemetry, fullTelemetryGeneration, startGenerationTime, endGenerationTime
        );

        OutsideAirWarmHumiditySensor outsideAirWarmHumiditySensor = OutsideAirWarmHumiditySensor.builder()
                .systemName(configuration.getName() + ": Air Warm-Humidity Sensor (Outside)")
                .systemLabel("")
                .fromGreenhouse(configuration.getName())
                .temperatureOut(outsideTemperatureTelemetry)
                .humidityOut(outsideHumidityTelemetry)
                .build();

        OutsideLightSensor outsideLightSensor = OutsideLightSensor.builder()
                .systemName(configuration.getName() + ": Light Sensor (Outside)")
                .systemLabel("")
                .fromGreenhouse(configuration.getName())
                .lightOut(outsideLightTelemetry)
                .build();

        InsideAirWarmHumiditySensor insideAirWarmHumiditySensor = InsideAirWarmHumiditySensor.builder()
                .systemName(configuration.getName() + ": Air Warm-Humidity Sensor (Inside)")
                .systemLabel("")
                .fromGreenhouse(configuration.getName())
                .temperatureIn(insideTemperatureTelemetry)
                .humidityIn(insideHumidityTelemetry)
                .build();

        InsideLightSensor insideLightSensor = InsideLightSensor.builder()
                .systemName(configuration.getName() + ": Light Sensor (Inside)")
                .systemLabel("")
                .fromGreenhouse(configuration.getName())
                .lightIn(insideLightTelemetry)
                .build();

        InsideCO2Sensor insideCO2Sensor = InsideCO2Sensor.builder()
                .systemName(configuration.getName() + ": CO2 Sensor (Inside)")
                .systemLabel("")
                .fromGreenhouse(configuration.getName())
                .concentration(co2ConcetracionTelemetry)
                .build();

        EnergyMeter energyMeter = EnergyMeter.builder()
                .systemName(configuration.getName() + ": Energy Meter")
                .systemLabel("")
                .fromGreenhouse(configuration.getName())
                .energyConsumptionLight(energyConsumptionLight)
                .energyConsumptionHeating(energyConsumptionHeating)
                .energyConsumptionCooling(energyConsumptionCooling)
                .energyConsumptionAirControl(energyConsumptionAirControl)
                .energyConsumptionIrrigation(energyConsumptionIrrigation)
                .build();

        WaterMeter waterMeter = WaterMeter.builder()
                .systemName(configuration.getName() + ": Water Meter")
                .systemLabel("")
                .fromGreenhouse(configuration.getName())
                .consumptionWater(telemetryConsumptionWater)
                .build();

        Plant plant = this.configurationToPlantMap.get(configuration.getPlantConfiguration());
        String workersInCharge = JsonUtils.toJson(configuration.getWorkersInCharge());
        return Greenhouse.builder()
                .systemName(configuration.getName())
                .systemLabel("")
                .plant(plant)
                .address(configuration.getAddress())
                .latitude(configuration.getLatitude())
                .longitude(configuration.getLongitude())
                .sections(sections)
                .insideAirWarmHumiditySensor(insideAirWarmHumiditySensor)
                .insideLightSensor(insideLightSensor)
                .insideCO2Sensor(insideCO2Sensor)
                .outsideAirWarmHumiditySensor(outsideAirWarmHumiditySensor)
                .outsideLightSensor(outsideLightSensor)
                .energyMeter(energyMeter)
                .waterMeter(waterMeter)
                .workersInCharge(workersInCharge)
                .build();
    }


    private Map<Long, WeatherData> loadWeatherData(StationCity city, ZonedDateTime startYear, boolean skipTelemetry, boolean fullTelemetryGeneration,
                                                   long startGenerationTime, long endGenerationTime) {
        Map<Long, WeatherData> noWeatherData = new HashMap<>();

        if (skipTelemetry) {
            return noWeatherData;
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

            Pair<Long, Long> fromToPair;
            try {
                fromToPair = calculateNewDateRange(DateTimeUtils.toTs(startYear), System.currentTimeMillis(), startGenerationTime, endGenerationTime, fullTelemetryGeneration);
            } catch (IllegalStateException e) {
                return noWeatherData;
            }

            ZonedDateTime startDate = DateTimeUtils.fromTs(fromToPair.getLeft()).truncatedTo(ChronoUnit.HOURS);
            ZonedDateTime nowDate = DateTimeUtils.fromTs(fromToPair.getRight()).truncatedTo(ChronoUnit.HOURS);
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


    private Telemetry<Integer> createOutsideLightTelemetry(Map<Long, WeatherData> tsToWeatherMap, GreenhouseConfiguration configuration, boolean skipTelemetry,
                                                           boolean fullTelemetryGeneration, long startGenerationTime, long endGenerationTime) {
        if (skipTelemetry) {
            return new Telemetry<>("skip");
        }

        Telemetry<Integer> result = new Telemetry<>("light_out");

        Pair<Long, Long> fromToPair;
        try {
            fromToPair = calculateNewDateRange(configuration.getStartTs(), configuration.getEndTs(), startGenerationTime, endGenerationTime, fullTelemetryGeneration);
        } catch (IllegalStateException e) {
            return new Telemetry<>("skip");
        }

        ZonedDateTime startDate = DateTimeUtils.fromTs(fromToPair.getLeft()).truncatedTo(ChronoUnit.HOURS);
        ZonedDateTime endDate = DateTimeUtils.fromTs(fromToPair.getRight()).truncatedTo(ChronoUnit.HOURS);
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
            int noise = (int) RandomUtils.getRandomNumber(-1000, 1000);
            int value = (int) ((hourLux + yearLux) * percents + noise);
            value = Math.max(0, value);

            result.add(iteratedTs, value);
            iteratedDate = iteratedDate.plus(1, ChronoUnit.HOURS);
        }

        return result;
    }

    private Telemetry<Integer> createOutsideTemperatureTelemetry(Map<Long, WeatherData> tsToWeatherMap, GreenhouseConfiguration configuration, boolean skipTelemetry,
                                                                 boolean fullTelemetryGeneration, long startGenerationTime, long endGenerationTime) {
        if (skipTelemetry) {
            return new Telemetry<>("skip");
        }

        Telemetry<Integer> result = new Telemetry<>("temperature_out");

        Pair<Long, Long> fromToPair;
        try {
            fromToPair = calculateNewDateRange(configuration.getStartTs(), configuration.getEndTs(), startGenerationTime, endGenerationTime, fullTelemetryGeneration);
        } catch (IllegalStateException e) {
            return new Telemetry<>("skip");
        }

        ZonedDateTime startDate = DateTimeUtils.fromTs(fromToPair.getLeft()).truncatedTo(ChronoUnit.HOURS);
        ZonedDateTime endDate = DateTimeUtils.fromTs(fromToPair.getRight()).truncatedTo(ChronoUnit.HOURS);
        ZonedDateTime iteratedDate = startDate;
        while (iteratedDate.isBefore(endDate)) {
            long iteratedTs = DateTimeUtils.toTs(iteratedDate);
            WeatherData weatherData = tsToWeatherMap.get(iteratedTs);

            double temperature = weatherData.getTemperatureCelsius();
            double humidity = weatherData.getHumidity();
            double value = temperature * (1 + humidity * 0.01) * 0.5;

            result.add(iteratedTs, (int) value);
            iteratedDate = iteratedDate.plus(1, ChronoUnit.HOURS);
        }

        return result;
    }

    private Telemetry<Integer> createOutsideHumidityTelemetry(Map<Long, WeatherData> tsToWeatherMap, GreenhouseConfiguration configuration, boolean skipTelemetry, boolean fullTelemetryGeneration,
                                                              long startGenerationTime, long endGenerationTime) {
        if (skipTelemetry) {
            return new Telemetry<>("skip");
        }

        Telemetry<Integer> result = new Telemetry<>("humidity_out");

        Pair<Long, Long> fromToPair;
        try {
            fromToPair = calculateNewDateRange(configuration.getStartTs(), configuration.getEndTs(), startGenerationTime, endGenerationTime, fullTelemetryGeneration);
        } catch (IllegalStateException e) {
            return new Telemetry<>("skip");
        }

        ZonedDateTime startDate = DateTimeUtils.fromTs(fromToPair.getLeft()).truncatedTo(ChronoUnit.HOURS);
        ZonedDateTime endDate = DateTimeUtils.fromTs(fromToPair.getRight()).truncatedTo(ChronoUnit.HOURS);
        ZonedDateTime iteratedDate = startDate;
        while (iteratedDate.isBefore(endDate)) {
            long iteratedTs = DateTimeUtils.toTs(iteratedDate);
            WeatherData weatherData = tsToWeatherMap.get(iteratedTs);

            result.add(iteratedTs, (int) weatherData.getHumidity());
            iteratedDate = iteratedDate.plus(1, ChronoUnit.HOURS);
        }

        return result;
    }

    private int getHourLuxValues(int hour) {
        /// As summer day [0-17_000]
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
        } else {
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
            case "Heavy Snow with Thunder":
            case "Heavy Rain":
            case "Heavy Rain / Windy":
            case "Heavy Rain Shower":
            case "Heavy Rain Shower / Windy":
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

            case "Rain and Snow":
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
            case "Drizzle and Fog":
            case "Freezing Rain":
            case "Freezing Rain / Windy":
            case "Snow Grains":
            case "Snow Grains / Windy":
            case "Thunder in the Vicinity":
            case "Showers in the Vicinity":
            case "Mist":
            case "Mist / Windy":
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
            case "":
                return 1.0;
            default:
                throw new IllegalArgumentException("Unsupported condition: " + condition);
        }
    }


    private Telemetry<Integer> createInsideLightTelemetry(Telemetry<Integer> outsideLightTelemetry, GreenhouseConfiguration configuration, boolean skipTelemetry, boolean fullTelemetryGeneration,
                                                          long startGenerationTime, long endGenerationTime) {
        if (skipTelemetry) {
            return new Telemetry<>("skip");
        }
        Telemetry<Integer> result = new Telemetry<>("light_in");

        Map<Timestamp, Telemetry.Point<Integer>> outsideLightTelemetryMap = outsideLightTelemetry.getPoints()
                .stream()
                .collect(Collectors.toMap(Telemetry.Point::getTs, Functions.identity()));

        double dayMinLevel = configuration.getPlantConfiguration().getDayMinLight();
        double dayMaxLevel = configuration.getPlantConfiguration().getDayMaxLight();
        double nightMinLevel = configuration.getPlantConfiguration().getNightMinLight();
        double nightMaxLevel = configuration.getPlantConfiguration().getNightMaxLight();
        double dayLevel = (dayMinLevel + dayMaxLevel) / 2;
        double nightLevel = (nightMinLevel + nightMaxLevel) / 2;

        Pair<Long, Long> fromToPair;
        try {
            fromToPair = calculateNewDateRange(configuration.getStartTs(), configuration.getEndTs(), startGenerationTime, endGenerationTime, fullTelemetryGeneration);
        } catch (IllegalStateException e) {
            return new Telemetry<>("skip");
        }

        ZonedDateTime startDate = DateTimeUtils.fromTs(fromToPair.getLeft()).truncatedTo(ChronoUnit.HOURS);
        ZonedDateTime endDate = DateTimeUtils.fromTs(fromToPair.getRight()).truncatedTo(ChronoUnit.HOURS);
        ZonedDateTime iteratedDate = startDate;
        while (iteratedDate.isBefore(endDate)) {
            long iteratedTs = DateTimeUtils.toTs(iteratedDate);
            int hour = iteratedDate.getHour();

            Telemetry.Point<Integer> outsideLightPoint = outsideLightTelemetryMap.get(Timestamp.of(iteratedTs));
            int outsideValue = outsideLightPoint.getValue();

            double currentNeededLevel = (DAY_START_HOUR <= hour && hour < NIGHT_START_HOUR)
                    ? dayLevel
                    : nightLevel;

            double diff = Math.max(0, currentNeededLevel - outsideValue);

            result.add(new Telemetry.Point<>(Timestamp.of(iteratedTs), (int) diff));
            iteratedDate = iteratedDate.plus(1, ChronoUnit.HOURS);
        }

        return result;
    }


    private Telemetry<Integer> createCo2ConcentrationTelemetryAndInterruption(Set<Long> aerations, Telemetry<Integer> temporalTelemetryCo2Generation, GreenhouseConfiguration configuration, boolean skipTelemetry, boolean fullTelemetryGeneration,
                                                                              long startGenerationTime, long endGenerationTime) {
        if (skipTelemetry) {
            return new Telemetry<>("skip");
        }
        Telemetry<Integer> result = new Telemetry<>("concentration");

        double startLevel = 1000;
        double minLevel = configuration.getPlantConfiguration().getMinCo2Concentration();
        double maxLevel = configuration.getPlantConfiguration().getMaxCo2Concentration();
        double decreaseLevel = maxLevel - minLevel;

        Map<Timestamp, Telemetry.Point<Integer>> co2ConsumptionMap = temporalTelemetryCo2Generation.getPoints()
                .stream()
                .collect(Collectors.toMap(Telemetry.Point::getTs, Functions.identity()));

        Pair<Long, Long> fromToPair;
        try {
            fromToPair = calculateNewDateRange(configuration.getStartTs(), configuration.getEndTs(), startGenerationTime, endGenerationTime, fullTelemetryGeneration);
        } catch (IllegalStateException e) {
            return new Telemetry<>("skip");
        }

        ZonedDateTime startDate = DateTimeUtils.fromTs(fromToPair.getLeft()).truncatedTo(ChronoUnit.HOURS);
        ZonedDateTime endDate = DateTimeUtils.fromTs(fromToPair.getRight()).truncatedTo(ChronoUnit.HOURS);
        ZonedDateTime iteratedDate = startDate;
        double currentLevel = startLevel;
        while (iteratedDate.isBefore(endDate)) {
            long iteratedTs = DateTimeUtils.toTs(iteratedDate);
            Integer co2Consumption = co2ConsumptionMap.get(Timestamp.of(iteratedTs)).getValue();

            currentLevel += co2Consumption;
            if (maxLevel <= currentLevel) {
                aerations.add(iteratedTs);
                currentLevel -= decreaseLevel;
            }

            currentLevel = Math.max(MIN_WORD_CO2_CONCENTRATION, currentLevel);

            result.add(new Telemetry.Point<>(Timestamp.of(iteratedTs), (int) currentLevel));
            iteratedDate = iteratedDate.plus(1, ChronoUnit.HOURS);
        }

        return result;
    }

    private Telemetry<Integer> createTemporalTelemetryCo2Generation(Telemetry<Integer> outsideLightTelemetry, Telemetry<Integer> insideLightTelemetry, GreenhouseConfiguration configuration, boolean skipTelemetry, boolean fullTelemetryGeneration, long startGenerationTime, long endGenerationTime) {
        if (skipTelemetry) {
            return new Telemetry<>("skip");
        }
        Telemetry<Integer> result = new Telemetry<>("temporal__co2_concentration");

        int nightConsumption = 50;
        double zeroConsumptionLightLevel = configuration.getPlantConfiguration().getDayMinLight();

        Map<Timestamp, Telemetry.Point<Integer>> outsideLightTelemetryMap = outsideLightTelemetry.getPoints()
                .stream()
                .collect(Collectors.toMap(Telemetry.Point::getTs, Functions.identity()));

        Map<Timestamp, Telemetry.Point<Integer>> insideLightTelemetryMap = insideLightTelemetry.getPoints()
                .stream()
                .collect(Collectors.toMap(Telemetry.Point::getTs, Functions.identity()));

        Pair<Long, Long> fromToPair;
        try {
            fromToPair = calculateNewDateRange(configuration.getStartTs(), configuration.getEndTs(), startGenerationTime, endGenerationTime, fullTelemetryGeneration);
        } catch (IllegalStateException e) {
            return new Telemetry<>("skip");
        }

        ZonedDateTime startDate = DateTimeUtils.fromTs(fromToPair.getLeft()).truncatedTo(ChronoUnit.HOURS);
        ZonedDateTime endDate = DateTimeUtils.fromTs(fromToPair.getRight()).truncatedTo(ChronoUnit.HOURS);
        ZonedDateTime iteratedDate = startDate;
        while (iteratedDate.isBefore(endDate)) {
            long iteratedTs = DateTimeUtils.toTs(iteratedDate);

            int outsideLight = outsideLightTelemetryMap.get(Timestamp.of(iteratedTs)).getValue();
            int insideLight = insideLightTelemetryMap.get(Timestamp.of(iteratedTs)).getValue();
            int light = outsideLight + insideLight;

            int consumption = (int) (Math.round((-1.0 * nightConsumption * light) / zeroConsumptionLightLevel) + nightConsumption);

            result.add(new Telemetry.Point<>(Timestamp.of(iteratedTs), consumption));
            iteratedDate = iteratedDate.plus(1, ChronoUnit.HOURS);
        }

        return result;
    }


    private Telemetry<Integer> createInsideTemperatureTelemetry(Set<Long> aerations, Set<Long> heatings, Set<Long> coolings, Telemetry<Integer> outsideTemperatureTelemetry, GreenhouseConfiguration configuration, boolean skipTelemetry, boolean fullTelemetryGeneration, long startGenerationTime, long endGenerationTime) {
        if (skipTelemetry) {
            return new Telemetry<>("skip");
        }
        Telemetry<Integer> result = new Telemetry<>("temperature_in");

        double startLevel = 15;

        double defaultCoefficient = 0.2;
        double aerationCoefficient = 0.4;

        boolean heatingMode = false;
        boolean coolingMode = false;

        double heatingIncreaseValue = 8;
        double coolingDecreaseValue = 8;

        double dayLowLevel = configuration.getPlantConfiguration().getDayMinTemperature();
        double dayHighLevel = configuration.getPlantConfiguration().getDayMaxTemperature();
        double dayOkLevel = (dayLowLevel + dayHighLevel) / 2;
        double nightLowLevel = configuration.getPlantConfiguration().getNightMinTemperature();
        double nightHighLevel = configuration.getPlantConfiguration().getNightMaxTemperature();
        double nightOkLevel = (nightLowLevel + nightHighLevel) / 2;

        Map<Timestamp, Telemetry.Point<Integer>> outsideTemperatureTelemetryMap = outsideTemperatureTelemetry.getPoints()
                .stream()
                .collect(Collectors.toMap(Telemetry.Point::getTs, Functions.identity()));

        Pair<Long, Long> fromToPair;
        try {
            fromToPair = calculateNewDateRange(configuration.getStartTs(), configuration.getEndTs(), startGenerationTime, endGenerationTime, fullTelemetryGeneration);
        } catch (IllegalStateException e) {
            return new Telemetry<>("skip");
        }

        ZonedDateTime startDate = DateTimeUtils.fromTs(fromToPair.getLeft()).truncatedTo(ChronoUnit.HOURS);
        ZonedDateTime endDate = DateTimeUtils.fromTs(fromToPair.getRight()).truncatedTo(ChronoUnit.HOURS);
        ZonedDateTime iteratedDate = startDate;
        double currentLevel = startLevel;
        while (iteratedDate.isBefore(endDate)) {
            long iteratedTs = DateTimeUtils.toTs(iteratedDate);
            int hour = iteratedDate.getHour();

            boolean day = (DAY_START_HOUR <= hour && hour < NIGHT_START_HOUR);
            boolean aeration = aerations.contains(iteratedTs);
            double lowLevel = (day) ? dayLowLevel : nightLowLevel;
            double highLevel = (day) ? dayHighLevel : nightHighLevel;
            double okLevel = (day) ? dayOkLevel : nightOkLevel;

            double outsideTemperature = outsideTemperatureTelemetryMap.get(Timestamp.of(iteratedTs)).getValue();
            double diff = outsideTemperature - currentLevel;

            currentLevel += diff * defaultCoefficient;
            currentLevel += (aeration) ? diff * aerationCoefficient : 0;

            if (currentLevel <= okLevel) {
                coolingMode = false;
            }
            if (okLevel <= currentLevel) {
                heatingMode = false;
            }
            if (currentLevel < lowLevel) {
                heatingMode = true;
            }
            if (highLevel < currentLevel) {
                coolingMode = true;
            }

            if (aeration) {
                heatingMode = true;
            }

            if (heatingMode) {
                heatings.add(iteratedTs);
                currentLevel += Math.min(heatingIncreaseValue, Math.abs(currentLevel - okLevel));
            }
            if (coolingMode) {
                coolings.add(iteratedTs);
                currentLevel -= Math.min(coolingDecreaseValue, Math.abs(currentLevel - okLevel));
            }

            if (RandomUtils.getBooleanByProbability(0.2)) {
                currentLevel += RandomUtils.getRandomNumber(-2, 2);
            }

            result.add(new Telemetry.Point<>(Timestamp.of(iteratedTs), (int) currentLevel));
            iteratedDate = iteratedDate.plus(1, ChronoUnit.HOURS);
        }

        return result;
    }

    private Telemetry<Integer> createInsideHumidityTelemetry(Set<Long> aerations, Set<Long> heatings, Set<Long> coolings, Set<Long> humidifications, Set<Long> dehumidifications, Telemetry<Integer> outsideHumidityTelemetry, GreenhouseConfiguration configuration, boolean skipTelemetry,
                                                             boolean fullTelemetryGeneration, long startGenerationTime, long endGenerationTime) {
        if (skipTelemetry) {
            return new Telemetry<>("skip");
        }
        Telemetry<Integer> result = new Telemetry<>("humidity_in");

        double startLevel = 30;
        double increaseLevel = 3;
        double aerationDecreaseValue = 30;
        double heatingIncreaseValue = 2;
        double coolingDecreaseValue = 5;
        double humidificationIncreaseValue = 10;
        double dehumidificationDecreaseValue = 10;

        boolean humidificationMode = false;
        boolean dehumidificationMode = false;
        double lowLevel = configuration.getPlantConfiguration().getMinAirHumidity();
        double highLevel = configuration.getPlantConfiguration().getMaxAirHumidity();
        double okLevel = (lowLevel + highLevel) / 2;

        Map<Timestamp, Telemetry.Point<Integer>> outsideHumidityTelemetryMap = outsideHumidityTelemetry.getPoints()
                .stream()
                .collect(Collectors.toMap(Telemetry.Point::getTs, Functions.identity()));

        Pair<Long, Long> fromToPair;
        try {
            fromToPair = calculateNewDateRange(configuration.getStartTs(), configuration.getEndTs(), startGenerationTime, endGenerationTime, fullTelemetryGeneration);
        } catch (IllegalStateException e) {
            return new Telemetry<>("skip");
        }

        ZonedDateTime startDate = DateTimeUtils.fromTs(fromToPair.getLeft()).truncatedTo(ChronoUnit.HOURS);
        ZonedDateTime endDate = DateTimeUtils.fromTs(fromToPair.getRight()).truncatedTo(ChronoUnit.HOURS);
        ZonedDateTime iteratedDate = startDate;
        double currentLevel = startLevel;
        while (iteratedDate.isBefore(endDate)) {
            long iteratedTs = DateTimeUtils.toTs(iteratedDate);

            boolean aeration = aerations.contains(iteratedTs);
            boolean heating = heatings.contains(iteratedTs);
            boolean cooling = coolings.contains(iteratedTs);

            int outsideHumidity = outsideHumidityTelemetryMap.get(Timestamp.of(iteratedTs)).getValue();
            double diff = outsideHumidity - currentLevel;

            currentLevel += increaseLevel;
            currentLevel += (aeration) ? Math.signum(diff) * Math.min(aerationDecreaseValue, Math.abs(diff)) : 0;
            currentLevel += (heating) ? heatingIncreaseValue : 0;
            currentLevel -= (cooling) ? coolingDecreaseValue : 0;

            if (currentLevel <= okLevel) {
                dehumidificationMode = false;
            }
            if (okLevel <= currentLevel) {
                humidificationMode = false;
            }
            if (currentLevel < lowLevel) {
                humidificationMode = true;
            }
            if (highLevel < currentLevel) {
                dehumidificationMode = true;
            }

            if (humidificationMode) {
                humidifications.add(iteratedTs);
                currentLevel += Math.min(humidificationIncreaseValue, Math.abs(currentLevel - okLevel)) + RandomUtils.getRandomNumber(-1, 1);
            }
            if (dehumidificationMode) {
                dehumidifications.add(iteratedTs);
                currentLevel -= Math.min(dehumidificationDecreaseValue, Math.abs(currentLevel - okLevel)) + RandomUtils.getRandomNumber(-1, 1);
            }

            if (RandomUtils.getBooleanByProbability(0.3)) {
                currentLevel += RandomUtils.getRandomNumber(0, 1);
            }

            currentLevel = Math.min(currentLevel, 100);
            currentLevel = Math.max(currentLevel, 0);

            result.add(new Telemetry.Point<>(Timestamp.of(iteratedTs), (int) currentLevel));
            iteratedDate = iteratedDate.plus(1, ChronoUnit.HOURS);
        }

        return result;
    }


    private Telemetry<Double> createTelemetrySoilNitrogenLevel(GreenhouseConfiguration configuration, boolean skipTelemetry, boolean fullTelemetryGeneration, long startGenerationTime, long endGenerationTime) {
        if (skipTelemetry) {
            return new Telemetry<>("skip");
        }

        PlantConfiguration plantConfiguration = configuration.getPlantConfiguration();

        Pair<Long, Long> fromToPair;
        try {
            fromToPair = calculateNewDateRange(configuration.getStartTs(), configuration.getEndTs(), startGenerationTime, endGenerationTime, fullTelemetryGeneration);
        } catch (IllegalStateException e) {
            return new Telemetry<>("skip");
        }

        ZonedDateTime startDate = DateTimeUtils.fromTs(fromToPair.getLeft()).truncatedTo(ChronoUnit.HOURS);
        ZonedDateTime endDate = DateTimeUtils.fromTs(fromToPair.getRight()).truncatedTo(ChronoUnit.HOURS);
        Telemetry<Double> consumption = createTemporalTelemetryPlantNitrogenConsumption(plantConfiguration, startDate, endDate);

        String name = "nitrogen";
        double startLevel = RandomUtils.getRandomNumber((long) plantConfiguration.getMinNitrogenLevel(), (long) plantConfiguration.getMaxNitrogenLevel());
        double minLevel = plantConfiguration.getMinNitrogenLevel();
        double raiseValue = plantConfiguration.getMaxNitrogenLevel() - plantConfiguration.getMinNitrogenLevel();

        return createSoilLevel(name, startLevel, minLevel, raiseValue, consumption);
    }

    private Telemetry<Double> createTelemetrySoilPhosphorusLevel(GreenhouseConfiguration configuration, boolean skipTelemetry, boolean fullTelemetryGeneration, long startGenerationTime, long endGenerationTime) {
        if (skipTelemetry) {
            return new Telemetry<>("skip");
        }

        PlantConfiguration plantConfiguration = configuration.getPlantConfiguration();

        Pair<Long, Long> fromToPair;
        try {
            fromToPair = calculateNewDateRange(configuration.getStartTs(), configuration.getEndTs(), startGenerationTime, endGenerationTime, fullTelemetryGeneration);
        } catch (IllegalStateException e) {
            return new Telemetry<>("skip");
        }

        ZonedDateTime startDate = DateTimeUtils.fromTs(fromToPair.getLeft()).truncatedTo(ChronoUnit.HOURS);
        ZonedDateTime endDate = DateTimeUtils.fromTs(fromToPair.getRight()).truncatedTo(ChronoUnit.HOURS);
        Telemetry<Double> consumption = createTemporalTelemetryPlantPhosphorusConsumption(plantConfiguration, startDate, endDate);

        String name = "phosphorus";
        double startLevel = RandomUtils.getRandomNumber((long) plantConfiguration.getMinPhosphorusLevel(), (long) plantConfiguration.getMaxPhosphorusLevel());
        double minLevel = plantConfiguration.getMinPhosphorusLevel();
        double raiseValue = plantConfiguration.getMaxPhosphorusLevel() - plantConfiguration.getMinPhosphorusLevel();

        return createSoilLevel(name, startLevel, minLevel, raiseValue, consumption);
    }

    private Telemetry<Double> createTelemetrySoilPotassiumLevel(GreenhouseConfiguration configuration, boolean skipTelemetry, boolean fullTelemetryGeneration, long startGenerationTime, long endGenerationTime) {
        if (skipTelemetry) {
            return new Telemetry<>("skip");
        }

        PlantConfiguration plantConfiguration = configuration.getPlantConfiguration();

        Pair<Long, Long> fromToPair;
        try {
            fromToPair = calculateNewDateRange(configuration.getStartTs(), configuration.getEndTs(), startGenerationTime, endGenerationTime, fullTelemetryGeneration);
        } catch (IllegalStateException e) {
            return new Telemetry<>("skip");
        }

        ZonedDateTime startDate = DateTimeUtils.fromTs(fromToPair.getLeft()).truncatedTo(ChronoUnit.HOURS);
        ZonedDateTime endDate = DateTimeUtils.fromTs(fromToPair.getRight()).truncatedTo(ChronoUnit.HOURS);
        Telemetry<Double> consumption = createTemporalTelemetryPlantPotassiumConsumption(plantConfiguration, startDate, endDate);

        String name = "potassium";
        double startLevel = RandomUtils.getRandomNumber((long) plantConfiguration.getMinPotassiumLevel(), (long) plantConfiguration.getMaxPotassiumLevel());
        double minLevel = plantConfiguration.getMinPotassiumLevel();
        double raiseValue = plantConfiguration.getMaxPotassiumLevel() - plantConfiguration.getMinPotassiumLevel();

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


    private Telemetry<Double> createTemporalTelemetryPlantNitrogenConsumption(PlantConfiguration configuration, ZonedDateTime startDate, ZonedDateTime endDate) {
        int noiseAmplitude = 3;
        double noiseCoefficient = 1.0;
        String name = "temporal__" + configuration.getName().toString().toLowerCase() + "_" + configuration.getVariety().toLowerCase();
        int totalPeriodDays = (configuration.getMinRipeningPeriodDays() + configuration.getMaxRipeningPeriodDays()) / 2;
        return createTemporalTelemetryPlantConsumption(
                name, startDate, endDate, noiseAmplitude, noiseCoefficient, totalPeriodDays,
                configuration.getGrowthPeriodsDayList(), configuration.getGrowthPeriodsNitrogenConsumption()
        );
    }

    private Telemetry<Double> createTemporalTelemetryPlantPhosphorusConsumption(PlantConfiguration configuration, ZonedDateTime startDate, ZonedDateTime endDate) {
        int noiseAmplitude = 1;
        double noiseCoefficient = 0.01;
        String name = "temporal__" + configuration.getName().toString().toLowerCase() + "_" + configuration.getVariety().toLowerCase();
        int totalPeriodDays = (configuration.getMinRipeningPeriodDays() + configuration.getMaxRipeningPeriodDays()) / 2;
        return createTemporalTelemetryPlantConsumption(
                name, startDate, endDate, noiseAmplitude, noiseCoefficient, totalPeriodDays,
                configuration.getGrowthPeriodsDayList(), configuration.getGrowthPeriodsPhosphorusConsumption()
        );
    }

    private Telemetry<Double> createTemporalTelemetryPlantPotassiumConsumption(PlantConfiguration configuration, ZonedDateTime startDate, ZonedDateTime endDate) {
        int noiseAmplitude = 3;
        double noiseCoefficient = 1.0;
        String name = "temporal__" + configuration.getName().toString().toLowerCase() + "_" + configuration.getVariety().toLowerCase();
        int totalPeriodDays = (configuration.getMinRipeningPeriodDays() + configuration.getMaxRipeningPeriodDays()) / 2;
        return createTemporalTelemetryPlantConsumption(
                name, startDate, endDate, noiseAmplitude, noiseCoefficient, totalPeriodDays,
                configuration.getGrowthPeriodsDayList(), configuration.getGrowthPeriodsPotassiumConsumption()
        );
    }

    private Telemetry<Double> createTemporalTelemetryPlantConsumption(String name, ZonedDateTime startDate, ZonedDateTime endDate, int noiseAmplitude, double noiseCoefficient, int totalPeriodDays, List<Integer> periodDays, List<Double> periodValues) {
        Telemetry<Double> result = new Telemetry<>(name);

        startDate = startDate.truncatedTo(ChronoUnit.DAYS);
        endDate = endDate.truncatedTo(ChronoUnit.DAYS);
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


    private Telemetry<Double> createTelemetrySoilMoisture(GreenhouseConfiguration configuration, Telemetry<Double> temporalTelemetrySoilWaterConsumption, Set<Long> sectionIrrigations, boolean skipTelemetry, boolean fullTelemetryGeneration, long startGenerationTime, long endGenerationTime) {
        if (skipTelemetry) {
            return new Telemetry<>("skip");
        }
        Telemetry<Double> result = new Telemetry<>("moisture");

        PlantConfiguration plantConfiguration = configuration.getPlantConfiguration();
        double minLevel = plantConfiguration.getMinSoilMoisture();
        double maxLevel = plantConfiguration.getMaxSoilMoisture();
        double startLevel = RandomUtils.getRandomNumber(minLevel, maxLevel);

        Map<Timestamp, Telemetry.Point<Double>> waterConsumptionMap = temporalTelemetrySoilWaterConsumption.getPoints()
                .stream()
                .collect(Collectors.toMap(Telemetry.Point::getTs, Functions.identity()));

        Pair<Long, Long> fromToPair;
        try {
            fromToPair = calculateNewDateRange(configuration.getStartTs(), configuration.getEndTs(), startGenerationTime, endGenerationTime, fullTelemetryGeneration);
        } catch (IllegalStateException e) {
            return new Telemetry<>("skip");
        }

        ZonedDateTime startDate = DateTimeUtils.fromTs(fromToPair.getLeft()).truncatedTo(ChronoUnit.HOURS);
        ZonedDateTime endDate = DateTimeUtils.fromTs(fromToPair.getRight()).truncatedTo(ChronoUnit.HOURS);
        ZonedDateTime iteratedDate = startDate;
        double currentLevel = startLevel;
        while (iteratedDate.isBefore(endDate)) {
            long iteratedTs = DateTimeUtils.toTs(iteratedDate);
            double waterConsumption = waterConsumptionMap.get(Timestamp.of(iteratedTs)).getValue();

            currentLevel -= waterConsumption;
            if (currentLevel <= minLevel) {
                currentLevel += (maxLevel - minLevel);
                sectionIrrigations.add(iteratedTs);
            }

            currentLevel = Math.min(currentLevel, 100);
            currentLevel = Math.max(currentLevel, 0);

            result.add(new Telemetry.Point<>(Timestamp.of(iteratedTs), currentLevel));
            iteratedDate = iteratedDate.plus(1, ChronoUnit.HOURS);
        }

        return result;
    }

    private Telemetry<Double> createTemporalTelemetrySoilWaterConsumption(GreenhouseConfiguration configuration, boolean skipTelemetry, boolean fullTelemetryGeneration, long startGenerationTime, long endGenerationTime) {
        if (skipTelemetry) {
            return new Telemetry<>("skip");
        }
        Telemetry<Double> result = new Telemetry<>("temporal__soil_water_consumption");

        PlantConfiguration plantConfiguration = configuration.getPlantConfiguration();
        double minLevel = plantConfiguration.getMinSoilMoisture();
        double maxLevel = plantConfiguration.getMaxSoilMoisture();
        long period = 24L * (plantConfiguration.getMinRipeningPeriodDays() + plantConfiguration.getMaxRipeningPeriodDays()) / 2;

        Pair<Long, Long> fromToPair;
        try {
            fromToPair = calculateNewDateRange(configuration.getStartTs(), configuration.getEndTs(), startGenerationTime, endGenerationTime, fullTelemetryGeneration);
        } catch (IllegalStateException e) {
            return new Telemetry<>("skip");
        }

        ZonedDateTime startDate = DateTimeUtils.fromTs(fromToPair.getLeft()).truncatedTo(ChronoUnit.HOURS);
        ZonedDateTime endDate = DateTimeUtils.fromTs(fromToPair.getRight()).truncatedTo(ChronoUnit.HOURS);
        ZonedDateTime iteratedDate = startDate;
        while (iteratedDate.isBefore(endDate)) {
            long iteratedTs = DateTimeUtils.toTs(iteratedDate);
            long hoursBetween = ChronoUnit.HOURS.between(startDate, iteratedDate);
            long hourCycle = (hoursBetween % period) + 1;

            long step = hourCycle / (period / 3) + 1;

            double consumption = Math.pow(0.8, step) * (minLevel + maxLevel) / period;
            consumption += RandomUtils.getRandomNumber(0, 1);

            result.add(new Telemetry.Point<>(Timestamp.of(iteratedTs), consumption));
            iteratedDate = iteratedDate.plus(1, ChronoUnit.HOURS);
        }

        return result;
    }


    private Telemetry<Integer> createTelemetrySoilTemperature(GreenhouseConfiguration configuration, Telemetry<Integer> insideTemperatureTelemetry, Set<Long> sectionIrrigations, boolean skipTelemetry, boolean fullTelemetryGeneration, long startGenerationTime, long endGenerationTime) {
        if (skipTelemetry) {
            return new Telemetry<>("skip");
        }
        Telemetry<Integer> result = new Telemetry<>("temperature");

        PlantConfiguration plantConfiguration = configuration.getPlantConfiguration();
        double minLevel = plantConfiguration.getDayMinTemperature();
        double maxLevel = plantConfiguration.getDayMaxTemperature();
        double startLevel = RandomUtils.getRandomNumber(minLevel, maxLevel);
        double increaseLevel = 0.5;
        double decreaseIrrigationLevel = 5;

        Map<Timestamp, Telemetry.Point<Integer>> insideTemperatureTelemetryMap = insideTemperatureTelemetry.getPoints()
                .stream()
                .collect(Collectors.toMap(Telemetry.Point::getTs, Functions.identity()));

        Pair<Long, Long> fromToPair;
        try {
            fromToPair = calculateNewDateRange(configuration.getStartTs(), configuration.getEndTs(), startGenerationTime, endGenerationTime, fullTelemetryGeneration);
        } catch (IllegalStateException e) {
            return new Telemetry<>("skip");
        }

        ZonedDateTime startDate = DateTimeUtils.fromTs(fromToPair.getLeft()).truncatedTo(ChronoUnit.HOURS);
        ZonedDateTime endDate = DateTimeUtils.fromTs(fromToPair.getRight()).truncatedTo(ChronoUnit.HOURS);
        ZonedDateTime iteratedDate = startDate;
        double currentLevel = startLevel;
        while (iteratedDate.isBefore(endDate)) {
            long iteratedTs = DateTimeUtils.toTs(iteratedDate);
            boolean irrigation = sectionIrrigations.contains(iteratedTs);
            int insideTemperature = insideTemperatureTelemetryMap.get(Timestamp.of(iteratedTs)).getValue();

            double diff = insideTemperature - currentLevel;

            currentLevel += diff * increaseLevel;
            currentLevel -= (irrigation) ? decreaseIrrigationLevel : 0;

            result.add(new Telemetry.Point<>(Timestamp.of(iteratedTs), (int) currentLevel));
            iteratedDate = iteratedDate.plus(1, ChronoUnit.HOURS);
        }

        return result;
    }


    private Telemetry<Double> createTelemetrySoilAcidity(GreenhouseConfiguration configuration, Set<Long> sectionIrrigations, Set<Long> acidification, boolean skipTelemetry, boolean fullTelemetryGeneration,
                                                         long startGenerationTime, long endGenerationTime) {
        if (skipTelemetry) {
            return new Telemetry<>("skip");
        }
        Telemetry<Double> result = new Telemetry<>("acidity");

        PlantConfiguration plantConfiguration = configuration.getPlantConfiguration();
        int period = plantConfiguration.getMaxRipeningPeriodDays() * 24;
        double minLevel = plantConfiguration.getMinPh();
        double maxLevel = plantConfiguration.getMaxPh();
        double startLevel = RandomUtils.getRandomNumber(minLevel, maxLevel);

        double increaseLevel = (maxLevel - minLevel) / period;
        double irrigationIncreaseLevel = (maxLevel - minLevel) / period * 24 * 5;
        double acidificationDecreaseLevel = (maxLevel - minLevel);

        Pair<Long, Long> fromToPair;
        try {
            fromToPair = calculateNewDateRange(configuration.getStartTs(), configuration.getEndTs(), startGenerationTime, endGenerationTime, fullTelemetryGeneration);
        } catch (IllegalStateException e) {
            return new Telemetry<>("skip");
        }

        ZonedDateTime startDate = DateTimeUtils.fromTs(fromToPair.getLeft()).truncatedTo(ChronoUnit.HOURS);
        ZonedDateTime endDate = DateTimeUtils.fromTs(fromToPair.getRight()).truncatedTo(ChronoUnit.HOURS);
        ZonedDateTime iteratedDate = startDate;
        double currentLevel = startLevel;
        while (iteratedDate.isBefore(endDate)) {
            long iteratedTs = DateTimeUtils.toTs(iteratedDate);
            boolean irrigation = sectionIrrigations.contains(iteratedTs);

            currentLevel -= increaseLevel;
            currentLevel -= (irrigation) ? irrigationIncreaseLevel : 0;

            if (currentLevel <= minLevel) {
                acidification.add(iteratedTs);
                currentLevel += acidificationDecreaseLevel;
            }

            currentLevel += RandomUtils.getRandomNumber(-0.03, 0.03);

            result.add(new Telemetry.Point<>(Timestamp.of(iteratedTs), currentLevel));
            iteratedDate = iteratedDate.plus(1, ChronoUnit.HOURS);
        }

        return result;
    }


    private void createTelemetryHarvestReporter(Telemetry<Double> telemetryCropWeight, Telemetry<String> telemetryWorkerInCharge, GreenhouseConfiguration configuration, boolean skipTelemetry, boolean fullTelemetryGeneration, long startGenerationTime, long endGenerationTime) {
        if (skipTelemetry) {
            return;
        }

        PlantConfiguration plantConfiguration = configuration.getPlantConfiguration();
        int periodMin = plantConfiguration.getMinRipeningPeriodDays();
        int periodMax = plantConfiguration.getMaxRipeningPeriodDays();
        double averageCropWeight = plantConfiguration.getAverageCropWeight();
        double cropWeightNoiseAmplitude = averageCropWeight / 5;
        List<WorkerInChargeName> workersInCharge = configuration.getWorkersInCharge();

        double currentLevel = 0;
        boolean skip = true;

        Pair<Long, Long> fromToPair;
        try {
            fromToPair = calculateNewDateRange(configuration.getStartTs(), configuration.getEndTs(), startGenerationTime, endGenerationTime, fullTelemetryGeneration);
        } catch (IllegalStateException e) {
            return;
        }

        ZonedDateTime startDate = DateTimeUtils.fromTs(fromToPair.getLeft()).truncatedTo(ChronoUnit.HOURS);
        ZonedDateTime endDate = DateTimeUtils.fromTs(fromToPair.getRight()).truncatedTo(ChronoUnit.HOURS);
        ZonedDateTime iteratedDate = startDate;
        while (iteratedDate.isBefore(endDate)) {
            long iteratedTs = DateTimeUtils.toTs(iteratedDate);
            long daysBetween = ChronoUnit.DAYS.between(startDate, iteratedDate);
            long daysPeriod = daysBetween % periodMax;

            if (periodMin < daysPeriod) {
                if (skip) {
                    skip = false;
                    currentLevel = averageCropWeight;
                    currentLevel += RandomUtils.getRandomNumber(-cropWeightNoiseAmplitude, cropWeightNoiseAmplitude);
                }
                int workerIndex = (int) RandomUtils.getRandomNumber(0, workersInCharge.size());
                WorkerInChargeName worker = workersInCharge.get(workerIndex);

                double value = RandomUtils.getRandomNumber(0.5, 0.5 + currentLevel);
                value = (double) Math.round(value * 100d) / 100d;

                if (currentLevel < value) {
                    value = currentLevel;
                }
                currentLevel -= value;

                if (0 < value) {
                    telemetryCropWeight.add(new Telemetry.Point<>(Timestamp.of(iteratedTs), value));
                    telemetryWorkerInCharge.add(new Telemetry.Point<>(Timestamp.of(iteratedTs), worker.toString()));
                }
            } else {
                skip = true;
            }
            iteratedDate = iteratedDate.plus(1, ChronoUnit.DAYS);
        }
    }


    private void createTelemetryConsumptionEnergy(Telemetry<Integer> insideLightTelemetry, Set<Long> aerations, Set<Long> heatings, Set<Long> coolings, Set<Long> humidifications, Set<Long> dehumidifications, Map<String, Set<Long>> irrigations, GreenhouseConfiguration configuration, boolean skipTelemetry, boolean fullTelemetryGeneration, long startGenerationTime, long endGenerationTime, Telemetry<Double> energyConsumptionLight, Telemetry<Double> energyConsumptionHeating, Telemetry<Double> energyConsumptionCooling, Telemetry<Double> energyConsumptionAirControl, Telemetry<Double> energyConsumptionIrrigation) {
        if (skipTelemetry) {
            return;
        }

        Map<Timestamp, Telemetry.Point<Integer>> insideLightTelemetryMap = insideLightTelemetry.getPoints()
                .stream()
                .collect(Collectors.toMap(Telemetry.Point::getTs, Functions.identity()));

        Map<Long, Long> irrigationCountMap = irrigations.values()
                .stream()
                .flatMap(Collection::stream)
                .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));

        long fromMs = configuration.getStartTs();
        long toMs = configuration.getEndTs();

        Pair<Long, Long> fromToPair;
        try {
            fromToPair = calculateNewDateRange(configuration.getStartTs(), configuration.getEndTs(), startGenerationTime, endGenerationTime, fullTelemetryGeneration);
        } catch (IllegalStateException e) {
            return;
        }

        ZonedDateTime startDate = DateTimeUtils.fromTs(fromToPair.getLeft()).truncatedTo(ChronoUnit.HOURS);
        ZonedDateTime endDate = DateTimeUtils.fromTs(fromToPair.getRight()).truncatedTo(ChronoUnit.HOURS);
        ZonedDateTime iteratedDate = startDate;

        while (iteratedDate.isBefore(endDate)) {
            long iteratedTs = DateTimeUtils.toTs(iteratedDate);

            int light = insideLightTelemetryMap.get(Timestamp.of(iteratedTs)).getValue();
            boolean aeration = aerations.contains(iteratedTs);
            boolean heating = heatings.contains(iteratedTs);
            boolean cooling = coolings.contains(iteratedTs);
            boolean humidification = humidifications.contains(iteratedTs);
            boolean dehumidification = dehumidifications.contains(iteratedTs);
            long irrigationCount = irrigationCountMap.computeIfAbsent(iteratedTs, key -> 0L);

            double valueLight = 0;
            valueLight += light * 0.05;
            valueLight += RandomUtils.getRandomNumber(-2, 2);
            valueLight = Math.max(0, valueLight);

            double valueHeating = 0;
            valueHeating += (heating) ? 200 : 0;
            valueHeating += RandomUtils.getRandomNumber(-2, 2);
            valueHeating = Math.max(0, valueHeating);

            double valueCooling = 0;
            valueCooling += (cooling) ? 100 : 0;
            valueCooling += RandomUtils.getRandomNumber(-2, 2);
            valueCooling = Math.max(0, valueCooling);

            double valueAirControl = 0;
            valueAirControl += (aeration) ? 20 : 0;
            valueAirControl += (humidification) ? 20 : 0;
            valueAirControl += (dehumidification) ? 50 : 0;
            valueAirControl += RandomUtils.getRandomNumber(-2, 2);
            valueAirControl = Math.max(0, valueAirControl);

            double valueIrrigation = 0;
            valueIrrigation += irrigationCount * 100;
            valueIrrigation += RandomUtils.getRandomNumber(-2, 2);
            valueIrrigation = Math.max(0, valueIrrigation);

            Timestamp timestamp = Timestamp.of(iteratedTs);
            energyConsumptionLight.add(new Telemetry.Point<>(timestamp, valueLight));
            energyConsumptionHeating.add(new Telemetry.Point<>(timestamp, valueHeating));
            energyConsumptionCooling.add(new Telemetry.Point<>(timestamp, valueCooling));
            energyConsumptionAirControl.add(new Telemetry.Point<>(timestamp, valueAirControl));
            energyConsumptionIrrigation.add(new Telemetry.Point<>(timestamp, valueIrrigation));

            iteratedDate = iteratedDate.plus(1, ChronoUnit.HOURS);
        }
    }

    private Telemetry<Double> createTelemetryConsumptionWater(Set<Long> humidifications, Map<String, Set<Long>> irrigations, GreenhouseConfiguration configuration, boolean skipTelemetry, boolean fullTelemetryGeneration, long startGenerationTime, long endGenerationTime) {
        if (skipTelemetry) {
            return new Telemetry<>("skip");
        }
        Telemetry<Double> result = new Telemetry<>("consumptionWater");

        Map<Long, Long> irrigationCountMap = irrigations.values()
                .stream()
                .flatMap(Collection::stream)
                .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));

        Pair<Long, Long> fromToPair;
        try {
            fromToPair = calculateNewDateRange(configuration.getStartTs(), configuration.getEndTs(), startGenerationTime, endGenerationTime, fullTelemetryGeneration);
        } catch (IllegalStateException e) {
            return new Telemetry<>("skip");
        }

        ZonedDateTime startDate = DateTimeUtils.fromTs(fromToPair.getLeft()).truncatedTo(ChronoUnit.HOURS);
        ZonedDateTime endDate = DateTimeUtils.fromTs(fromToPair.getRight()).truncatedTo(ChronoUnit.HOURS);
        ZonedDateTime iteratedDate = startDate;
        while (iteratedDate.isBefore(endDate)) {
            long iteratedTs = DateTimeUtils.toTs(iteratedDate);

            boolean humidification = humidifications.contains(iteratedTs);
            long irrigationCount = irrigationCountMap.computeIfAbsent(iteratedTs, key -> 0L);

            double value = 0;
            value += (humidification) ? 0.5 : 0;
            value += irrigationCount * 2.5;

            value += RandomUtils.getRandomNumber(-0.5, 0.5);
            value = Math.max(0, value);

            result.add(new Telemetry.Point<>(Timestamp.of(iteratedTs), value));
            iteratedDate = iteratedDate.plus(1, ChronoUnit.HOURS);
        }

        return result;
    }


    private Asset createPlant(Plant plant, UUID ownerId, UUID assetGroupId, boolean strictGeneration) {
        String name = plant.getSystemName();
        String entityType = plant.entityType();
        final Set<Attribute<?>> attributes = Set.of(
                new Attribute<>("name", plant.getName()),
                new Attribute<>("variety", plant.getVariety()),
                new Attribute<>("dayMinTemperature", plant.getDayMinTemperature()),
                new Attribute<>("dayMaxTemperature", plant.getDayMaxTemperature()),
                new Attribute<>("nightMinTemperature", plant.getNightMinTemperature()),
                new Attribute<>("nightMaxTemperature", plant.getNightMaxTemperature()),
                new Attribute<>("dayMinLight", plant.getDayMinLight()),
                new Attribute<>("dayMaxLight", plant.getDayMaxLight()),
                new Attribute<>("nightMinLight", plant.getNightMinLight()),
                new Attribute<>("nightMaxLight", plant.getNightMaxLight()),
                new Attribute<>("minAirHumidity", plant.getMinAirHumidity()),
                new Attribute<>("maxAirHumidity", plant.getMaxAirHumidity()),
                new Attribute<>("minSoilMoisture", plant.getMinSoilMoisture()),
                new Attribute<>("maxSoilMoisture", plant.getMaxSoilMoisture()),
                new Attribute<>("minSoilTemperature", plant.getMinSoilTemperature()),
                new Attribute<>("maxSoilTemperature", plant.getMaxSoilTemperature()),
                new Attribute<>("minCo2Concentration", plant.getMinCo2Concentration()),
                new Attribute<>("maxCo2Concentration", plant.getMaxCo2Concentration()),
                new Attribute<>("minPh", plant.getMinPh()),
                new Attribute<>("maxPh", plant.getMaxPh()),
                new Attribute<>("minRipeningPeriodDays", plant.getMinRipeningPeriodDays()),
                new Attribute<>("maxRipeningPeriodDays", plant.getMaxRipeningPeriodDays()),
                new Attribute<>("minNitrogenLevel", plant.getMinNitrogenLevel()),
                new Attribute<>("maxNitrogenLevel", plant.getMaxNitrogenLevel()),
                new Attribute<>("minPhosphorusLevel", plant.getMinPhosphorusLevel()),
                new Attribute<>("maxPhosphorusLevel", plant.getMaxPhosphorusLevel()),
                new Attribute<>("minPotassiumLevel", plant.getMinPotassiumLevel()),
                new Attribute<>("maxPotassiumLevel", plant.getMaxPotassiumLevel()),
                new Attribute<>("averageCropWeight", plant.getAverageCropWeight()),
                new Attribute<>("growthPeriodsDayList", plant.getGrowthPeriodsDayList()),
                new Attribute<>("growthPeriodsNitrogenConsumption", plant.getGrowthPeriodsNitrogenConsumption()),
                new Attribute<>("growthPeriodsPhosphorusConsumption", plant.getGrowthPeriodsPhosphorusConsumption()),
                new Attribute<>("growthPeriodsPotassiumConsumption", plant.getGrowthPeriodsPotassiumConsumption())
        );

        Asset asset;
        if (tbRestClient.isPe()) {
            var newCustomerId = new CustomerId(ownerId);
            asset = strictGeneration
                    ? tbRestClient.createAsset(name, entityType, newCustomerId, attributes)
                    : tbRestClient.createAssetIfNotExists(name, entityType, newCustomerId, attributes)
            ;
            tbRestClient.addEntitiesToTheGroup(assetGroupId, Set.of(asset.getUuidId()));
        } else {
            asset = strictGeneration
                    ? tbRestClient.createAsset(name, entityType, attributes)
                    : tbRestClient.createAssetIfNotExists(name, entityType, attributes)
            ;
            tbRestClient.assignAssetToCustomer(ownerId, asset.getUuidId());
        }

        this.plantToIdMap.put(plant, asset.getUuidId());
        return asset;
    }

    private Asset createGreenhouse(Greenhouse greenhouse, UUID ownerId, UUID assetGroupId, boolean strictGeneration) {
        String name = greenhouse.getSystemName();
        String entityType = greenhouse.entityType();
        final Set<Attribute<?>> attributes = Set.of(
                new Attribute<>("address", greenhouse.getAddress()),
                new Attribute<>("latitude", greenhouse.getLatitude()),
                new Attribute<>("longitude", greenhouse.getLongitude()),
                new Attribute<>("workersInCharge", greenhouse.getWorkersInCharge())
        );

        Asset asset;
        if (tbRestClient.isPe()) {
            var customerId = new CustomerId(ownerId);
            asset = strictGeneration
                    ? tbRestClient.createAsset(name, entityType, customerId, attributes)
                    : tbRestClient.createAssetIfNotExists(name, entityType, customerId, attributes)
            ;
            tbRestClient.addEntitiesToTheGroup(assetGroupId, Set.of(asset.getUuidId()));
        } else {
            asset = strictGeneration
                    ? tbRestClient.createAsset(name, entityType, attributes)
                    : tbRestClient.createAssetIfNotExists(name, entityType, attributes)
            ;
            tbRestClient.assignAssetToCustomer(ownerId, asset.getUuidId());
        }

        this.greenhouseToIdMap.put(greenhouse, asset.getUuidId());
        return asset;
    }

    private Asset createSection(Section section, UUID ownerId, UUID assetGroupId, boolean strictGeneration) {
        String name = section.getSystemName();
        String entityType = section.entityType();
        final Set<Attribute<?>> attributes = Set.of(
                new Attribute<>("position_height", section.getPositionHeight()),
                new Attribute<>("position_width", section.getPositionWidth()),
                new Attribute<>("area", section.getArea()),
                new Attribute<>("from_greenhouse", section.getFromGreenhouse())
        );

        Asset asset;
        if (tbRestClient.isPe()) {
            var customerId = new CustomerId(ownerId);
            asset = strictGeneration
                    ? tbRestClient.createAsset(name, entityType, customerId, attributes)
                    : tbRestClient.createAssetIfNotExists(name, entityType, customerId, attributes)
            ;
            tbRestClient.addEntitiesToTheGroup(assetGroupId, Set.of(asset.getUuidId()));
        } else {
            asset = strictGeneration
                    ? tbRestClient.createAsset(name, entityType, attributes)
                    : tbRestClient.createAssetIfNotExists(name, entityType, attributes)
            ;
            tbRestClient.assignAssetToCustomer(ownerId, asset.getUuidId());
        }

        return asset;
    }

    private Device createSoilNpkSensor(SoilNpkSensor soilNpkSensor, UUID ownerId, UUID deviceGroupId, boolean strictGeneration) {
        String name = soilNpkSensor.getSystemName();
        String entityType = soilNpkSensor.entityType();
        final Set<Attribute<?>> attributes = Set.of(
                new Attribute<>("from_greenhouse", soilNpkSensor.getFromGreenhouse())
        );

        Device device;
        if (tbRestClient.isPe()) {
            device = strictGeneration
                    ? tbRestClient.createDevice(name, entityType, new CustomerId(ownerId), attributes)
                    : tbRestClient.createDeviceIfNotExists(name, entityType, new CustomerId(ownerId), attributes);
            tbRestClient.addEntitiesToTheGroup(deviceGroupId, Set.of(device.getUuidId()));
        } else {
            device = strictGeneration
                    ? tbRestClient.createDevice(name, entityType, attributes)
                    : tbRestClient.createDeviceIfNotExists(name, entityType, attributes)
            ;
            tbRestClient.assignDeviceToCustomer(ownerId, device.getUuidId());
        }

        final var deviceCredentials = tbRestClient.getDeviceCredentials(device.getUuidId());

        tbRestClient.pushTelemetry(deviceCredentials.getCredentialsId(), soilNpkSensor.getNitrogen());
        tbRestClient.pushTelemetry(deviceCredentials.getCredentialsId(), soilNpkSensor.getPotassium());
        tbRestClient.pushTelemetry(deviceCredentials.getCredentialsId(), soilNpkSensor.getPhosphorus());

        this.soilNpkSensorToIdMap.put(soilNpkSensor, device.getUuidId());
        return device;
    }

    private Device createSoilWarmMoistureSensor(SoilWarmMoistureSensor soilWarmMoistureSensor, UUID ownerId, UUID deviceGroupId, boolean strictGeneration) {
        String name = soilWarmMoistureSensor.getSystemName();
        String entityType = soilWarmMoistureSensor.entityType();
        final Set<Attribute<?>> attributes = Set.of(
                new Attribute<>("from_greenhouse", soilWarmMoistureSensor.getFromGreenhouse())
        );

        Device device;
        if (tbRestClient.isPe()) {
            device = strictGeneration
                    ? tbRestClient.createDevice(name, entityType, new CustomerId(ownerId), attributes)
                    : tbRestClient.createDeviceIfNotExists(name, entityType, new CustomerId(ownerId), attributes);
            tbRestClient.addEntitiesToTheGroup(deviceGroupId, Set.of(device.getUuidId()));
        } else {
            device = strictGeneration
                    ? tbRestClient.createDevice(name, entityType, attributes)
                    : tbRestClient.createDeviceIfNotExists(name, entityType, attributes)
            ;
            tbRestClient.assignDeviceToCustomer(ownerId, device.getUuidId());
        }

        final var deviceCredentials = tbRestClient.getDeviceCredentials(device.getUuidId());

        tbRestClient.pushTelemetry(deviceCredentials.getCredentialsId(), soilWarmMoistureSensor.getTemperature());
        tbRestClient.pushTelemetry(deviceCredentials.getCredentialsId(), soilWarmMoistureSensor.getMoisture());

        this.soilWarmMoistureSensorToIdMap.put(soilWarmMoistureSensor, device.getUuidId());
        return device;
    }

    private Device createSoilAciditySensor(SoilAciditySensor soilAciditySensor, UUID ownerId, UUID deviceGroupId, boolean strictGeneration) {
        String name = soilAciditySensor.getSystemName();
        String entityType = soilAciditySensor.entityType();
        final Set<Attribute<?>> attributes = Set.of(
                new Attribute<>("from_greenhouse", soilAciditySensor.getFromGreenhouse())
        );

        Device device;
        if (tbRestClient.isPe()) {
            device = strictGeneration
                    ? tbRestClient.createDevice(name, entityType, new CustomerId(ownerId), attributes)
                    : tbRestClient.createDeviceIfNotExists(name, entityType, new CustomerId(ownerId), attributes);
            tbRestClient.addEntitiesToTheGroup(deviceGroupId, Set.of(device.getUuidId()));
        } else {
            device = strictGeneration
                    ? tbRestClient.createDevice(name, entityType, attributes)
                    : tbRestClient.createDeviceIfNotExists(name, entityType, attributes)
            ;
            tbRestClient.assignDeviceToCustomer(ownerId, device.getUuidId());
        }

        final var deviceCredentials = tbRestClient.getDeviceCredentials(device.getUuidId());

        tbRestClient.pushTelemetry(deviceCredentials.getCredentialsId(), soilAciditySensor.getAcidity());

        this.soilAciditySensorToIdMap.put(soilAciditySensor, device.getUuidId());
        return device;
    }

    private Device createInsideAirWarmHumiditySensor(InsideAirWarmHumiditySensor insideAirWarmHumiditySensor, UUID ownerId, UUID deviceGroupId, boolean strictGeneration) {
        String name = insideAirWarmHumiditySensor.getSystemName();
        String entityType = insideAirWarmHumiditySensor.entityType();
        final Set<Attribute<?>> attributes = Set.of(
                new Attribute<>("from_greenhouse", insideAirWarmHumiditySensor.getFromGreenhouse())
        );

        Device device;
        if (tbRestClient.isPe()) {
            device = strictGeneration
                    ? tbRestClient.createDevice(name, entityType, new CustomerId(ownerId), attributes)
                    : tbRestClient.createDeviceIfNotExists(name, entityType, new CustomerId(ownerId), attributes);
            tbRestClient.addEntitiesToTheGroup(deviceGroupId, Set.of(device.getUuidId()));
        } else {
            device = strictGeneration
                    ? tbRestClient.createDevice(name, entityType, attributes)
                    : tbRestClient.createDeviceIfNotExists(name, entityType, attributes)
            ;
            tbRestClient.assignDeviceToCustomer(ownerId, device.getUuidId());
        }
        DeviceCredentials deviceCredentials = tbRestClient.getDeviceCredentials(device.getUuidId());

        if (strictGeneration) {
            tbRestClient.setEntityAttributes(device.getUuidId(), EntityType.DEVICE, Attribute.Scope.SERVER_SCOPE, attributes);
        }

        tbRestClient.pushTelemetry(deviceCredentials.getCredentialsId(), insideAirWarmHumiditySensor.getTemperatureIn());
        tbRestClient.pushTelemetry(deviceCredentials.getCredentialsId(), insideAirWarmHumiditySensor.getHumidityIn());

        this.insideAirWarmHumiditySensorToIdMap.put(insideAirWarmHumiditySensor, device.getUuidId());
        return device;
    }

    private Device createInsideCO2Sensor(InsideCO2Sensor insideCO2Sensor, UUID ownerId, UUID deviceGroupId, boolean strictGeneration) {
        String name = insideCO2Sensor.getSystemName();
        String entityType = insideCO2Sensor.entityType();
        final Set<Attribute<?>> attributes = Set.of(
                new Attribute<>("from_greenhouse", insideCO2Sensor.getFromGreenhouse())
        );

        Device device;
        if (tbRestClient.isPe()) {
            device = strictGeneration
                    ? tbRestClient.createDevice(name, entityType, new CustomerId(ownerId), attributes)
                    : tbRestClient.createDeviceIfNotExists(name, entityType, new CustomerId(ownerId), attributes);
            tbRestClient.addEntitiesToTheGroup(deviceGroupId, Set.of(device.getUuidId()));
        } else {
            device = strictGeneration
                    ? tbRestClient.createDevice(name, entityType, attributes)
                    : tbRestClient.createDeviceIfNotExists(name, entityType, attributes);
            tbRestClient.assignDeviceToCustomer(ownerId, device.getUuidId());
        }

        final var deviceCredentials = tbRestClient.getDeviceCredentials(device.getUuidId());

        tbRestClient.pushTelemetry(deviceCredentials.getCredentialsId(), insideCO2Sensor.getConcentration());

        this.insideCO2SensorToIdMap.put(insideCO2Sensor, device.getUuidId());
        return device;
    }

    private Device createInsideLightSensor(InsideLightSensor insideLightSensor, UUID ownerId, UUID deviceGroupId, boolean strictGeneration) {
        String name = insideLightSensor.getSystemName();
        String entityType = insideLightSensor.entityType();
        final Set<Attribute<?>> attributes = Set.of(
                new Attribute<>("from_greenhouse", insideLightSensor.getFromGreenhouse())
        );

        Device device;
        if (tbRestClient.isPe()) {
            device = strictGeneration
                    ? tbRestClient.createDevice(name, entityType, new CustomerId(ownerId), attributes)
                    : tbRestClient.createDeviceIfNotExists(name, entityType, new CustomerId(ownerId), attributes);
            tbRestClient.addEntitiesToTheGroup(deviceGroupId, Set.of(device.getUuidId()));
        } else {
            device = strictGeneration
                    ? tbRestClient.createDevice(name, entityType, attributes)
                    : tbRestClient.createDeviceIfNotExists(name, entityType, attributes);
            tbRestClient.assignDeviceToCustomer(ownerId, device.getUuidId());
        }

        final var deviceCredentials = tbRestClient.getDeviceCredentials(device.getUuidId());

        tbRestClient.pushTelemetry(deviceCredentials.getCredentialsId(), insideLightSensor.getLightIn());

        this.insideLightSensorToIdMap.put(insideLightSensor, device.getUuidId());
        return device;
    }

    private Device createHarvestReporter(HarvestReporter harvestReporter, UUID ownerId, UUID deviceGroupId, boolean strictGeneration) {
        String name = harvestReporter.getSystemName();
        String entityType = harvestReporter.entityType();
        final Set<Attribute<?>> attributes = Set.of(
                new Attribute<>("from_greenhouse", harvestReporter.getFromGreenhouse())
        );

        Device device;
        if (tbRestClient.isPe()) {
            device = strictGeneration
                    ? tbRestClient.createDevice(name, entityType, new CustomerId(ownerId), attributes)
                    : tbRestClient.createDeviceIfNotExists(name, entityType, new CustomerId(ownerId), attributes);
            tbRestClient.addEntitiesToTheGroup(deviceGroupId, Set.of(device.getUuidId()));
        } else {
            device = strictGeneration
                    ? tbRestClient.createDevice(name, entityType, attributes)
                    : tbRestClient.createDeviceIfNotExists(name, entityType, attributes);
            tbRestClient.assignDeviceToCustomer(ownerId, device.getUuidId());
        }

        final var deviceCredentials = tbRestClient.getDeviceCredentials(device.getUuidId());

        tbRestClient.pushTelemetry(deviceCredentials.getCredentialsId(), harvestReporter.getCropWeight());
        tbRestClient.pushTelemetry(deviceCredentials.getCredentialsId(), harvestReporter.getWorkerInCharge());

        this.harvestReporterToIdMap.put(harvestReporter, device.getUuidId());
        return device;
    }

    private Device createEnergyMeter(EnergyMeter energyMeter, UUID ownerId, UUID deviceGroupId, boolean strictGeneration) {
        String name = energyMeter.getSystemName();
        String entityType = energyMeter.entityType();
        final Set<Attribute<?>> attributes = Set.of(
                new Attribute<>("from_greenhouse", energyMeter.getFromGreenhouse())
        );

        Device device;
        if (tbRestClient.isPe()) {
            device = strictGeneration
                    ? tbRestClient.createDevice(name, entityType, new CustomerId(ownerId), attributes)
                    : tbRestClient.createDeviceIfNotExists(name, entityType, new CustomerId(ownerId), attributes);
            tbRestClient.addEntitiesToTheGroup(deviceGroupId, Set.of(device.getUuidId()));
        } else {
            device = strictGeneration
                    ? tbRestClient.createDevice(name, entityType, attributes)
                    : tbRestClient.createDeviceIfNotExists(name, entityType, attributes);
            tbRestClient.assignDeviceToCustomer(ownerId, device.getUuidId());
        }

        final var deviceCredentials = tbRestClient.getDeviceCredentials(device.getUuidId());

        tbRestClient.pushTelemetry(deviceCredentials.getCredentialsId(), energyMeter.getEnergyConsumptionLight());
        tbRestClient.pushTelemetry(deviceCredentials.getCredentialsId(), energyMeter.getEnergyConsumptionHeating());
        tbRestClient.pushTelemetry(deviceCredentials.getCredentialsId(), energyMeter.getEnergyConsumptionCooling());
        tbRestClient.pushTelemetry(deviceCredentials.getCredentialsId(), energyMeter.getEnergyConsumptionAirControl());
        tbRestClient.pushTelemetry(deviceCredentials.getCredentialsId(), energyMeter.getEnergyConsumptionIrrigation());

        this.energyMeterToIdMap.put(energyMeter, device.getUuidId());
        return device;
    }

    private Device createWaterMeter(WaterMeter waterMeter, UUID ownerId, UUID deviceGroupId, boolean strictGeneration) {
        String name = waterMeter.getSystemName();
        String entityType = waterMeter.entityType();
        final Set<Attribute<?>> attributes = Set.of(
                new Attribute<>("from_greenhouse", waterMeter.getFromGreenhouse())
        );

        Device device;
        if (tbRestClient.isPe()) {
            device = strictGeneration
                    ? tbRestClient.createDevice(name, entityType, new CustomerId(ownerId), attributes)
                    : tbRestClient.createDeviceIfNotExists(name, entityType, new CustomerId(ownerId), attributes);
            tbRestClient.addEntitiesToTheGroup(deviceGroupId, Set.of(device.getUuidId()));
        } else {
            device = strictGeneration
                    ? tbRestClient.createDevice(name, entityType, attributes)
                    : tbRestClient.createDeviceIfNotExists(name, entityType, attributes);
            tbRestClient.assignDeviceToCustomer(ownerId, device.getUuidId());
        }

        final var deviceCredentials = tbRestClient.getDeviceCredentials(device.getUuidId());

        tbRestClient.pushTelemetry(deviceCredentials.getCredentialsId(), waterMeter.getConsumptionWater());

        this.waterMeterToIdMap.put(waterMeter, device.getUuidId());
        return device;
    }

    private Device createOutsideAirWarmHumiditySensor(OutsideAirWarmHumiditySensor outsideAirWarmHumiditySensor, UUID ownerId, UUID deviceGroupId, boolean strictGeneration) {
        String name = outsideAirWarmHumiditySensor.getSystemName();
        String entityType = outsideAirWarmHumiditySensor.entityType();
        final Set<Attribute<?>> attributes = Set.of(
                new Attribute<>("from_greenhouse", outsideAirWarmHumiditySensor.getFromGreenhouse())
        );

        Device device;
        if (tbRestClient.isPe()) {
            device = strictGeneration
                    ? tbRestClient.createDevice(name, entityType, new CustomerId(ownerId), attributes)
                    : tbRestClient.createDeviceIfNotExists(name, entityType, new CustomerId(ownerId), attributes);
            tbRestClient.addEntitiesToTheGroup(deviceGroupId, Set.of(device.getUuidId()));
        } else {
            device = strictGeneration
                    ? tbRestClient.createDevice(name, entityType, attributes)
                    : tbRestClient.createDeviceIfNotExists(name, entityType, attributes);
            tbRestClient.assignDeviceToCustomer(ownerId, device.getUuidId());
        }

        final var deviceCredentials = tbRestClient.getDeviceCredentials(device.getUuidId());
        tbRestClient.pushTelemetry(deviceCredentials.getCredentialsId(), outsideAirWarmHumiditySensor.getTemperatureOut());
        tbRestClient.pushTelemetry(deviceCredentials.getCredentialsId(), outsideAirWarmHumiditySensor.getHumidityOut());

        this.outsideAirWarmHumiditySensorToIdMap.put(outsideAirWarmHumiditySensor, device.getUuidId());
        return device;
    }

    private Device createOutsideLightSensor(OutsideLightSensor outsideLightSensor, UUID ownerId, UUID deviceGroupId, boolean strictGeneration) {
        String name = outsideLightSensor.getSystemName();
        String entityType = outsideLightSensor.entityType();
        final Set<Attribute<?>> attributes = Set.of(
                new Attribute<>("from_greenhouse", outsideLightSensor.getFromGreenhouse())
        );

        Device device;
        if (tbRestClient.isPe()) {
            device = strictGeneration
                    ? tbRestClient.createDevice(name, entityType, new CustomerId(ownerId), attributes)
                    : tbRestClient.createDeviceIfNotExists(name, entityType, new CustomerId(ownerId), attributes);
            tbRestClient.addEntitiesToTheGroup(deviceGroupId, Set.of(device.getUuidId()));
        } else {
            device = strictGeneration
                    ? tbRestClient.createDevice(name, entityType, attributes)
                    : tbRestClient.createDeviceIfNotExists(name, entityType, attributes);
            tbRestClient.assignDeviceToCustomer(ownerId, device.getUuidId());
        }

        final var deviceCredentials = tbRestClient.getDeviceCredentials(device.getUuidId());

        tbRestClient.pushTelemetry(deviceCredentials.getCredentialsId(), outsideLightSensor.getLightOut());

        this.outsideLightSensorToIdMap.put(outsideLightSensor, device.getUuidId());
        return device;
    }


    private double getNodePositionX(int greenhouseCounter, double x, double y) {
        return 200 + RuleNodeAdditionalInfo.CELL_SIZE * 10 * x;
    }

    private double getNodePositionY(int greenhouseCounter, double x, double y) {
        return 250 + greenhouseCounter * RuleNodeAdditionalInfo.CELL_SIZE * 85 + RuleNodeAdditionalInfo.CELL_SIZE * 3 * y;
    }

    private String getGreenhouseGeneratorCode(double latitude, double longitude, String apiId) throws IOException {
        String fileContent = this.fileService.getFileContent(getSolutionName(), "greenhouse_generator.js");
        fileContent = fileContent.replace("PUT_LATITUDE", String.valueOf(latitude));
        fileContent = fileContent.replace("PUT_LONGITUDE", String.valueOf(longitude));
        fileContent = fileContent.replace("PUT_API_ID", apiId);
        return fileContent;
    }

    private Pair<Long, Long> calculateNewDateRange(long from, long to, long startGenerationTime, long endGenerationTime, boolean fullTelemetryGeneration)
            throws IllegalStateException {
        long newfromMs = from;
        long newToMs = to;

        if (!fullTelemetryGeneration) {
            var fromEndPair = DateTimeUtils.getDatesIntersection(newfromMs, newToMs, startGenerationTime, endGenerationTime);
            newfromMs = fromEndPair.getLeft();
            newToMs = fromEndPair.getRight();
        } else {
            newfromMs = startGenerationTime;
            newToMs = endGenerationTime;
        }

        return Pair.of(newfromMs, newToMs);
    }
}

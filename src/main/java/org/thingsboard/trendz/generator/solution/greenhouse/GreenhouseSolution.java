package org.thingsboard.trendz.generator.solution.greenhouse;

import lombok.extern.slf4j.Slf4j;
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
import org.thingsboard.trendz.generator.model.tb.Telemetry;
import org.thingsboard.trendz.generator.model.tb.Timestamp;
import org.thingsboard.trendz.generator.service.anomaly.AnomalyService;
import org.thingsboard.trendz.generator.service.dashboard.DashboardService;
import org.thingsboard.trendz.generator.service.rest.TbRestClient;
import org.thingsboard.trendz.generator.service.roolchain.RuleChainBuildingService;
import org.thingsboard.trendz.generator.solution.SolutionTemplateGenerator;
import org.thingsboard.trendz.generator.solution.greenhouse.configuration.GreenhouseConfiguration;
import org.thingsboard.trendz.generator.solution.greenhouse.configuration.StationCity;
import org.thingsboard.trendz.generator.solution.greenhouse.model.*;
import org.thingsboard.trendz.generator.utils.MySortedSet;

import java.time.ZonedDateTime;
import java.util.ArrayList;
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

    private final TbRestClient tbRestClient;
    private final AnomalyService anomalyService;
    private final RuleChainBuildingService ruleChainBuildingService;
    private final DashboardService dashboardService;

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

            for (Greenhouse greenhouse : greenhouses) {

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
                        .name("Greenhouse in Kyiv")
                        .stationCity(StationCity.KYIV)
                        .address("Svyatoshyns'ka St, 34 к, Kyiv, 02000")
                        .latitude(50.446603)
                        .longitude(30.386447)
                        .plantType(PlantType.TOMATO)
                        .variety("Sweet Spanish")
                        .sectionHeight(5)
                        .sectionWidth(10)
                        .build(),

                GreenhouseConfiguration.builder()
                        .order(2)
                        .name("Greenhouse in Krakow")
                        .stationCity(StationCity.KRAKOW)
                        .address("Zielona 18, 32-087 Bibice, Poland")
                        .latitude(50.121765)
                        .longitude(19.946134)
                        .plantType(PlantType.CUCUMBER)
                        .variety("English")
                        .sectionHeight(5)
                        .sectionWidth(7)
                        .build(),

                GreenhouseConfiguration.builder()
                        .order(3)
                        .name("Greenhouse in Warszawa")
                        .stationCity(StationCity.WARSZAWA)
                        .address("Ojca Aniceta 28, 03-264 Warszawa, Poland")
                        .latitude(52.306237)
                        .longitude(21.039917)
                        .plantType(PlantType.ONION)
                        .variety("Sweet Spanish")
                        .sectionHeight(10)
                        .sectionWidth(15)
                        .build(),

                GreenhouseConfiguration.builder()
                        .order(4)
                        .name("Greenhouse in Stuttgart")
                        .stationCity(StationCity.STUTTGART)
                        .address("Augsburger Str. 500, 70327 Stuttgart, Germany")
                        .latitude(48.774252)
                        .longitude(9.259500)
                        .plantType(PlantType.TOMATO)
                        .variety("Cherry")
                        .sectionHeight(5)
                        .sectionWidth(5)
                        .build()
        );


        Set<ModelEntity> entities = MySortedSet.of();
        entities.addAll(plants);
        entities.addAll(
                greenhouseConfigurations
                        .stream()
                        .map(configuration -> makeGreenhouseByConfiguration(configuration, startYear, skipTelemetry))
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


    private Greenhouse makeGreenhouseByConfiguration(GreenhouseConfiguration configuration, ZonedDateTime startYear, boolean skipTelemetry) {
        Set<Section> sections = new TreeSet<>();
        for (int height = 1; height <= configuration.getSectionHeight(); height++) {
            for (int width = 1; width <= configuration.getSectionWidth(); width++) {

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

                SoilNpkSensor soilNpkSensor = SoilNpkSensor.builder()
                        .systemName("Soil NPK Sensor: " + configuration.getName() + ", " + String.format("%s-%s", height, width))
                        .systemLabel("")
                        .nitrogen(new Telemetry<>("nitrogen", MySortedSet.of(new Telemetry.Point<>(Timestamp.of(0), 0))))
                        .phosphorus(new Telemetry<>("phosphorus", MySortedSet.of(new Telemetry.Point<>(Timestamp.of(0), 0))))
                        .potassium(new Telemetry<>("potassium", MySortedSet.of(new Telemetry.Point<>(Timestamp.of(0), 0))))
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
                        .soilWarmMoistureSensor(soilWarmMoistureSensor)
                        .soilAciditySensor(soilAciditySensor)
                        .soilNpkSensor(soilNpkSensor)
                        .harvestReporter(harvestReporter)
                        .build();

                sections.add(section);
            }
        }

        InsideAirWarmHumiditySensor insideAirWarmHumiditySensor = InsideAirWarmHumiditySensor.builder()
                .systemName(configuration.getName() + ": Air Warm-Moisture Sensor (Inside)")
                .systemLabel("")
                .temperature(new Telemetry<>("temperature", MySortedSet.of(new Telemetry.Point<>(Timestamp.of(0), 0))))
                .humidity(new Telemetry<>("humidity", MySortedSet.of(new Telemetry.Point<>(Timestamp.of(0), 0))))
                .build();

        InsideLightSensor insideLightSensor = InsideLightSensor.builder()
                .systemName(configuration.getName() + ": Light Sensor (Inside)")
                .systemLabel("")
                .light(new Telemetry<>("light", MySortedSet.of(new Telemetry.Point<>(Timestamp.of(0), 0))))
                .build();

        InsideCO2Sensor insideCO2Sensor = InsideCO2Sensor.builder()
                .systemName(configuration.getName() + ": CO2 Sensor (Inside)")
                .systemLabel("")
                .concentration(new Telemetry<>("concentration", MySortedSet.of(new Telemetry.Point<>(Timestamp.of(0), 0))))
                .build();

        OutsideAirWarmHumiditySensor outsideAirWarmHumiditySensor = OutsideAirWarmHumiditySensor.builder()
                .systemName(configuration.getName() + ": Air Warm-Moisture Sensor (Outside)")
                .systemLabel("")
                .temperature(new Telemetry<>("temperature", MySortedSet.of(new Telemetry.Point<>(Timestamp.of(0), 0))))
                .humidity(new Telemetry<>("humidity", MySortedSet.of(new Telemetry.Point<>(Timestamp.of(0), 0))))
                .build();

        OutsideLightSensor outsideLightSensor = OutsideLightSensor.builder()
                .systemName(configuration.getName() + ": Light Sensor (Outside)")
                .systemLabel("")
                .light(new Telemetry<>("light", MySortedSet.of(new Telemetry.Point<>(Timestamp.of(0), 0))))
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
        tbRestClient.pushTelemetry(deviceCredentials.getCredentialsId(), soilNpkSensor.getPotassium());

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
}

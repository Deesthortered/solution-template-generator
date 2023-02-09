package org.thingsboard.trendz.generator.solution.greenhouse;

import lombok.extern.slf4j.Slf4j;
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
import org.thingsboard.trendz.generator.model.tb.Attribute;
import org.thingsboard.trendz.generator.model.tb.CustomerData;
import org.thingsboard.trendz.generator.model.tb.CustomerUser;
import org.thingsboard.trendz.generator.service.anomaly.AnomalyService;
import org.thingsboard.trendz.generator.service.dashboard.DashboardService;
import org.thingsboard.trendz.generator.service.rest.TbRestClient;
import org.thingsboard.trendz.generator.service.roolchain.RuleChainBuildingService;
import org.thingsboard.trendz.generator.solution.SolutionTemplateGenerator;
import org.thingsboard.trendz.generator.solution.greenhouse.model.*;
import org.thingsboard.trendz.generator.utils.MySortedSet;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
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

    private final Map<SoilNpkSensor, UUID> soilNpkSensorToIdMap = new HashMap<>();
    private final Map<SoilWarmMoistureSensor, UUID> soilWarmMoistureSensorToIdMap = new HashMap<>();
    private final Map<SoilAciditySensor, UUID> soilAciditySensorToIdMap = new HashMap<>();
    private final Map<InsideAirWarmMoistureSensor, UUID> insideAirWarmMoistureSensorToIdMap = new HashMap<>();
    private final Map<InsideCO2Sensor, UUID> insideCO2SensorToIdMap = new HashMap<>();
    private final Map<InsideLightSensor, UUID> insideLightSensorToIdMap = new HashMap<>();
    private final Map<HarvestReporter, UUID> harvestReporterToIdMap = new HashMap<>();
    private final Map<EnergyMeter, UUID> energyMeterToIdMap = new HashMap<>();
    private final Map<WaterMeter, UUID> waterMeterToIdMap = new HashMap<>();
    private final Map<OutsideAirWarmMoistureSensor, UUID> outsideAirWarmMoistureSensorToIdMap = new HashMap<>();
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
                .map(modelEntity -> (Greenhouse) modelEntity)
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

        return ModelData.builder()
                .data(MySortedSet.of())
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

        Set<Greenhouse> greenhouses = mapToGreenhouses(data);
        for (Greenhouse greenhouse : greenhouses) {

        }
    }

    private void validateData(ModelData data) {
        Set<Greenhouse> greenhouses = mapToGreenhouses(data);



        Set<String> assets = new HashSet<Greenhouse>() // Sets.union()
                .stream()
                .map(ModelEntity::getSystemName)
                .collect(Collectors.toSet());

        Set<String> devices = new HashSet<Greenhouse>() // Sets.union()
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
        Set<Greenhouse> greenhouses = mapToGreenhouses(data);




        Set<String> assets = new HashSet<Greenhouse>() // Sets.union()
                .stream()
                .map(ModelEntity::getSystemName)
                .collect(Collectors.toSet());

        Set<String> devices = new HashSet<Greenhouse>() //  Sets.union()
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

    private Device createInsideAirWarmMoistureSensor(InsideAirWarmMoistureSensor insideAirWarmMoistureSensor, UUID ownerId, UUID deviceGroupId) {
        String name = insideAirWarmMoistureSensor.getSystemName();
        String entityType = insideAirWarmMoistureSensor.entityType();

        Device device;
        if (tbRestClient.isPe()) {
            device = tbRestClient.createDevice(name, entityType, new CustomerId(ownerId));
            tbRestClient.addEntitiesToTheGroup(deviceGroupId, Set.of(device.getUuidId()));
        } else {
            device = tbRestClient.createDevice(name, entityType);
            tbRestClient.assignDeviceToCustomer(ownerId, device.getUuidId());
        }
        DeviceCredentials deviceCredentials = tbRestClient.getDeviceCredentials(device.getUuidId());


        tbRestClient.pushTelemetry(deviceCredentials.getCredentialsId(), insideAirWarmMoistureSensor.getTemperature());
        tbRestClient.pushTelemetry(deviceCredentials.getCredentialsId(), insideAirWarmMoistureSensor.getMoisture());

        this.insideAirWarmMoistureSensorToIdMap.put(insideAirWarmMoistureSensor, device.getUuidId());
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

    private Device createOutsideAirWarmMoistureSensor(OutsideAirWarmMoistureSensor outsideAirWarmMoistureSensor, UUID ownerId, UUID deviceGroupId) {
        String name = outsideAirWarmMoistureSensor.getSystemName();
        String entityType = outsideAirWarmMoistureSensor.entityType();

        Device device;
        if (tbRestClient.isPe()) {
            device = tbRestClient.createDevice(name, entityType, new CustomerId(ownerId));
            tbRestClient.addEntitiesToTheGroup(deviceGroupId, Set.of(device.getUuidId()));
        } else {
            device = tbRestClient.createDevice(name, entityType);
            tbRestClient.assignDeviceToCustomer(ownerId, device.getUuidId());
        }
        DeviceCredentials deviceCredentials = tbRestClient.getDeviceCredentials(device.getUuidId());


        tbRestClient.pushTelemetry(deviceCredentials.getCredentialsId(), outsideAirWarmMoistureSensor.getTemperature());
        tbRestClient.pushTelemetry(deviceCredentials.getCredentialsId(), outsideAirWarmMoistureSensor.getMoisture());

        this.outsideAirWarmMoistureSensorToIdMap.put(outsideAirWarmMoistureSensor, device.getUuidId());
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

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
import org.thingsboard.trendz.generator.model.tb.Telemetry;
import org.thingsboard.trendz.generator.model.tb.Timestamp;
import org.thingsboard.trendz.generator.service.FileService;
import org.thingsboard.trendz.generator.service.VisualizationService;
import org.thingsboard.trendz.generator.service.anomaly.AnomalyService;
import org.thingsboard.trendz.generator.service.rest.TbRestClient;
import org.thingsboard.trendz.generator.solution.SolutionTemplateGenerator;
import org.thingsboard.trendz.generator.solution.watermetering.configuration.CityConfiguration;
import org.thingsboard.trendz.generator.solution.watermetering.configuration.ConsumerConfiguration;
import org.thingsboard.trendz.generator.solution.watermetering.configuration.RegionConfiguration;
import org.thingsboard.trendz.generator.solution.watermetering.model.City;
import org.thingsboard.trendz.generator.solution.watermetering.model.Consumer;
import org.thingsboard.trendz.generator.solution.watermetering.model.ConsumerType;
import org.thingsboard.trendz.generator.solution.watermetering.model.PumpStation;
import org.thingsboard.trendz.generator.solution.watermetering.model.Region;
import org.thingsboard.trendz.generator.utils.DateTimeUtils;
import org.thingsboard.trendz.generator.utils.RandomUtils;

import java.time.DayOfWeek;
import java.time.Month;
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

                    for (Consumer consumer : region.getConsumers()) {
                        UUID consumerId = this.consumerToIdMap.get(consumer);

                    }

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
        Set<CityConfiguration> cityConfigurations = Set.of(
                CityConfiguration.builder()
                        .name("London")
                        .label("Label for City London")
                        .population(4_000_000)
                        .regionConfigurations(Set.of(
                                RegionConfiguration.builder()
                                        .startYear(startYear)
                                        .name("Dulwich")
                                        .label("Label for Dulwich, London")
                                        .consumerConfigurations(Set.of(
                                                new ConsumerConfiguration("1", ConsumerType.HSH, Collections.emptySet()),
                                                new ConsumerConfiguration("2", ConsumerType.HSH, Collections.emptySet()),
                                                new ConsumerConfiguration("3", ConsumerType.HSH, Collections.emptySet()),
                                                new ConsumerConfiguration("4", ConsumerType.HSH, Collections.emptySet()),
                                                new ConsumerConfiguration("1", ConsumerType.GOV, Collections.emptySet()),
                                                new ConsumerConfiguration("1", ConsumerType.IND, Collections.emptySet())
                                        ))
                                        .build(),
                                RegionConfiguration.builder()
                                        .startYear(startYear)
                                        .name("Wimbledon")
                                        .label("Label for Wimbledon, London")
                                        .consumerConfigurations(Set.of(
                                                new ConsumerConfiguration("1", ConsumerType.GOV, Collections.emptySet()),
                                                new ConsumerConfiguration("1", ConsumerType.IND, Collections.emptySet())
                                        ))
                                        .build()
                        ))
                        .build(),

                CityConfiguration.builder()
                        .name("Edinburgh")
                        .label("Label for City Edinburgh")
                        .population(300_000)
                        .regionConfigurations(Set.of(
                                RegionConfiguration.builder()
                                        .startYear(startYear)
                                        .name("Leith")
                                        .label("Label for Leith, Edinburgh")
                                        .consumerConfigurations(Set.of(
                                                new ConsumerConfiguration("1", ConsumerType.HSH, Collections.emptySet()),
                                                new ConsumerConfiguration("1", ConsumerType.GOV, Collections.emptySet()),
                                                new ConsumerConfiguration("1", ConsumerType.IND, Collections.emptySet())
                                        ))
                                        .build(),
                                RegionConfiguration.builder()
                                        .startYear(startYear)
                                        .name("Stockbridge")
                                        .label("Label for Stockbridge, Edinburgh")
                                        .consumerConfigurations(Set.of(
                                                new ConsumerConfiguration("1", ConsumerType.HSH, Collections.emptySet()),
                                                new ConsumerConfiguration("1", ConsumerType.GOV, Collections.emptySet()),
                                                new ConsumerConfiguration("1", ConsumerType.IND, Collections.emptySet())
                                        ))
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


    private City makeCityByConfiguration(CityConfiguration cityConfiguration, boolean skipTelemetry) {
        Set<Region> regions = new TreeSet<>();
        Set<PumpStation> pumpStations = new TreeSet<>();
        for (RegionConfiguration regionConfiguration : cityConfiguration.getRegionConfigurations()) {
            Region region = makeRegionByConfiguration(regionConfiguration, skipTelemetry);
            regions.add(region);
        }
        for (Region region : regions) {
            PumpStation pumpStation = makePumpStationByRegion(region);
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
        Set<Consumer> consumers = new TreeSet<>();
        for (ConsumerConfiguration consumerConfiguration : regionConfiguration.getConsumerConfigurations()) {
            Consumer consumer = makeConsumerByConfiguration(regionConfiguration, consumerConfiguration, skipTelemetry);
            consumers.add(consumer);
        }

        Set<Telemetry<Long>> consumptionSet = consumers.stream()
                .map(Consumer::getConsumption)
                .collect(Collectors.toSet());

        Telemetry<Long> fullConsumption = createTelemetryRegionFullConsumption(consumptionSet, skipTelemetry);

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

    private PumpStation makePumpStationByRegion(Region region) {
        String name = region.getSystemName() + " Pump Station";
        Telemetry<Long> provided = new Telemetry<>("provided", region.getFullConsumption().getPoints());

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
        Set<DayOfWeek> weekEnd = Set.of(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY);

        long now = System.currentTimeMillis();
        ZonedDateTime startDate = startYear.truncatedTo(ChronoUnit.HOURS);
        ZonedDateTime nowDate = DateTimeUtils.fromTs(now).truncatedTo(ChronoUnit.HOURS);
        ZonedDateTime iteratedDate = startDate;
        while (iteratedDate.isBefore(nowDate)) {
            long iteratedTs = DateTimeUtils.toTs(iteratedDate);
            int hour = iteratedDate.getHour();
            DayOfWeek dayOfWeek = iteratedDate.getDayOfWeek();
            int dayOfYear = iteratedDate.getDayOfYear();
            Month month = iteratedDate.getMonth();
            long dailyNoise = RandomUtils.getRandomNumber(-20, 20);

            long consumption;
            if (weekEnd.contains(dayOfWeek)) {
                consumption = getHourConsumerConsumption(type, hour);
            } else {
                consumption = 10;
            }
            consumption += getModificationByMonth(month);
            consumption += getModificationByDayOfYear(dayOfYear);

            long value = consumption + dailyNoise;

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

    private long getHourConsumerConsumption(ConsumerType type, int hour) {
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
            case 2:
            case 3:
                return 10;
            case 4:
                return 50;
            case 5:
                return 100;
            case 6:
            case 7:
                return 150;
            case 8:
            case 9:
                return 100;
            case 10:
                return 50;
            case 11:
            case 12:
            case 13:
            case 14:
            case 15:
                return 10;
            case 16:
                return 30;
            case 17:
            case 18:
            case 19:
                return 50;
            case 20:
                return 150;
            case 21:
                return 100;
            case 22:
                return 40;
            case 23:
                return 10;
            default:
                throw new IllegalArgumentException("Unsupported hour = " + hour);
        }
    }

    private long getHourGovConsumption(int hour) {
        switch (hour) {
            case 0:
            case 1:
            case 2:
                return 50;
            case 3:
            case 4:
            case 5:
                return 60;
            case 6:
                return 70;
            case 7:
                return 80;
            case 8:
                return 100;
            case 9:
            case 10:
            case 11:
            case 12:
            case 13:
            case 14:
            case 15:
            case 16:
                return 120;
            case 17:
                return 100;
            case 18:
            case 19:
                return 80;
            case 20:
            case 21:
            case 22:
            case 23:
                return 50;
            default:
                throw new IllegalArgumentException("Unsupported hour = " + hour);
        }
    }

    private long getHourIndConsumption(int hour) {
        // 100L, 100L, 100L, 100L, 200L, 200L, 500L, 400L, 400L, 400L, 450L, 500L, 600L, 550L, 550L, 400L, 300L, 100L, 100L, 100L, 100L, 100L, 100L, 100L

        switch (hour) {
            case 0:
            case 1:
            case 2:
            case 3:
                return 100;
            case 4:
            case 5:
                return 200;
            case 6:
                return 500;
            case 7:
            case 8:
            case 9:
                return 400;
            case 10:
                return 450;
            case 11:
                return 500;
            case 12:
                return 600;
            case 13:
            case 14:
                return 550;
            case 15:
                return 400;
            case 16:
                return 300;
            case 17:
            case 18:
            case 19:
            case 20:
            case 21:
            case 22:
            case 23:
                return 100;
            default:
                throw new IllegalArgumentException("Unsupported hour = " + hour);
        }
    }

    private long getModificationByMonth(Month month) {
        long value = 20;
        switch (month) {
            case JANUARY:
            case FEBRUARY:
                return 0;
            case MARCH:
            case APRIL:
            case MAY:
                return value;
            case JUNE:
            case JULY:
            case AUGUST:
                return 0;
            case SEPTEMBER:
            case OCTOBER:
            case NOVEMBER:
                return value;
            case DECEMBER:
                return 0;
            default: throw new IllegalArgumentException("Unsupported month: " + month);
        }
    }

    private long getModificationByDayOfYear(int dayOfYear) {
        return (dayOfYear * 24L) / 500 + RandomUtils.getRandomNumber(-5, 5);
    }
}

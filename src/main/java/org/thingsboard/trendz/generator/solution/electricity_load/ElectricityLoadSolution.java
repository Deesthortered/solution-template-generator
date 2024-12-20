package org.thingsboard.trendz.generator.solution.electricity_load;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.thingsboard.server.common.data.Customer;
import org.thingsboard.server.common.data.Device;
import org.thingsboard.server.common.data.EntityType;
import org.thingsboard.server.common.data.group.EntityGroup;
import org.thingsboard.server.common.data.id.CustomerId;
import org.thingsboard.server.common.data.security.DeviceCredentials;
import org.thingsboard.trendz.generator.exception.CustomerAlreadyExistException;
import org.thingsboard.trendz.generator.exception.DeviceAlreadyExistException;
import org.thingsboard.trendz.generator.exception.SolutionValidationException;
import org.thingsboard.trendz.generator.model.ModelData;
import org.thingsboard.trendz.generator.model.ModelEntity;
import org.thingsboard.trendz.generator.model.anomaly.AnomalyInfo;
import org.thingsboard.trendz.generator.model.tb.CustomerData;
import org.thingsboard.trendz.generator.model.tb.CustomerUser;
import org.thingsboard.trendz.generator.model.tb.Telemetry;
import org.thingsboard.trendz.generator.model.tb.Timestamp;
import org.thingsboard.trendz.generator.service.FileService;
import org.thingsboard.trendz.generator.service.anomaly.AnomalyService;
import org.thingsboard.trendz.generator.service.rest.TbRestClient;
import org.thingsboard.trendz.generator.solution.SolutionTemplateGenerator;
import org.thingsboard.trendz.generator.solution.electricity_load.model.ElectricityLoadDiagrams20112014;
import org.thingsboard.trendz.generator.utils.DateTimeUtils;
import org.thingsboard.trendz.generator.utils.MySortedSet;

import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
@Service
public class ElectricityLoadSolution implements SolutionTemplateGenerator {

    private static final String CUSTOMER_TITLE = "Electricity Load Customer";
    private static final String CUSTOMER_USER_EMAIL = "electricity.load@thingsboard.io";
    private static final String CUSTOMER_USER_PASSWORD = "password";
    private static final String CUSTOMER_USER_FIRST_NAME = "Electricity Load Solution";
    private static final String CUSTOMER_USER_LAST_NAME = "";

    private static final String ASSET_GROUP_NAME = "Electricity Load Asset Group";
    private static final String DEVICE_GROUP_NAME = "Electricity Load Device Group";

    private static final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final String DATE_FIELD = "Date";
    private static final int DEVICE_LIMIT = 10;

    private final TbRestClient tbRestClient;
    private final FileService fileService;
    private final AnomalyService anomalyService;

    @Autowired
    public ElectricityLoadSolution(
            TbRestClient tbRestClient,
            FileService fileService,
            AnomalyService anomalyService
    ) {
        this.tbRestClient = tbRestClient;
        this.fileService = fileService;
        this.anomalyService = anomalyService;
    }


    @Override
    public String getSolutionName() {
        return "ElectricityLoad";
    }

    @Override
    public void validate() throws SolutionValidationException {
        try {
            log.info("Electricity Load Solution - start validation");

            validateCustomerData();

            if (!tbRestClient.isPe()) {
                ModelData data = makeData(true, 0L, 0L);
                validateData(data);
            }

            log.info("Electricity Load Solution - validation is completed!");
        } catch (Exception e) {
            throw new SolutionValidationException(getSolutionName(), e);
        }
    }

    @Override
    public void generate(
            boolean skipTelemetry, boolean strictGeneration, boolean fullTelemetryGeneration,
            long startGenerationTime, long endGenerationTime
    ) {
        log.info("Electricity Load Solution - start generation");
        try {
            CustomerData customerData = createCustomerData(strictGeneration);
            ModelData data = makeData(skipTelemetry, startGenerationTime, endGenerationTime);
            applyData(data, customerData, strictGeneration);

            log.info("Electricity Load Solution - generation is completed!");
        } catch (Exception e) {
            log.error("Electricity Load Solution generate was failed, skipping...", e);
        }
    }

    @Override
    public void remove() {
        log.info("Electricity Load Solution - start removal");
        try {
            deleteCustomerData();

            if (!tbRestClient.isPe()) {
                ModelData data = makeData(true, 0L, 0L);
                deleteData(data);
            }

            log.info("Electricity Load Solution - removal is completed!");
        } catch (Exception e) {
            log.error("Electricity Load Solution removal was failed, skipping...", e);
        }
    }


    private CustomerData createCustomerData(boolean strictGeneration) {
        Customer customer = strictGeneration
                ? this.tbRestClient.createCustomer(CUSTOMER_TITLE)
                : this.tbRestClient.createCustomerIfNotExists(CUSTOMER_TITLE);
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


    private ModelData makeData(boolean skipTelemetry, long startGenerationTime, long endGenerationTime) {
        Path filePath = this.fileService.getFilePath(getSolutionName(), "LD2011_2014.txt");

        Map<String, Telemetry<Double>> telemetryMap;
        if (skipTelemetry) {
            Set<String> allDevicesNames = getAllDevicesNames(filePath);
            telemetryMap = allDevicesNames.stream()
                    .map(deviceName -> Map.entry(deviceName, new Telemetry<Double>("skip")))
                    .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        } else {
            log.info("Parsing telemetry...");
            telemetryMap = loadTelemetry(filePath, startGenerationTime, endGenerationTime);
            log.info("Telemetry was parsed");
        }

        Set<AnomalyInfo> anomalyInfoSet = MySortedSet.of();

        log.info("Preprocessing the telemetry");
        Set<ModelEntity> devices = telemetryMap.keySet().stream()
                .flatMap(deviceName -> {
                    Telemetry<Double> originalTelemetry = telemetryMap.get(deviceName);
                    this.anomalyService.applyAnomaly(originalTelemetry, anomalyInfoSet);

                    Telemetry<Double> fullTelemetry = originalTelemetry.getPoints().stream()
                            .map(point -> {
                                Timestamp oldTs = point.getTs();
                                Timestamp newTs = Timestamp.of(DateTimeUtils.toTs(DateTimeUtils.fromTs(oldTs.get()).plusYears(12)));

                                return new Telemetry.Point<>(newTs, point.getValue());
                            })
                            .collect(Collectors.collectingAndThen(
                                    Collectors.toCollection(TreeSet::new),
                                    set -> new Telemetry<>(originalTelemetry.getName(), set)
                            ));

                    Telemetry<Double> shortTelemetry = fullTelemetry.getPoints().stream()
                            .filter(point -> {
                                ZonedDateTime date = DateTimeUtils.fromTs(point.getTs());
                                return date.getYear() < 2025;
                            })
                            .collect(Collectors.collectingAndThen(
                                    Collectors.toCollection(TreeSet::new),
                                    set -> new Telemetry<>(originalTelemetry.getName(), set)
                            ));

                    return Stream.of(
                            new ElectricityLoadDiagrams20112014(deviceName, "", fullTelemetry),
                            new ElectricityLoadDiagrams20112014(deviceName + "_short", "", shortTelemetry)
                    );
                })

                .collect(Collectors.toCollection(TreeSet::new));

        log.info("Preprocessing is finished");
        return new ModelData(devices);
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

        Set<ElectricityLoadDiagrams20112014> meters = data.getData().stream()
                .map(item -> (ElectricityLoadDiagrams20112014) item)
                .collect(Collectors.toCollection(TreeSet::new));

        int iterator = 1;
        for (ElectricityLoadDiagrams20112014 meter : meters) {
            log.info("Pushing telemetry for device {}/{}: {}", iterator++, meters.size(), meter.getSystemName());
            Device consumerDevice = createMeter(meter, ownerId, deviceGroupId, strictGeneration);
        }
    }

    private void validateData(ModelData data) {
        Set<ElectricityLoadDiagrams20112014> meters = data.getData().stream()
                .map(item -> (ElectricityLoadDiagrams20112014) item)
                .collect(Collectors.toSet());

        Set<String> devices = meters.stream()
                .map(ModelEntity::getSystemName)
                .collect(Collectors.toSet());

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
        Set<ElectricityLoadDiagrams20112014> meters = data.getData().stream()
                .map(item -> (ElectricityLoadDiagrams20112014) item)
                .collect(Collectors.toSet());

        Set<String> devices = meters.stream()
                .map(ModelEntity::getSystemName)
                .collect(Collectors.toSet());

        this.tbRestClient.getAllDevices()
                .stream()
                .filter(device -> devices.contains(device.getName()))
                .forEach(device -> this.tbRestClient.deleteDevice(device.getUuidId()));
    }


    private Device createMeter(ElectricityLoadDiagrams20112014 meter, UUID ownerId, UUID deviceGroupId, boolean strictGeneration) {
        Device device;

        if (tbRestClient.isPe()) {
            device = strictGeneration
                    ? tbRestClient.createDevice(meter.getSystemName(), meter.entityType(), new CustomerId(ownerId), null)
                    : tbRestClient.createDeviceIfNotExists(meter.getSystemName(), meter.entityType(), new CustomerId(ownerId), null);
            tbRestClient.addEntitiesToTheGroup(deviceGroupId, Set.of(device.getUuidId()));
        } else {
            device = strictGeneration
                    ? tbRestClient.createDevice(meter.getSystemName(), meter.entityType(), null)
                    : tbRestClient.createDeviceIfNotExists(meter.getSystemName(), meter.entityType(), null);
            tbRestClient.assignDeviceToCustomer(ownerId, device.getUuidId());
        }

        DeviceCredentials deviceCredentials = tbRestClient.getDeviceCredentials(device.getUuidId());
        tbRestClient.pushTelemetry(deviceCredentials.getCredentialsId(), meter.getConsumption());
        return device;
    }

    private Map<String, Telemetry<Double>> loadTelemetry(Path filePath, long startGenerationTime, long endGenerationTime) {
        log.info("Start telemetry parsing from the file: {}", filePath);
        CSVFormat format = getFormat();
        try (
                FileReader reader = new FileReader(filePath.toFile());
                CSVParser parser = format.parse(reader);
        ) {
            Map<String, Telemetry<Double>> consumptionMap = parser.getHeaderMap().keySet().stream()
                    .filter(header -> !header.equals(DATE_FIELD))
                    .limit(DEVICE_LIMIT)
                    .map(header -> Map.entry(header, new Telemetry<Double>("consumption")))
                    .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

            int iterator = 1;
            for (CSVRecord record : parser) {
                LocalDateTime dateTime = LocalDateTime
                        .parse(record.get(DATE_FIELD), formatter);

                long ts = dateTime.toInstant(ZoneOffset.UTC).toEpochMilli();
                if (ts < startGenerationTime && endGenerationTime <= ts) {
                    continue;
                }

                for (String header : consumptionMap.keySet()) {
                    if (header.equals(DATE_FIELD)) {
                        continue;
                    }
                    String recordValue = record.get(header);

                    String valueStr = recordValue.replace(",", ".");
                    double value = Double.parseDouble(valueStr);

                    Telemetry<Double> telemetry = consumptionMap.get(header);
                    telemetry.add(new Telemetry.Point<>(Timestamp.of(ts), value));
                }

                iterator++;
                if (iterator % 10000 == 0) {
                    log.info("Processed CSV row: {}", iterator);
                }
            }

            log.info("Start telemetry parsing from the file is finished");
            return consumptionMap;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private Set<String> getAllDevicesNames(Path filePath) {
        CSVFormat format = getFormat();
        try (
                FileReader reader = new FileReader(filePath.toFile());
                CSVParser parser = format.parse(reader);
        ) {
            return parser.getHeaderMap().keySet().stream()
                    .filter(header -> !header.equals(DATE_FIELD))
                    .collect(Collectors.toCollection(TreeSet::new));

        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private CSVFormat getFormat() {
        return CSVFormat.DEFAULT.builder()
                .setHeader()
                .setDelimiter(';')
                .build();
    }
}

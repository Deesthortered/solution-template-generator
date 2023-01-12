package org.thingsboard.trendz.generator.solution.energymetering;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.thingsboard.server.common.data.Customer;
import org.thingsboard.server.common.data.Device;
import org.thingsboard.server.common.data.EntityType;
import org.thingsboard.server.common.data.asset.Asset;
import org.thingsboard.server.common.data.security.DeviceCredentials;
import org.thingsboard.trendz.generator.exception.CustomerAlreadyExistException;
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

import java.util.List;
import java.util.Set;

@Slf4j
@Service
public class EnergyMeteringSolution implements SolutionTemplateGenerator {

    private static final String CUSTOMER_TITLE = "Energy Metering Customer";
    private static final String CUSTOMER_USER_EMAIL = "energymetering@thingsboard.io";
    private static final String CUSTOMER_USER_PASSWORD = "password";
    private static final String CUSTOMER_USER_FIRST_NAME = "John";
    private static final String CUSTOMER_USER_LAST_NAME = "Doe";

    private static final String RULE_CHAIN_NAME = "Energy Metering Rule Chain";

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
//            applyData(data);

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
        Apartment ap15 = makeHogurityApartment15();

        return Building.builder()
                .systemName("Hogurity")
                .systemLabel("Asset label for Hogurity building")
                .address("USA, New York, New York City, Manhattan, ...")
                .apartments(Set.of(ap11, ap12, ap13, ap14, ap15))
                .build();
    }


    private Apartment makeAlpireApartment11() {
        return Apartment.builder()
                .systemName("Apt 101 in Alpire")
                .systemLabel("Apartment label")
                .floor(1)
                .area(0)
                .roomNumber(1)
                .state("")
                .energyMeter(
                        EnergyMeter.builder()
                                .systemName("")
                                .systemLabel("")
                                .serialNumber(0L)
                                .installDate(0L)
                                .build()
                )
                .heatMeter(
                        HeatMeter.builder()
                                .systemName("")
                                .systemLabel("")
                                .serialNumber(0L)
                                .installDate(0L)
                                .build()
                )
                .build();
    }

    private Apartment makeAlpireApartment12() {
        return Apartment.builder()
                .systemName("Apt 102 in Alpire")
                .systemLabel("Apartment label")
                .floor(1)
                .area(0)
                .roomNumber(2)
                .state("")
                .energyMeter(
                        EnergyMeter.builder()
                                .systemName("")
                                .systemLabel("")
                                .serialNumber(0L)
                                .installDate(0L)
                                .build()
                )
                .heatMeter(
                        HeatMeter.builder()
                                .systemName("")
                                .systemLabel("")
                                .serialNumber(0L)
                                .installDate(0L)
                                .build()
                )
                .build();
    }

    private Apartment makeAlpireApartment21() {
        return Apartment.builder()
                .systemName("Apt 201 in Alpire")
                .systemLabel("Apartment label")
                .floor(2)
                .area(0)
                .roomNumber(3)
                .state("")
                .energyMeter(
                        EnergyMeter.builder()
                                .systemName("")
                                .systemLabel("")
                                .serialNumber(0L)
                                .installDate(0L)
                                .build()
                )
                .heatMeter(
                        HeatMeter.builder()
                                .systemName("")
                                .systemLabel("")
                                .serialNumber(0L)
                                .installDate(0L)
                                .build()
                )
                .build();
    }

    private Apartment makeAlpireApartment22() {
        return Apartment.builder()
                .systemName("Apt 202 in Alpire")
                .systemLabel("Apartment label")
                .floor(2)
                .area(0)
                .roomNumber(4)
                .state("")
                .energyMeter(
                        EnergyMeter.builder()
                                .systemName("")
                                .systemLabel("")
                                .serialNumber(0L)
                                .installDate(0L)
                                .build()
                )
                .heatMeter(
                        HeatMeter.builder()
                                .systemName("")
                                .systemLabel("")
                                .serialNumber(0L)
                                .installDate(0L)
                                .build()
                )
                .build();
    }

    private Apartment makeAlpireApartment31() {
        return Apartment.builder()
                .systemName("Apt 301 in Alpire")
                .systemLabel("Apartment label")
                .floor(3)
                .area(0)
                .roomNumber(5)
                .state("")
                .energyMeter(
                        EnergyMeter.builder()
                                .systemName("")
                                .systemLabel("")
                                .serialNumber(0L)
                                .installDate(0L)
                                .build()
                )
                .heatMeter(
                        HeatMeter.builder()
                                .systemName("")
                                .systemLabel("")
                                .serialNumber(0L)
                                .installDate(0L)
                                .build()
                )
                .build();
    }

    private Apartment makeAlpireApartment32() {
        return Apartment.builder()
                .systemName("Apt 302 in Alpire")
                .systemLabel("Apartment label")
                .floor(3)
                .area(0)
                .roomNumber(6)
                .state("")
                .energyMeter(
                        EnergyMeter.builder()
                                .systemName("")
                                .systemLabel("")
                                .serialNumber(0L)
                                .installDate(0L)
                                .build()
                )
                .heatMeter(
                        HeatMeter.builder()
                                .systemName("")
                                .systemLabel("")
                                .serialNumber(0L)
                                .installDate(0L)
                                .build()
                )
                .build();
    }

    private Apartment makeAlpireApartment41() {
        return Apartment.builder()
                .systemName("Apt 401 in Alpire")
                .systemLabel("Apartment label")
                .floor(4)
                .area(0)
                .roomNumber(7)
                .state("")
                .energyMeter(
                        EnergyMeter.builder()
                                .systemName("")
                                .systemLabel("")
                                .serialNumber(0L)
                                .installDate(0L)
                                .build()
                )
                .heatMeter(
                        HeatMeter.builder()
                                .systemName("")
                                .systemLabel("")
                                .serialNumber(0L)
                                .installDate(0L)
                                .build()
                )
                .build();
    }

    private Apartment makeAlpireApartment42() {
        return Apartment.builder()
                .systemName("Apt 402 in Alpire")
                .systemLabel("Apartment label")
                .floor(4)
                .area(0)
                .roomNumber(8)
                .state("")
                .energyMeter(
                        EnergyMeter.builder()
                                .systemName("")
                                .systemLabel("")
                                .serialNumber(0L)
                                .installDate(0L)
                                .build()
                )
                .heatMeter(
                        HeatMeter.builder()
                                .systemName("")
                                .systemLabel("")
                                .serialNumber(0L)
                                .installDate(0L)
                                .build()
                )
                .build();
    }

    private Apartment makeAlpireApartment51() {
        return Apartment.builder()
                .systemName("Apt 501 in Alpire")
                .systemLabel("Apartment label")
                .floor(5)
                .area(0)
                .roomNumber(9)
                .state("")
                .energyMeter(
                        EnergyMeter.builder()
                                .systemName("")
                                .systemLabel("")
                                .serialNumber(0L)
                                .installDate(0L)
                                .build()
                )
                .heatMeter(
                        HeatMeter.builder()
                                .systemName("")
                                .systemLabel("")
                                .serialNumber(0L)
                                .installDate(0L)
                                .build()
                )
                .build();
    }

    private Apartment makeAlpireApartment52() {
        return Apartment.builder()
                .systemName("Apt 502 in Alpire")
                .systemLabel("Apartment label")
                .floor(5)
                .area(0)
                .roomNumber(10)
                .state("")
                .energyMeter(
                        EnergyMeter.builder()
                                .systemName("")
                                .systemLabel("")
                                .serialNumber(0L)
                                .installDate(0L)
                                .build()
                )
                .heatMeter(
                        HeatMeter.builder()
                                .systemName("")
                                .systemLabel("")
                                .serialNumber(0L)
                                .installDate(0L)
                                .build()
                )
                .build();
    }


    private Apartment makeFelineApartment11() {
        return Apartment.builder()
                .systemName("Apt 101 in Feline")
                .systemLabel("")
                .floor(1)
                .area(0)
                .roomNumber(1)
                .state("")
                .energyMeter(
                        EnergyMeter.builder()
                                .systemName("")
                                .systemLabel("")
                                .serialNumber(0L)
                                .installDate(0L)
                                .build()
                )
                .heatMeter(
                        HeatMeter.builder()
                                .systemName("")
                                .systemLabel("")
                                .serialNumber(0L)
                                .installDate(0L)
                                .build()
                )
                .build();
    }

    private Apartment makeFelineApartment12() {
        return Apartment.builder()
                .systemName("Apt 102 in Feline")
                .systemLabel("")
                .floor(1)
                .area(0)
                .roomNumber(2)
                .state("")
                .energyMeter(
                        EnergyMeter.builder()
                                .systemName("")
                                .systemLabel("")
                                .serialNumber(0L)
                                .installDate(0L)
                                .build()
                )
                .heatMeter(
                        HeatMeter.builder()
                                .systemName("")
                                .systemLabel("")
                                .serialNumber(0L)
                                .installDate(0L)
                                .build()
                )
                .build();
    }

    private Apartment makeFelineApartment13() {
        return Apartment.builder()
                .systemName("Apt 103 in Feline")
                .systemLabel("")
                .floor(1)
                .area(0)
                .roomNumber(3)
                .state("")
                .energyMeter(
                        EnergyMeter.builder()
                                .systemName("")
                                .systemLabel("")
                                .serialNumber(0L)
                                .installDate(0L)
                                .build()
                )
                .heatMeter(
                        HeatMeter.builder()
                                .systemName("")
                                .systemLabel("")
                                .serialNumber(0L)
                                .installDate(0L)
                                .build()
                )
                .build();
    }

    private Apartment makeFelineApartment21() {
        return Apartment.builder()
                .systemName("Apt 201 in Feline")
                .systemLabel("")
                .floor(2)
                .area(0)
                .roomNumber(4)
                .state("")
                .energyMeter(
                        EnergyMeter.builder()
                                .systemName("")
                                .systemLabel("")
                                .serialNumber(0L)
                                .installDate(0L)
                                .build()
                )
                .heatMeter(
                        HeatMeter.builder()
                                .systemName("")
                                .systemLabel("")
                                .serialNumber(0L)
                                .installDate(0L)
                                .build()
                )
                .build();
    }

    private Apartment makeFelineApartment22() {
        return Apartment.builder()
                .systemName("Apt 202 in Feline")
                .systemLabel("")
                .floor(2)
                .area(0)
                .roomNumber(5)
                .state("")
                .energyMeter(
                        EnergyMeter.builder()
                                .systemName("")
                                .systemLabel("")
                                .serialNumber(0L)
                                .installDate(0L)
                                .build()
                )
                .heatMeter(
                        HeatMeter.builder()
                                .systemName("")
                                .systemLabel("")
                                .serialNumber(0L)
                                .installDate(0L)
                                .build()
                )
                .build();
    }

    private Apartment makeFelineApartment23() {
        return Apartment.builder()
                .systemName("Apt 203 in Feline")
                .systemLabel("")
                .floor(2)
                .area(0)
                .roomNumber(6)
                .state("")
                .energyMeter(
                        EnergyMeter.builder()
                                .systemName("")
                                .systemLabel("")
                                .serialNumber(0L)
                                .installDate(0L)
                                .build()
                )
                .heatMeter(
                        HeatMeter.builder()
                                .systemName("")
                                .systemLabel("")
                                .serialNumber(0L)
                                .installDate(0L)
                                .build()
                )
                .build();
    }

    private Apartment makeFelineApartment31() {
        return Apartment.builder()
                .systemName("Apt 301 in Feline")
                .systemLabel("")
                .floor(3)
                .area(0)
                .roomNumber(7)
                .state("")
                .energyMeter(
                        EnergyMeter.builder()
                                .systemName("")
                                .systemLabel("")
                                .serialNumber(0L)
                                .installDate(0L)
                                .build()
                )
                .heatMeter(
                        HeatMeter.builder()
                                .systemName("")
                                .systemLabel("")
                                .serialNumber(0L)
                                .installDate(0L)
                                .build()
                )
                .build();
    }

    private Apartment makeFelineApartment32() {
        return Apartment.builder()
                .systemName("Apt 301 in Feline")
                .systemLabel("")
                .floor(3)
                .area(0)
                .roomNumber(8)
                .state("")
                .energyMeter(
                        EnergyMeter.builder()
                                .systemName("")
                                .systemLabel("")
                                .serialNumber(0L)
                                .installDate(0L)
                                .build()
                )
                .heatMeter(
                        HeatMeter.builder()
                                .systemName("")
                                .systemLabel("")
                                .serialNumber(0L)
                                .installDate(0L)
                                .build()
                )
                .build();
    }

    private Apartment makeFelineApartment33() {
        return Apartment.builder()
                .systemName("Apt 303 in Feline")
                .systemLabel("")
                .floor(3)
                .area(0)
                .roomNumber(9)
                .state("")
                .energyMeter(
                        EnergyMeter.builder()
                                .systemName("")
                                .systemLabel("")
                                .serialNumber(0L)
                                .installDate(0L)
                                .build()
                )
                .heatMeter(
                        HeatMeter.builder()
                                .systemName("")
                                .systemLabel("")
                                .serialNumber(0L)
                                .installDate(0L)
                                .build()
                )
                .build();
    }


    private Apartment makeHogurityApartment11() {
        return Apartment.builder()
                .systemName("Apt 101 in Hogurity")
                .systemLabel("")
                .floor(1)
                .area(0)
                .roomNumber(1)
                .state("")
                .energyMeter(
                        EnergyMeter.builder()
                                .systemName("")
                                .systemLabel("")
                                .serialNumber(0L)
                                .installDate(0L)
                                .build()
                )
                .heatMeter(
                        HeatMeter.builder()
                                .systemName("")
                                .systemLabel("")
                                .serialNumber(0L)
                                .installDate(0L)
                                .build()
                )
                .build();
    }

    private Apartment makeHogurityApartment12() {
        return Apartment.builder()
                .systemName("Apt 102 in Hogurity")
                .systemLabel("")
                .floor(1)
                .area(0)
                .roomNumber(2)
                .state("")
                .energyMeter(
                        EnergyMeter.builder()
                                .systemName("")
                                .systemLabel("")
                                .serialNumber(0L)
                                .installDate(0L)
                                .build()
                )
                .heatMeter(
                        HeatMeter.builder()
                                .systemName("")
                                .systemLabel("")
                                .serialNumber(0L)
                                .installDate(0L)
                                .build()
                )
                .build();
    }

    private Apartment makeHogurityApartment13() {
        return Apartment.builder()
                .systemName("Apt 103 in Hogurity")
                .systemLabel("")
                .floor(1)
                .area(0)
                .roomNumber(3)
                .state("")
                .energyMeter(
                        EnergyMeter.builder()
                                .systemName("")
                                .systemLabel("")
                                .serialNumber(0L)
                                .installDate(0L)
                                .build()
                )
                .heatMeter(
                        HeatMeter.builder()
                                .systemName("")
                                .systemLabel("")
                                .serialNumber(0L)
                                .installDate(0L)
                                .build()
                )
                .build();
    }

    private Apartment makeHogurityApartment14() {
        return Apartment.builder()
                .systemName("Apt 104 in Hogurity")
                .systemLabel("")
                .floor(1)
                .area(0)
                .roomNumber(4)
                .state("")
                .energyMeter(
                        EnergyMeter.builder()
                                .systemName("")
                                .systemLabel("")
                                .serialNumber(0L)
                                .installDate(0L)
                                .build()
                )
                .heatMeter(
                        HeatMeter.builder()
                                .systemName("")
                                .systemLabel("")
                                .serialNumber(0L)
                                .installDate(0L)
                                .build()
                )
                .build();
    }

    private Apartment makeHogurityApartment15() {
        return Apartment.builder()
                .systemName("Apt 105 in Hogurity")
                .systemLabel("")
                .floor(1)
                .area(0)
                .roomNumber(5)
                .state("")
                .energyMeter(
                        EnergyMeter.builder()
                                .systemName("")
                                .systemLabel("")
                                .serialNumber(0L)
                                .installDate(0L)
                                .build()
                )
                .heatMeter(
                        HeatMeter.builder()
                                .systemName("")
                                .systemLabel("")
                                .serialNumber(0L)
                                .installDate(0L)
                                .build()
                )
                .build();
    }


    private Telemetry<? extends Number> createTelemetryEnergyMeterConsumption(boolean occupied, int level) {
        Telemetry<Integer> result = new Telemetry<>("energyConsumption");
        return result;
    }

    private Telemetry<? extends Number> createTelemetryEnergyMeterConsAbsolute(boolean occupied, int level) {
        Telemetry<Integer> result = new Telemetry<>("energyConsAbsolute");
        return result;
    }

    private Telemetry<? extends Number> createTelemetryHeatMeterConsumption(boolean occupied, int level) {
        Telemetry<Integer> result = new Telemetry<>("heatConsumption");
        return result;
    }

    private Telemetry<? extends Number> createTelemetryHeatMeterTemperature(boolean occupied, int level) {
        Telemetry<Integer> result = new Telemetry<>("temperature");
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

package org.thingsboard.trendz.generator.solution.energymetering;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.thingsboard.server.common.data.Customer;
import org.thingsboard.server.common.data.Device;
import org.thingsboard.server.common.data.EntityType;
import org.thingsboard.server.common.data.asset.Asset;
import org.thingsboard.trendz.generator.exception.CustomerAlreadyExistException;
import org.thingsboard.trendz.generator.exception.RuleChainAlreadyExistException;
import org.thingsboard.trendz.generator.model.Attribute;
import org.thingsboard.trendz.generator.model.CustomerUser;
import org.thingsboard.trendz.generator.model.Scope;
import org.thingsboard.trendz.generator.service.FileService;
import org.thingsboard.trendz.generator.service.TbRestClient;
import org.thingsboard.trendz.generator.solution.SolutionTemplateGenerator;

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


    @Autowired
    public EnergyMeteringSolution(
            TbRestClient tbRestClient,
            FileService fileService
    ) {
        this.tbRestClient = tbRestClient;
        this.fileService = fileService;
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


            Building alpire = Building.builder()
                    .systemName("Alpire")
                    .systemLabel("Asset label for Alpire building")
                    .address("USA, California, San Francisco, ...")
                    .apartments(Set.of(
                            Apartment.builder()
                                    .systemName("Apt 101 in Alpire")
                                    .systemLabel("")
                                    .name("")
                                    .localNumber(0)
                                    .floor(0)
                                    .area(0)
                                    .state("")
                                    .roomNumber(0)
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
                                    .build()
                    ))
                    .build();

            Building feline = Building.builder()
                    .systemName("Feline")
                    .systemLabel("Asset label for Feline building")
                    .address("USA, New York, New York City, Brooklyn, ...")
                    .apartments(Set.of(
                            Apartment.builder()
                                    .systemName("Apt 101 in Feline")
                                    .systemLabel("")
                                    .name("")
                                    .localNumber(0)
                                    .floor(0)
                                    .area(0)
                                    .state("")
                                    .roomNumber(0)
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
                                    .build()
                    ))
                    .build();

            Building hogurity = Building.builder()
                    .systemName("Hogurity")
                    .systemLabel("Asset label for Hogurity building")
                    .address("USA, New York, New York City, Manhattan, ...")
                    .apartments(Set.of(
                            Apartment.builder()
                                    .systemName("Apt 101 in Hogurity")
                                    .systemLabel("")
                                    .name("")
                                    .localNumber(0)
                                    .floor(0)
                                    .area(0)
                                    .state("")
                                    .roomNumber(0)
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
                                    .build()
                    ))
                    .build();

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
                new Attribute<>("name", apartment.getName()),
                new Attribute<>("localNumber", apartment.getLocalNumber()),
                new Attribute<>("floor", apartment.getFloor()),
                new Attribute<>("area", apartment.getArea()),
                new Attribute<>("state", apartment.getState()),
                new Attribute<>("roomNumber", apartment.getRoomNumber())
        );
        tbRestClient.setEntityAttributes(asset.getUuidId(), EntityType.ASSET, Scope.SERVER_SCOPE, attributes);

        return asset;
    }

    private Device createEnergyMeter(EnergyMeter energyMeter) {
        Device device = tbRestClient.createDevice(energyMeter.getSystemName(), "energyMeter");

        Set<Attribute<?>> attributes = Set.of(
                new Attribute<>("installDate", energyMeter.getInstallDate()),
                new Attribute<>("serialNumber", energyMeter.getSerialNumber())
        );
        tbRestClient.setEntityAttributes(device.getUuidId(), EntityType.DEVICE, Scope.SERVER_SCOPE, attributes);

        return device;
    }

    private Device createHeatMeter(HeatMeter heatMeter) {
        Device device = tbRestClient.createDevice(heatMeter.getSystemName(), "heatMeter");

        Set<Attribute<?>> attributes = Set.of(
                new Attribute<>("installDate", heatMeter.getInstallDate()),
                new Attribute<>("serialNumber", heatMeter.getSerialNumber())
        );
        tbRestClient.setEntityAttributes(device.getUuidId(), EntityType.DEVICE, Scope.SERVER_SCOPE, attributes);

        return device;
    }
}

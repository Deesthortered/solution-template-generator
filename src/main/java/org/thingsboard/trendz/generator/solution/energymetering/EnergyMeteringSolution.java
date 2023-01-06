package org.thingsboard.trendz.generator.solution.energymetering;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.thingsboard.server.common.data.Customer;
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
    private static final String CUSTOMER_USER_PASSWORD = "basicpassword123";
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
    public void generate() {
        log.info("Energy Metering - start generation");
        try {
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


            Customer customer = tbRestClient.createCustomer(CUSTOMER_TITLE);
            CustomerUser customerUser = tbRestClient.createCustomerUser(
                    customer, CUSTOMER_USER_EMAIL, CUSTOMER_USER_PASSWORD,
                    CUSTOMER_USER_FIRST_NAME, CUSTOMER_USER_LAST_NAME
            );

//            String buildingType = "building";
//            String apartmentType = "apartment";
//
//            Asset buildingAlpire = tbRestClient.createAsset("Alpire", buildingType);
//            Asset buildingFeline = tbRestClient.createAsset("Feline", buildingType);
//            Asset buildingHogurity = tbRestClient.createAsset("Hogurity", buildingType);
//
//            Asset apartmentAlpire101 = tbRestClient.createAsset("Apt 101 in Alpire", apartmentType);
//            Asset apartmentAlpire102 = tbRestClient.createAsset("Apt 102 in Alpire", apartmentType);
//            Asset apartmentAlpire201 = tbRestClient.createAsset("Apt 201 in Alpire", apartmentType);
//            Asset apartmentAlpire202 = tbRestClient.createAsset("Apt 202 in Alpire", apartmentType);
//
//            Asset apartmentFeline101 = tbRestClient.createAsset("Apt 101 in Feline", apartmentType);
//            Asset apartmentFeline102 = tbRestClient.createAsset("Apt 101 in Feline", apartmentType);
//            Asset apartmentFeline103 = tbRestClient.createAsset("Apt 101 in Feline", apartmentType);
//            Asset apartmentFeline201 = tbRestClient.createAsset("Apt 101 in Feline", apartmentType);
//            Asset apartmentFeline202 = tbRestClient.createAsset("Apt 101 in Feline", apartmentType);
//            Asset apartmentFeline203 = tbRestClient.createAsset("Apt 101 in Feline", apartmentType);
//            Asset apartmentFeline301 = tbRestClient.createAsset("Apt 101 in Feline", apartmentType);
//            Asset apartmentFeline302 = tbRestClient.createAsset("Apt 101 in Feline", apartmentType);
//            Asset apartmentFeline303 = tbRestClient.createAsset("Apt 101 in Feline", apartmentType);
//
//            Asset apartmentHogurity101 = tbRestClient.createAsset("Apt 101 in Hogurity", apartmentType);
//            Asset apartmentHogurity102 = tbRestClient.createAsset("Apt 102 in Hogurity", apartmentType);
//            Asset apartmentHogurity201 = tbRestClient.createAsset("Apt 201 in Hogurity", apartmentType);
//            Asset apartmentHogurity202 = tbRestClient.createAsset("Apt 202 in Hogurity", apartmentType);

            log.info("Energy Metering Solution - generation is completed!");
        } catch (Exception e) {
            log.error("Energy Metering Solution generate was failed, skipping...", e);
        }
    }

    @Override
    public void remove() {
        log.info("Energy Metering - start removal");
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
}

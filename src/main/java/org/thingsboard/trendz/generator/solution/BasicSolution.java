package org.thingsboard.trendz.generator.solution;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.thingsboard.rule.engine.debug.TbMsgGeneratorNode;
import org.thingsboard.rule.engine.debug.TbMsgGeneratorNodeConfiguration;
import org.thingsboard.rule.engine.telemetry.TbMsgTimeseriesNode;
import org.thingsboard.rule.engine.telemetry.TbMsgTimeseriesNodeConfiguration;
import org.thingsboard.server.common.data.Customer;
import org.thingsboard.server.common.data.Device;
import org.thingsboard.server.common.data.EntityType;
import org.thingsboard.server.common.data.asset.Asset;
import org.thingsboard.server.common.data.relation.EntityRelation;
import org.thingsboard.server.common.data.rule.NodeConnectionInfo;
import org.thingsboard.server.common.data.rule.RuleChain;
import org.thingsboard.server.common.data.rule.RuleChainMetaData;
import org.thingsboard.server.common.data.rule.RuleNode;
import org.thingsboard.server.common.data.security.DeviceCredentials;
import org.thingsboard.trendz.generator.exception.AssetAlreadyExistException;
import org.thingsboard.trendz.generator.exception.CustomerAlreadyExistException;
import org.thingsboard.trendz.generator.exception.DeviceAlreadyExistException;
import org.thingsboard.trendz.generator.exception.RuleChainAlreadyExistException;
import org.thingsboard.trendz.generator.model.Attribute;
import org.thingsboard.trendz.generator.model.NodeConnectionType;
import org.thingsboard.trendz.generator.model.RelationType;
import org.thingsboard.trendz.generator.model.RuleNodeAdditionalInfo;
import org.thingsboard.trendz.generator.model.Scope;
import org.thingsboard.trendz.generator.model.Telemetry;
import org.thingsboard.trendz.generator.model.Timestamp;
import org.thingsboard.trendz.generator.service.FileService;
import org.thingsboard.trendz.generator.service.TbRestClient;
import org.thingsboard.trendz.generator.utils.JsonUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Slf4j
@Service
public class BasicSolution implements SolutionTemplateGenerator {

    private static final String CUSTOMER_TITLE = "Basic Customer";
    private static final String DEVICE_NAME = "Basic Device Device";
    private static final String DEVICE_TYPE = "Basic Type";
    private static final String ASSET_NAME = "Basic Asset";
    private static final String ASSET_TYPE = "Basic Asset Type";
    private static final String RULE_CHAIN_NAME = "Basic Rule Chain";
    private static final String FILE_CODE_NAME = "basic.js";

    private final TbRestClient tbRestClient;
    private final FileService fileService;

    @Autowired
    public BasicSolution(
            TbRestClient tbRestClient,
            FileService fileService
    ) {
        this.tbRestClient = tbRestClient;
        this.fileService = fileService;
    }

    @Override
    public String getSolutionName() {
        return "Basic";
    }

    @Override
    public void generate() {
        log.info("Basic Solution - start generation");
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

            tbRestClient.getAssetByName(DEVICE_NAME)
                    .ifPresent(asset -> {
                        throw new AssetAlreadyExistException(asset);
                    });

            tbRestClient.getDeviceByName(DEVICE_NAME)
                    .ifPresent(device -> {
                        throw new DeviceAlreadyExistException(device);
                    });

            Customer customer = tbRestClient.createCustomer(CUSTOMER_TITLE);
            Asset asset = tbRestClient.createAsset(ASSET_NAME, ASSET_TYPE);
            Device device = tbRestClient.createDevice(DEVICE_NAME, DEVICE_TYPE);
            EntityRelation relation = tbRestClient.createRelation(RelationType.CONTAINS.getType(), asset.getId(), device.getId());

            tbRestClient.assignAssetToCustomer(customer.getUuidId(), asset.getUuidId());
            tbRestClient.assignDeviceToCustomer(customer.getUuidId(), device.getUuidId());

            Telemetry<Integer> deviceTelemetry = new Telemetry<>("deviceTelemetry");
            deviceTelemetry.add(new Telemetry.Point<>(Timestamp.of(1640995200000L), 10));
            deviceTelemetry.add(new Telemetry.Point<>(Timestamp.of(1641081600000L), 20));
            deviceTelemetry.add(new Telemetry.Point<>(Timestamp.of(1641168000000L), 30));
            deviceTelemetry.add(new Telemetry.Point<>(Timestamp.of(1641254400000L), 40));
            deviceTelemetry.add(new Telemetry.Point<>(Timestamp.of(1641340800000L), 50));
            DeviceCredentials credentials = tbRestClient.getCredentials(device.getUuidId());
            tbRestClient.pushTelemetry(credentials.getCredentialsId(), deviceTelemetry);

            Set<Attribute<?>> attributes = Set.of(
                    new Attribute<>("doubleKey", 1.0),
                    new Attribute<>("longKey", 1L),
                    new Attribute<>("intKey", 1),
                    new Attribute<>("booleanKey", true),
                    new Attribute<>("StringKey", "qwerty")
            );
            tbRestClient.setEntityAttributes(
                    device.getUuidId(), EntityType.DEVICE, Scope.SERVER_SCOPE, attributes
            );
            tbRestClient.setEntityAttributes(
                    asset.getUuidId(), EntityType.ASSET, Scope.SERVER_SCOPE, attributes
            );

            TbMsgGeneratorNodeConfiguration generatorConfiguration = new TbMsgGeneratorNodeConfiguration();
            generatorConfiguration.setOriginatorType(EntityType.DEVICE);
            generatorConfiguration.setOriginatorId(device.getId().toString());
            generatorConfiguration.setMsgCount(0);
            generatorConfiguration.setPeriodInSeconds(60);
            generatorConfiguration.setJsScript(this.fileService.getFileContent(getSolutionName(), FILE_CODE_NAME));

            RuleNode generatorNode = new RuleNode();
            generatorNode.setName("Basic Generator Node");
            generatorNode.setType(TbMsgGeneratorNode.class.getName());
            generatorNode.setConfiguration(JsonUtils.makeNodeFromPojo(generatorConfiguration));
            generatorNode.setAdditionalInfo(
                    RuleNodeAdditionalInfo.builder()
                            .description("Basic description")
                            .layoutX(RuleNodeAdditionalInfo.CELL_SIZE * 5)
                            .layoutY(RuleNodeAdditionalInfo.CELL_SIZE * 25)
                            .build()
                            .toJsonNode()
            );

            TbMsgTimeseriesNodeConfiguration saveConfiguration = new TbMsgTimeseriesNodeConfiguration();
            saveConfiguration.setDefaultTTL(0);
            saveConfiguration.setUseServerTs(true);
            saveConfiguration.setSkipLatestPersistence(false);

            RuleNode saveNode = new RuleNode();
            saveNode.setName("Basic Save Node");
            saveNode.setType(TbMsgTimeseriesNode.class.getName());
            saveNode.setConfiguration(JsonUtils.makeNodeFromPojo(saveConfiguration));
            saveNode.setAdditionalInfo(
                    RuleNodeAdditionalInfo.builder()
                            .description("Basic description")
                            .layoutX(RuleNodeAdditionalInfo.CELL_SIZE * 15)
                            .layoutY(RuleNodeAdditionalInfo.CELL_SIZE * 25)
                            .build()
                            .toJsonNode()
            );

            RuleChain ruleChain = tbRestClient.createRuleChain(RULE_CHAIN_NAME);
            RuleChainMetaData metaData = tbRestClient.getRuleChainMetadataByRuleChainId(ruleChain.getUuidId())
                    .orElseThrow();

            List<RuleNode> nodes = metaData.getNodes();
            nodes.add(generatorNode);
            nodes.add(saveNode);

            NodeConnectionInfo connection = new NodeConnectionInfo();
            connection.setType(NodeConnectionType.SUCCESS.getType());
            connection.setFromIndex(0);
            connection.setToIndex(1);

            List<NodeConnectionInfo> connections = metaData.getConnections();
            if (connections == null) {
                connections = new ArrayList<>();
                metaData.setConnections(connections);
            }
            connections.add(connection);

            RuleChainMetaData savedMetaData = tbRestClient.saveRuleChainMetadata(metaData);

            log.info("Basic Solution - generation is completed!");
        } catch (Exception e) {
            log.error("Basic Solution generate was failed, skipping...", e);
        }
    }

    @Override
    public void remove() {
        log.info("Basic Solution - start removal");
        try {
            Optional<Asset> assetByName = tbRestClient.getAssetByName(ASSET_NAME);
            Optional<Device> deviceByName = tbRestClient.getDeviceByName(DEVICE_NAME);
            if (assetByName.isPresent() && deviceByName.isPresent()) {
                tbRestClient.getRelation(
                        assetByName.get().getUuidId(), EntityType.ASSET,
                        deviceByName.get().getUuidId(), EntityType.DEVICE,
                        RelationType.CONTAINS
                ).ifPresent(relation -> {
                    tbRestClient.deleteRelation(
                            assetByName.get().getUuidId(), EntityType.ASSET,
                            deviceByName.get().getUuidId(), EntityType.DEVICE,
                            RelationType.CONTAINS
                    );
                });
            }

            assetByName.ifPresent(asset -> {
                tbRestClient.getCustomerByTitle(CUSTOMER_TITLE)
                        .ifPresent(customer -> tbRestClient.unassignAssetToCustomer(asset.getUuidId()));
                tbRestClient.deleteAsset(asset.getUuidId());
            });

            deviceByName.ifPresent(device -> {
                tbRestClient.getCustomerByTitle(CUSTOMER_TITLE)
                        .ifPresent(customer -> tbRestClient.unassignDeviceToCustomer(device.getUuidId()));
                tbRestClient.deleteDevice(device.getUuidId());
            });


            tbRestClient.getAllRuleChains()
                    .stream()
                    .filter(ruleChain -> ruleChain.getName().equals(RULE_CHAIN_NAME))
                    .findAny()
                    .flatMap(ruleChain -> tbRestClient.getRuleChainById(ruleChain.getUuidId()))
                    .ifPresent(ruleChain -> {
                        tbRestClient.deleteRuleChain(ruleChain.getUuidId());
                    });

            tbRestClient.getCustomerByTitle(CUSTOMER_TITLE)
                    .ifPresent(customer -> tbRestClient.deleteCustomer(customer.getUuidId()));

            log.info("Basic Solution - removal is completed!");
        } catch (Exception e) {
            log.error("WaterMeteringSolution removal was failed, skipping...", e);
        }
    }
}

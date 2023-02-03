package org.thingsboard.trendz.generator.service.roolchain;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.thingsboard.rule.engine.api.NodeConfiguration;
import org.thingsboard.rule.engine.debug.TbMsgGeneratorNode;
import org.thingsboard.rule.engine.debug.TbMsgGeneratorNodeConfiguration;
import org.thingsboard.rule.engine.metadata.TbGetAttributesNode;
import org.thingsboard.rule.engine.metadata.TbGetAttributesNodeConfiguration;
import org.thingsboard.rule.engine.telemetry.TbMsgTimeseriesNode;
import org.thingsboard.rule.engine.telemetry.TbMsgTimeseriesNodeConfiguration;
import org.thingsboard.rule.engine.transform.TbChangeOriginatorNode;
import org.thingsboard.rule.engine.transform.TbChangeOriginatorNodeConfiguration;
import org.thingsboard.rule.engine.transform.TbTransformMsgNode;
import org.thingsboard.rule.engine.transform.TbTransformMsgNodeConfiguration;
import org.thingsboard.server.common.data.EntityType;
import org.thingsboard.server.common.data.rule.NodeConnectionInfo;
import org.thingsboard.server.common.data.rule.RuleNode;
import org.thingsboard.server.common.data.script.ScriptLanguage;
import org.thingsboard.trendz.generator.model.tb.NodeConnectionType;
import org.thingsboard.trendz.generator.model.tb.RuleNodeAdditionalInfo;
import org.thingsboard.trendz.generator.service.FileService;
import org.thingsboard.trendz.generator.utils.JsonUtils;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
public class RuleChainBuildingService {

    private final FileService fileService;

    public RuleChainBuildingService(
            FileService fileService
    ) {
        this.fileService = fileService;
    }


    public RuleNode createSaveNode(String name, double gridX, double gridY) {
        TbMsgTimeseriesNodeConfiguration saveConfiguration = new TbMsgTimeseriesNodeConfiguration();
        saveConfiguration.setDefaultTTL(0);
        saveConfiguration.setUseServerTs(false);
        saveConfiguration.setSkipLatestPersistence(false);

        return createRuleNode(name, TbMsgTimeseriesNode.class, saveConfiguration, (int) gridX, (int) gridY);
    }

    public RuleNode createGeneratorNode(String solutionName, String name, UUID entityId, String fileName, double gridX, double gridY) throws IOException {
        String fileContent = this.fileService.getFileContent(solutionName, fileName);

        TbMsgGeneratorNodeConfiguration generatorConfiguration = new TbMsgGeneratorNodeConfiguration();
        generatorConfiguration.setOriginatorType(EntityType.DEVICE);
        generatorConfiguration.setOriginatorId(entityId.toString());
        generatorConfiguration.setMsgCount(0);
        generatorConfiguration.setPeriodInSeconds(3600);
        generatorConfiguration.setJsScript(fileContent);

        return createRuleNode(name, TbMsgGeneratorNode.class, generatorConfiguration, (int) gridX, (int) gridY);
    }

    public RuleNode createLatestTelemetryLoadNode(String name, String telemetryName, double gridX, double gridY) {
        TbGetAttributesNodeConfiguration configuration = new TbGetAttributesNodeConfiguration();
        configuration.setLatestTsKeyNames(List.of(telemetryName));
        configuration.setGetLatestValueWithTs(true);

        return createRuleNode(name, TbGetAttributesNode.class, configuration, (int) gridX, (int) gridY);
    }

    public RuleNode createTransformationNode(String solutionName, String name, String scriptFileName, double gridX, double gridY) throws IOException {
        String fileContent = this.fileService.getFileContent(solutionName, scriptFileName);

        TbTransformMsgNodeConfiguration configuration = new TbTransformMsgNodeConfiguration();
        configuration.setScriptLang(ScriptLanguage.JS);
        configuration.setJsScript(fileContent);

        return createRuleNode(name, TbTransformMsgNode.class, configuration, (int) gridX, (int) gridY);
    }

    public RuleNode createChangeOriginatorNode(String name, String entityNamePattern, EntityType entityType, double gridX, double gridY) {
        TbChangeOriginatorNodeConfiguration configuration = new TbChangeOriginatorNodeConfiguration();
        configuration.setOriginatorSource("ENTITY");
        configuration.setEntityType(entityType.name());
        configuration.setEntityNamePattern(entityNamePattern);

        return createRuleNode(name, TbChangeOriginatorNode.class, configuration, (int) gridX, (int) gridY);
    }

    public NodeConnectionInfo createRuleConnection(int from, int to) {
        NodeConnectionInfo connection = new NodeConnectionInfo();
        connection.setType(NodeConnectionType.SUCCESS.toString());
        connection.setFromIndex(from);
        connection.setToIndex(to);
        return connection;
    }


    private RuleNode createRuleNode(String name, Class<?> typeClass, NodeConfiguration<?> configuration, int x, int y) {
        RuleNode generatorNode = new RuleNode();
        generatorNode.setName(name);
        generatorNode.setType(typeClass.getName());
        generatorNode.setConfiguration(JsonUtils.makeNodeFromPojo(configuration));
        generatorNode.setAdditionalInfo(
                RuleNodeAdditionalInfo.builder()
                        .description("Description for " + name)
                        .layoutX(x)
                        .layoutY(y)
                        .build()
                        .toJsonNode()
        );

        return generatorNode;
    }
}
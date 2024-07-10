package org.thingsboard.trendz.generator.service.roolchain;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.thingsboard.rule.engine.api.NodeConfiguration;
import org.thingsboard.rule.engine.debug.TbMsgGeneratorNode;
import org.thingsboard.rule.engine.debug.TbMsgGeneratorNodeConfiguration;
import org.thingsboard.rule.engine.delay.TbMsgDelayNode;
import org.thingsboard.rule.engine.delay.TbMsgDelayNodeConfiguration;
import org.thingsboard.rule.engine.metadata.TbGetAttributesNode;
import org.thingsboard.rule.engine.metadata.TbGetAttributesNodeConfiguration;
import org.thingsboard.rule.engine.rest.TbRestApiCallNode;
import org.thingsboard.rule.engine.rest.TbRestApiCallNodeConfiguration;
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

import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
public class RuleChainBuildingService {

    private final FileService fileService;
    private final int telemetryTtl;

    public RuleChainBuildingService(
            FileService fileService,
            @Value("${generator.telemetryTtl}") int telemetryTtl
    ) {
        this.fileService = fileService;
        this.telemetryTtl = telemetryTtl;
    }

    public NodeConnectionInfo createRuleConnection(int from, int to) {
        NodeConnectionInfo connection = new NodeConnectionInfo();
        connection.setType(NodeConnectionType.SUCCESS.toString());
        connection.setFromIndex(from);
        connection.setToIndex(to);
        return connection;
    }


    public RuleNode createSaveNode(String name, double gridX, double gridY) {
        TbMsgTimeseriesNodeConfiguration saveConfiguration = new TbMsgTimeseriesNodeConfiguration();
        saveConfiguration.setDefaultTTL(this.telemetryTtl);
        saveConfiguration.setUseServerTs(false);
        saveConfiguration.setSkipLatestPersistence(false);

        return createRuleNode(name, TbMsgTimeseriesNode.class, saveConfiguration, (int) gridX, (int) gridY);
    }

    public RuleNode createGeneratorNode(String name, UUID entityId, EntityType entityType, String code, double gridX, double gridY) {
        TbMsgGeneratorNodeConfiguration generatorConfiguration = new TbMsgGeneratorNodeConfiguration();
        generatorConfiguration.setOriginatorType(entityType);
        generatorConfiguration.setOriginatorId(entityId.toString());
        generatorConfiguration.setMsgCount(0);
        generatorConfiguration.setPeriodInSeconds(3600);
        generatorConfiguration.setJsScript(code);

        return createRuleNode(name, TbMsgGeneratorNode.class, generatorConfiguration, (int) gridX, (int) gridY);
    }

    public RuleNode createOriginatorAttributesNode(String name, List<String> clientAttributes, List<String> sharedAttributes, List<String> serverAttributes, List<String> telemetries, boolean withTs, double gridX, double gridY) {
        TbGetAttributesNodeConfiguration configuration = new TbGetAttributesNodeConfiguration();
        configuration.setClientAttributeNames(clientAttributes);
        configuration.setSharedAttributeNames(sharedAttributes);
        configuration.setServerAttributeNames(serverAttributes);
        configuration.setLatestTsKeyNames(telemetries);
        configuration.setGetLatestValueWithTs(withTs);

        return createRuleNode(name, TbGetAttributesNode.class, configuration, (int) gridX, (int) gridY);
    }

    public RuleNode createTransformationNode(String name, String script, double gridX, double gridY) {
        TbTransformMsgNodeConfiguration configuration = new TbTransformMsgNodeConfiguration();
        configuration.setScriptLang(ScriptLanguage.JS);
        configuration.setJsScript(script);

        return createRuleNode(name, TbTransformMsgNode.class, configuration, (int) gridX, (int) gridY);
    }

    public RuleNode createChangeOriginatorNode(String name, String entityNamePattern, EntityType entityType, double gridX, double gridY) {
        TbChangeOriginatorNodeConfiguration configuration = new TbChangeOriginatorNodeConfiguration();
        configuration.setOriginatorSource("ENTITY");
        configuration.setEntityType(entityType.name());
        configuration.setEntityNamePattern(entityNamePattern);

        return createRuleNode(name, TbChangeOriginatorNode.class, configuration, (int) gridX, (int) gridY);
    }

    public RuleNode createRestApiCallNode(String name, String urlPattern, String requestMethod, double gridX, double gridY) {
        TbRestApiCallNodeConfiguration configuration = new TbRestApiCallNodeConfiguration();
        configuration.setRestEndpointUrlPattern(urlPattern);
        configuration.setRequestMethod(requestMethod);
        configuration.setHeaders(Collections.emptyMap());

        return createRuleNode(name, TbRestApiCallNode.class, configuration, (int) gridX, (int) gridY);
    }

    public RuleNode createDelayNode(String name, int periodSeconds, double gridX, double gridY) {
        TbMsgDelayNodeConfiguration configuration = new TbMsgDelayNodeConfiguration();
        configuration.setPeriodInSeconds(periodSeconds);
        configuration.setMaxPendingMsgs(100);

        return createRuleNode(name, TbMsgDelayNode.class, configuration, (int) gridX, (int) gridY);
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

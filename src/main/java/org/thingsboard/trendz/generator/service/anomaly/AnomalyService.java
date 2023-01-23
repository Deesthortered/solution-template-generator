package org.thingsboard.trendz.generator.service.anomaly;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.thingsboard.trendz.generator.model.anomaly.AnomalyInfo;
import org.thingsboard.trendz.generator.model.anomaly.AnomalyType;
import org.thingsboard.trendz.generator.model.tb.Telemetry;

import javax.annotation.PostConstruct;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class AnomalyService {


    private final List<AnomalyCreator> anomalyCreatorList;
    private Map<AnomalyType, AnomalyCreator> typeToCreatorMap;

    @PostConstruct
    public void init() {
        this.typeToCreatorMap = new EnumMap<>(AnomalyType.class);
        for (AnomalyCreator creator : this.anomalyCreatorList) {
            this.typeToCreatorMap.put(creator.type(), creator);
        }
    }

    public void applyAnomaly(Telemetry<?> telemetry, List<AnomalyInfo> anomalyInfoList) {
        for (AnomalyInfo anomalyInfo : anomalyInfoList) {
            AnomalyCreator creator = this.typeToCreatorMap.get(anomalyInfo.getType());
            if (creator == null) {
                throw new IllegalStateException("Anomaly creator type is not supported: " + anomalyInfo.getType());
            }
            creator.create(telemetry, anomalyInfo);
        }
    }
}

package org.thingsboard.trendz.generator.service.anomaly;

import org.thingsboard.trendz.generator.model.anomaly.AnomalyInfo;
import org.thingsboard.trendz.generator.model.anomaly.AnomalyType;
import org.thingsboard.trendz.generator.model.tb.Telemetry;

public class IncreasedDataAnomalyCreator implements AnomalyCreator {

    @Override
    public AnomalyType type() {
        return AnomalyType.INCREASED_DATA;
    }

    @Override
    public void create(Telemetry<?> telemetry, AnomalyInfo anomalyInfo) {

    }
}

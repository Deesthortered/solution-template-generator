package org.thingsboard.trendz.generator.service.anomaly;

import org.thingsboard.trendz.generator.model.anomaly.AnomalyInfo;
import org.thingsboard.trendz.generator.model.anomaly.AnomalyType;
import org.thingsboard.trendz.generator.model.tb.Telemetry;

public class DecreasedDataAnomalyCreator implements AnomalyCreator {

    @Override
    public AnomalyType type() {
        return AnomalyType.DECREASED_DATA;
    }

    @Override
    public void create(Telemetry<?> telemetry, AnomalyInfo anomalyInfo) {

    }
}

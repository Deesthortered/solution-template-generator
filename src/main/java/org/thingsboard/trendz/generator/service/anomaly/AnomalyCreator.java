package org.thingsboard.trendz.generator.service.anomaly;

import org.thingsboard.trendz.generator.model.anomaly.AnomalyInfo;
import org.thingsboard.trendz.generator.model.anomaly.AnomalyType;
import org.thingsboard.trendz.generator.model.tb.Telemetry;

public interface AnomalyCreator {

    AnomalyType type();

    void create(Telemetry<?> telemetry, AnomalyInfo anomalyInfo);
}

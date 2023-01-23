package org.thingsboard.trendz.generator.service.anomaly;

import org.thingsboard.trendz.generator.model.anomaly.AnomalyInfo;
import org.thingsboard.trendz.generator.model.anomaly.AnomalyType;
import org.thingsboard.trendz.generator.model.tb.Telemetry;
import org.thingsboard.trendz.generator.utils.DateTimeUtils;

import java.time.ZonedDateTime;

public class ZeroValueAnomalyCreator implements AnomalyCreator {

    @Override
    public AnomalyType type() {
        return AnomalyType.ZERO_VALUES;
    }

    @Override
    public void create(Telemetry<?> telemetry, AnomalyInfo anomalyInfo) {
        ZonedDateTime startDate = DateTimeUtils.fromTs(anomalyInfo.getStartTs());
        ZonedDateTime endDate = DateTimeUtils.fromTs(anomalyInfo.getEndTs());

    }
}

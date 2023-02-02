package org.thingsboard.trendz.generator.service.anomaly;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.thingsboard.trendz.generator.model.anomaly.AnomalyInfo;
import org.thingsboard.trendz.generator.model.anomaly.AnomalyType;
import org.thingsboard.trendz.generator.model.tb.Telemetry;
import org.thingsboard.trendz.generator.utils.DateTimeUtils;

import java.time.ZonedDateTime;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
public class SettingValueAnomalyCreator implements AnomalyCreator {

    @Override
    public AnomalyType type() {
        return AnomalyType.SET_VALUES;
    }

    @Override
    public void create(Telemetry<? extends Number> telemetry, AnomalyInfo anomalyInfo) {
        ZonedDateTime startDate = anomalyInfo.getStartDate();
        ZonedDateTime endDate = anomalyInfo.getEndDate();

        Set<? extends Telemetry.Point<? extends Number>> rawPoints = telemetry.getPoints();
        Set<Telemetry.Point<? extends Number>> points = (Set<Telemetry.Point<? extends Number>>) rawPoints;

        Set<Telemetry.Point<? extends Number>> oldPoints = points.stream()
                .filter(point -> DateTimeUtils.toTs(startDate) <= point.getTs().get())
                .filter(point -> point.getTs().get() < DateTimeUtils.toTs(endDate))
                .collect(Collectors.toSet());

        Set<Telemetry.Point<? extends Number>> newPoints = oldPoints.stream()
                .map(point -> new Telemetry.Point<>(point.getTs(), castValue(point.getValue(), anomalyInfo.getSettingValue())))
                .collect(Collectors.toSet());

        points.removeAll(oldPoints);
        points.addAll(newPoints);
    }
}

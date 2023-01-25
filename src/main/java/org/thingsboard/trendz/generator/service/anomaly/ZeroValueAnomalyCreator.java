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
public class ZeroValueAnomalyCreator implements AnomalyCreator {

    @Override
    public AnomalyType type() {
        return AnomalyType.ZERO_VALUES;
    }

    @Override
    public <T> void create(Telemetry<T> telemetry, AnomalyInfo anomalyInfo) {
        ZonedDateTime startDate = anomalyInfo.getStartDate();
        ZonedDateTime endDate = anomalyInfo.getEndDate();

        Set<Telemetry.Point<T>> oldPoints = telemetry.getPoints().stream()
                .filter(point -> DateTimeUtils.toTs(startDate) <= point.getTs().get())
                .filter(point -> point.getTs().get() < DateTimeUtils.toTs(endDate))
                .collect(Collectors.toSet());

        Set<Telemetry.Point<T>> newPoints = oldPoints.stream()
                .map(point -> new Telemetry.Point<T>(point.getTs(), getZeroValue(point.getValue())))
                .collect(Collectors.toSet());

        telemetry.getPoints().removeAll(oldPoints);
        telemetry.getPoints().addAll(newPoints);
    }


    private <T> T getZeroValue(T oldValue) {
        if (oldValue instanceof Byte) {
            return (T) Byte.valueOf((byte) 0);
        }
        if (oldValue instanceof Short) {
            return (T) Short.valueOf((short) 0);
        }
        if (oldValue instanceof Integer) {
            return (T) Integer.valueOf(0);
        }
        if (oldValue instanceof Long) {
            return (T) Long.valueOf(0);
        }
        if (oldValue instanceof Float) {
            return (T) Float.valueOf(0);
        }
        if (oldValue instanceof Double) {
            return (T) Double.valueOf(0);
        }
        if (oldValue instanceof String) {
            return ((T) "");
        }
        throw new IllegalArgumentException("Value class is not supported: " + oldValue.getClass());
    }
}

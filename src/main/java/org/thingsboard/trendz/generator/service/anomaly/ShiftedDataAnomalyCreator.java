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
public class ShiftedDataAnomalyCreator implements AnomalyCreator {

    @Override
    public AnomalyType type() {
        return AnomalyType.SHIFTED_DATA;
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
                .map(point -> new Telemetry.Point<>(point.getTs(), getShiftedValue(point.getValue(), anomalyInfo.getValue())))
                .collect(Collectors.toSet());

        telemetry.getPoints().removeAll(oldPoints);
        telemetry.getPoints().addAll(newPoints);
    }


    private <T> T getShiftedValue(T oldValue, long shift) {
        if (oldValue instanceof Byte) {
            return (T) Byte.valueOf((byte) (((byte) oldValue) + ((byte) shift)));
        }
        if (oldValue instanceof Short) {
            return (T) Short.valueOf((short) (((short) oldValue) + ((short) shift)));
        }
        if (oldValue instanceof Integer) {
            return (T) Integer.valueOf((int) (((int) oldValue) + ((int) shift)));
        }
        if (oldValue instanceof Long) {
            return (T) Long.valueOf((long) (((long) oldValue) + ((long) shift)));
        }
        if (oldValue instanceof Float) {
            return (T) Float.valueOf((float) (((float) oldValue) + ((float) shift)));
        }
        if (oldValue instanceof Double) {
            return (T) Double.valueOf((double) (((double) oldValue) + ((double) shift)));
        }
        throw new IllegalArgumentException("Value class is not supported: " + oldValue.getClass());
    }
}

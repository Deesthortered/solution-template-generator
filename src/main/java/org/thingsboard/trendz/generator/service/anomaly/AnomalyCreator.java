package org.thingsboard.trendz.generator.service.anomaly;

import org.thingsboard.trendz.generator.model.anomaly.AnomalyInfo;
import org.thingsboard.trendz.generator.model.anomaly.AnomalyType;
import org.thingsboard.trendz.generator.model.tb.Telemetry;

public interface AnomalyCreator {

    AnomalyType type();

    void create(Telemetry<? extends Number> telemetry, AnomalyInfo anomalyInfo);


    default  <T> T castValue(T oldValue, double newValue) {
        if (oldValue instanceof Byte) {
            return (T) Byte.valueOf((byte) newValue);
        }
        if (oldValue instanceof Short) {
            return (T) Short.valueOf((short) newValue);
        }
        if (oldValue instanceof Integer) {
            return (T) Integer.valueOf((int) newValue);
        }
        if (oldValue instanceof Long) {
            return (T) Long.valueOf((long) newValue);
        }
        if (oldValue instanceof Float) {
            return (T) Float.valueOf((float) newValue);
        }
        if (oldValue instanceof Double) {
            return (T) Double.valueOf(newValue);
        }
        throw new IllegalArgumentException("Value class is not supported: " + oldValue.getClass());
    }
}

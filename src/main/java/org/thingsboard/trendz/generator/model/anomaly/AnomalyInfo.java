package org.thingsboard.trendz.generator.model.anomaly;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.ZonedDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnomalyInfo implements Comparable<AnomalyInfo> {

    private ZonedDateTime startDate;
    private ZonedDateTime endDate;
    private AnomalyType type;

    private double settingValue;
    private double shiftValue;
    private double coefficient;
    private double noiseAmplitude;

    @Override
    public int compareTo(AnomalyInfo that) {
        return this.startDate.compareTo(that.startDate);
    }
}

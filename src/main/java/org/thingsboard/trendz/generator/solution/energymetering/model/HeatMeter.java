package org.thingsboard.trendz.generator.solution.energymetering.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.thingsboard.trendz.generator.model.Telemetry;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HeatMeter implements Comparable<HeatMeter> {

    private String systemName;
    private String systemLabel;

    private long installDate;
    private long serialNumber;
    private Telemetry<Long> temperature;
    private Telemetry<Long> heatConsumption;
    private Telemetry<Long> heatConsAbsolute;


    @Override
    public int compareTo(@NotNull HeatMeter that) {
        return this.systemName.compareTo(that.systemName);
    }
}

package org.thingsboard.trendz.generator.solution.energymetering.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.thingsboard.trendz.generator.model.ModelEntity;
import org.thingsboard.trendz.generator.model.Telemetry;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HeatMeter implements ModelEntity {

    private String systemName;
    private String systemLabel;

    private long installDate;
    private long serialNumber;
    private Telemetry<Long> temperature;
    private Telemetry<Long> heatConsumption;
    private Telemetry<Long> heatConsAbsolute;
}

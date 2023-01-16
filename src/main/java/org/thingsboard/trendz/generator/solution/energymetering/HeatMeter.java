package org.thingsboard.trendz.generator.solution.energymetering;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.thingsboard.trendz.generator.model.Telemetry;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HeatMeter {

    private String systemName;
    private String systemLabel;

    private long installDate;
    private long serialNumber;
    private Telemetry<Integer> temperature;
    private Telemetry<Integer> heatConsumption;
    private Telemetry<Integer> heatConsAbsolute;
}

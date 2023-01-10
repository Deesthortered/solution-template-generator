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
public class EnergyMeter {

    private String systemName;
    private String systemLabel;

    private long installDate;
    private long serialNumber;
    private Telemetry<Integer> energyConsumption;
    private Telemetry<Integer> energyConsAbsolute;
    private Telemetry<Integer> value;
}

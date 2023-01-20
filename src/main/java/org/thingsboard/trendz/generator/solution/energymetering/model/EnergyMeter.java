package org.thingsboard.trendz.generator.solution.energymetering.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.thingsboard.trendz.generator.model.Telemetry;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EnergyMeter implements ModelEntity {

    private String systemName;
    private String systemLabel;

    private long installDate;
    private long serialNumber;

    private Telemetry<Long> energyConsumption;
    private Telemetry<Long> energyConsAbsolute;
}

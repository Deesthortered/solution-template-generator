package org.thingsboard.trendz.generator.solution.electricity_load.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.thingsboard.trendz.generator.model.ModelEntity;
import org.thingsboard.trendz.generator.model.tb.Telemetry;

@Getter
@ToString
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ElectricityLoadDiagrams20112014 extends ModelEntity {

    @Override
    public String entityType() {
        return "EL ElectricityLoadDiagrams20112014";
    }

    private String systemName;
    private String systemLabel;

    private Telemetry<Double> consumption;
}

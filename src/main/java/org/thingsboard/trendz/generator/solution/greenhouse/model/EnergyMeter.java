package org.thingsboard.trendz.generator.solution.greenhouse.model;

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
public class EnergyMeter extends ModelEntity {

    @Override
    public String entityType() {
        return "GR energy meter";
    }

    private String systemName;
    private String systemLabel;
    private String fromGreenhouse;

    private Telemetry<Double> energyConsumptionLight;
    private Telemetry<Double> energyConsumptionHeating;
    private Telemetry<Double> energyConsumptionCooling;
    private Telemetry<Double> energyConsumptionAirControl;
    private Telemetry<Double> energyConsumptionIrrigation;
}
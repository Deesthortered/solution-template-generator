package org.thingsboard.trendz.generator.solution.greenhouse.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.thingsboard.trendz.generator.model.ModelEntity;

import java.util.Set;

@Getter
@ToString
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Greenhouse extends ModelEntity {

    @Override
    public String entityType() {
        return "GR) greenhouse";
    }

    private String systemName;
    private String systemLabel;

    private PlantType plantType;

    private Set<Section> sections;
    private InsideAirWarmMoistureSensor insideAirWarmMoistureSensor;
    private InsideLightSensor insideLightSensor;
    private InsideCO2Sensor insideCO2Sensor;
    private OutsideAirWarmMoistureSensor outsideAirWarmMoistureSensor;
    private OutsideLightSensor outsideLightSensor;
    private EnergyMeter energyMeter;
    private WaterMeter waterMeter;
}

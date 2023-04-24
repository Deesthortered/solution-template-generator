package org.thingsboard.trendz.generator.solution.greenhouse.model;

import lombok.*;
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
        return "GR greenhouse";
    }

    private String systemName;
    private String systemLabel;

    private Plant plant;
    private String address;
    private double latitude;
    private double longitude;

    private Set<Section> sections;
    private InsideAirWarmHumiditySensor insideAirWarmHumiditySensor;
    private InsideLightSensor insideLightSensor;
    private InsideCO2Sensor insideCO2Sensor;
    private OutsideAirWarmHumiditySensor outsideAirWarmHumiditySensor;
    private OutsideLightSensor outsideLightSensor;
    private EnergyMeter energyMeter;
    private WaterMeter waterMeter;

    private String workersInCharge;
}

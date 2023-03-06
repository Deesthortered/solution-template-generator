package org.thingsboard.trendz.generator.solution.greenhouse.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.thingsboard.trendz.generator.model.ModelEntity;

@Getter
@ToString
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Section extends ModelEntity {

    @Override
    public String entityType() {
        return "GR section";
    }

    private String systemName;
    private String systemLabel;

    private int positionHeight;
    private int positionWidth;
    private int area;

    private SoilWarmMoistureSensor soilWarmMoistureSensor;
    private SoilAciditySensor soilAciditySensor;
    private SoilNpkSensor soilNpkSensor;
    private HarvestReporter harvestReporter;
}

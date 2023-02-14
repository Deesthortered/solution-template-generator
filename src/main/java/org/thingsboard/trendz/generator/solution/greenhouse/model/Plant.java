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
public class Plant extends ModelEntity {

    // special asset for saving plant properties
    @Override
    public String entityType() {
        return "GR) plant";
    }

    private String systemName;
    private String systemLabel;

    private String name;
    private String variety;
    private int minRipeningCycleDays;
    private int maxRipeningCycleDays;
    private double dayMinTemperature;
    private double dayMaxTemperature;
    private double nightMinTemperature;
    private double nightMaxTemperature;
    private double minAirHumidity;
    private double maxAirHumidity;
    private double minSoilMoisture;
    private double maxSoilMoisture;
    private double minCo2Concentration;
    private double maxCo2Concentration;
    private double minLight;
    private double maxLight;
    private double minPh;
    private double maxPh;
}

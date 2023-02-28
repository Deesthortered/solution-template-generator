package org.thingsboard.trendz.generator.solution.greenhouse.configuration;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.thingsboard.trendz.generator.model.ConfigurationEntity;
import org.thingsboard.trendz.generator.solution.greenhouse.model.PlantName;

@Getter
@ToString
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlantConfiguration extends ConfigurationEntity {

    private int order;

    private PlantName name;
    private String variety;
    private int minRipeningCycleDays;
    private int maxRipeningCycleDays;
    private double dayMinTemperature;
    private double dayMaxTemperature;
    private double nightMinTemperature;
    private double nightMaxTemperature;
    private double dayMinLight;
    private double dayMaxLight;
    private double nightMinLight;
    private double nightMaxLight;
    private double minAirHumidity;
    private double maxAirHumidity;
    private double minSoilMoisture;
    private double maxSoilMoisture;
    private double minCo2Concentration;
    private double maxCo2Concentration;
    private double minPh;
    private double maxPh;
}

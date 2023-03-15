package org.thingsboard.trendz.generator.solution.greenhouse.configuration;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.thingsboard.trendz.generator.model.ConfigurationEntity;
import org.thingsboard.trendz.generator.solution.greenhouse.model.PlantName;

import java.util.List;

@Getter
@ToString
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlantConfiguration extends ConfigurationEntity {

    private int order;

    private PlantName name;
    private String variety;
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
    private double minSoilTemperature;
    private double maxSoilTemperature;
    private double minCo2Concentration;
    private double maxCo2Concentration;
    private double minPh;
    private double maxPh;
    private int minRipeningCycleDays;
    private int maxRipeningCycleDays;
    private double minNitrogenLevel;
    private double maxNitrogenLevel;
    private double minPhosphorusLevel;
    private double maxPhosphorusLevel;
    private double minPotassiumLevel;
    private double maxPotassiumLevel;

    private List<Integer> growthPeriodsDayList;
    private List<Double> growthPeriodsNitrogenConsumption;
    private List<Double> growthPeriodsPhosphorusConsumption;
    private List<Double> growthPeriodsPotassiumConsumption;

    private double averageCropWeight;
}

package org.thingsboard.trendz.generator.solution.greenhouse.configuration;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.thingsboard.trendz.generator.model.ConfigurationEntity;
import org.thingsboard.trendz.generator.solution.greenhouse.model.PlantType;

@Getter
@ToString
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GreenhouseConfiguration extends ConfigurationEntity {

    private int order;

    private String name;
    private StationCity stationCity;
    private String address;
    private double latitude;
    private double longitude;
    private long installDate;

    private PlantType plantType;
    private String variety;
    private int sectionHeight;
    private int sectionWidth;
    private int sectionArea;
}

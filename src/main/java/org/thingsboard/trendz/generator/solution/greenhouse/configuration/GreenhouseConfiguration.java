package org.thingsboard.trendz.generator.solution.greenhouse.configuration;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.thingsboard.trendz.generator.model.ConfigurationEntity;
import org.thingsboard.trendz.generator.solution.greenhouse.model.Plant;

@Getter
@ToString
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GreenhouseConfiguration extends ConfigurationEntity {

    private int order;

    private long startTs;
    private long endTs;
    private String name;
    private StationCity stationCity;
    private String address;
    private double latitude;
    private double longitude;

    private Plant plant;
    private int sectionHeight;
    private int sectionWidth;
    private int sectionArea;
}

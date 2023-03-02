package org.thingsboard.trendz.generator.solution.greenhouse.configuration;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.thingsboard.trendz.generator.model.ConfigurationEntity;

import java.util.Set;

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

    private PlantConfiguration plantConfiguration;
    private int sectionHeight;
    private int sectionWidth;
    private int sectionArea;

    private Set<WorkerInChargeName> workersInCharge;
}

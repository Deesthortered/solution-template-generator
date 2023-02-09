package org.thingsboard.trendz.generator.solution.energymetering.configuration;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.thingsboard.trendz.generator.model.ConfigurationEntity;
import org.thingsboard.trendz.generator.model.anomaly.AnomalyInfo;

import java.util.Set;

@Getter
@ToString
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApartmentConfiguration extends ConfigurationEntity {

    private int order;

    private int floor;
    private int number;
    private int area;
    private boolean occupied;
    private int level;
    private long startDate;

    private Set<AnomalyInfo> anomalies;
}

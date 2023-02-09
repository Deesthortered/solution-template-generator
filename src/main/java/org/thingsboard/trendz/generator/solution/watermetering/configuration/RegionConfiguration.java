package org.thingsboard.trendz.generator.solution.watermetering.configuration;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.thingsboard.trendz.generator.model.ConfigurationEntity;
import org.thingsboard.trendz.generator.model.anomaly.AnomalyInfo;

import java.time.ZonedDateTime;
import java.util.Set;

@Getter
@ToString
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RegionConfiguration extends ConfigurationEntity {

    private int order;

    private ZonedDateTime startYear;
    private String name;
    private String label;

    private Set<AnomalyInfo> anomalies;

    private Set<ConsumerConfiguration> consumerConfigurations;
    private PumpStationConfiguration pumpStationConfiguration;
}

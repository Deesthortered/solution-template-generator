package org.thingsboard.trendz.generator.solution.watermetering.configuration;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.thingsboard.trendz.generator.model.anomaly.AnomalyInfo;

import java.time.ZonedDateTime;
import java.util.Set;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RegionConfiguration {

    private ZonedDateTime startYear;
    private String name;
    private String label;

    private Set<AnomalyInfo> anomalies;

    private Set<ConsumerConfiguration> consumerConfigurations;
    private PumpStationConfiguration pumpStationConfiguration;
}

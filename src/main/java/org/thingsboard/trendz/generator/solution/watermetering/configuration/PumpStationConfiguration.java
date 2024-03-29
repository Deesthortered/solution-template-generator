package org.thingsboard.trendz.generator.solution.watermetering.configuration;

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
public class PumpStationConfiguration extends ConfigurationEntity {

    private int order;

    private Set<AnomalyInfo> anomalies;
}

package org.thingsboard.trendz.generator.solution.watermetering.configuration;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.thingsboard.trendz.generator.model.ConfigurationEntity;
import org.thingsboard.trendz.generator.model.anomaly.AnomalyInfo;
import org.thingsboard.trendz.generator.solution.watermetering.model.ConsumerType;

import java.util.Set;

@Getter
@ToString
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConsumerConfiguration extends ConfigurationEntity {

    private int order;

    private String index;
    private ConsumerType type;

    private Set<AnomalyInfo> anomalies;
}

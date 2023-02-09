package org.thingsboard.trendz.generator.solution.watermetering.configuration;

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
public class CityConfiguration extends ConfigurationEntity {

    private int order;

    private String name;
    private String label;
    private long population;
    private Set<RegionConfiguration> regionConfigurations;
}

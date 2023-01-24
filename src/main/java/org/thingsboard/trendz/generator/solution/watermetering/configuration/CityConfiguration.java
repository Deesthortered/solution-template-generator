package org.thingsboard.trendz.generator.solution.watermetering.configuration;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Set;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CityConfiguration {

    private String name;
    private String label;
    private long population;
    private Set<RegionConfiguration> regionConfigurations;
}

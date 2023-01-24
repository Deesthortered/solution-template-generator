package org.thingsboard.trendz.generator.solution.watermetering.configuration;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.ZonedDateTime;
import java.util.Set;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CityConfiguration {

    private ZonedDateTime startYear;
    private String name;
    private String label;
    private long population;
    private Set<RegionConfiguration> regionConfigurations;
}

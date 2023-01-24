package org.thingsboard.trendz.generator.solution.watermetering.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.thingsboard.trendz.generator.model.ModelEntity;

import java.util.Set;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class City implements ModelEntity {

    private String systemName;
    private String systemLabel;

    private long population;

    private Set<Region> regions;
    private Set<PumpStation> pumpStations;
}

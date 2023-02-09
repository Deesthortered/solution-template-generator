package org.thingsboard.trendz.generator.solution.watermetering.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.thingsboard.trendz.generator.model.ModelEntity;

import java.util.Set;

@Getter
@ToString
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class City extends ModelEntity {

    @Override
    public String entityType() {
        return "WM City";
    }

    private String systemName;
    private String systemLabel;

    private long population;

    private Set<Region> regions;
    private Set<PumpStation> pumpStations;
}

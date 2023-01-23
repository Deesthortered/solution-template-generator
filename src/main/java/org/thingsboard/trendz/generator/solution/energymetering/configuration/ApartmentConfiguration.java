package org.thingsboard.trendz.generator.solution.energymetering.configuration;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.thingsboard.trendz.generator.model.anomaly.AnomalyInfo;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApartmentConfiguration {

    private int area;
    private boolean occupied;
    private int level;
    private long startDate;

    private List<AnomalyInfo> anomalies;
}

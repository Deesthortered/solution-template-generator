package org.thingsboard.trendz.generator.solution.energymetering;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApartmentConfiguration {

    private int floor;
    private int area;
    private int roomNumber;
    private boolean occupied;
    private int level;
    private long startDate;
    private boolean anomaly;
}
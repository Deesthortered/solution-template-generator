package org.thingsboard.trendz.generator.solution.energymetering;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Apartment {

    private String systemName;
    private String systemLabel;


    private int floor;
    private int area;
    private int roomNumber;
    private String state;


    private EnergyMeter energyMeter;
    private HeatMeter heatMeter;
}

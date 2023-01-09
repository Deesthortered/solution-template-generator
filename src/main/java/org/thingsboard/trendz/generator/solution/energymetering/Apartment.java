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

    private String name;
    private int localNumber;
    private int floor;
    private int area;
    private String state;
    private int roomNumber;


    private EnergyMeter energyMeter;
    private HeatMeter heatMeter;
}

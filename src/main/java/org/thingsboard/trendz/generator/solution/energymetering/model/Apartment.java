package org.thingsboard.trendz.generator.solution.energymetering.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Apartment {

    private UUID systemId;
    private String systemName;
    private String systemLabel;


    private int floor;
    private int area;
    private int roomNumber;
    private String state;


    private EnergyMeter energyMeter;
    private HeatMeter heatMeter;
}

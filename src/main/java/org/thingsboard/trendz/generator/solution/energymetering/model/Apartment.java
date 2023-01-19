package org.thingsboard.trendz.generator.solution.energymetering.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Apartment implements Comparable<Apartment> {

    private String systemName;
    private String systemLabel;


    private int floor;
    private int area;
    private int roomNumber;
    private String state;


    private EnergyMeter energyMeter;
    private HeatMeter heatMeter;


    @Override
    public int compareTo(@NotNull Apartment that) {
        return this.systemName.compareTo(that.systemName);
    }
}

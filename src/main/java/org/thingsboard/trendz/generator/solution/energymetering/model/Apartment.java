package org.thingsboard.trendz.generator.solution.energymetering.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.thingsboard.trendz.generator.model.ModelEntity;

@Getter
@ToString
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Apartment extends ModelEntity {

    @Override
    public String entityType() {
        return "EM) apartment";
    }

    private String systemName;
    private String systemLabel;

    private int floor;
    private int area;
    private int roomNumber;
    private String state;

    private EnergyMeter energyMeter;
    private HeatMeter heatMeter;
}

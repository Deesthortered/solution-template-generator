package org.thingsboard.trendz.generator.solution.energymetering.configuration;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.thingsboard.trendz.generator.model.ConfigurationEntity;

import java.util.List;

@Getter
@ToString
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BuildingConfiguration extends ConfigurationEntity {

    private int order;

    private String name;
    private String label;
    private String address;
    private int floorCount;
    private int apartmentsByFloorCount;
    private ApartmentConfiguration defaultApartmentConfiguration;
    private List<ApartmentConfiguration> customApartmentConfigurations;
}

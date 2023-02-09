package org.thingsboard.trendz.generator.solution.energymetering.configuration;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.thingsboard.trendz.generator.model.ConfigurationEntity;

import java.util.HashMap;
import java.util.Map;

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
    private Map<Integer, Map<Integer, ApartmentConfiguration>> floorApartmentToConfigurationMap;

    public ApartmentConfiguration getApartmentConfiguration(int floor, int number) {
        if (this.floorApartmentToConfigurationMap == null) {
            this.floorApartmentToConfigurationMap = new HashMap<>();
        }

        return this.floorApartmentToConfigurationMap
                .computeIfAbsent(floor, (key) -> new HashMap<>())
                .computeIfAbsent(number, (key) -> defaultApartmentConfiguration);
    }

    public void setApartmentConfiguration(int floor, int number, ApartmentConfiguration apartmentConfiguration) {
        if (this.floorApartmentToConfigurationMap == null) {
            this.floorApartmentToConfigurationMap = new HashMap<>();
        }

        this.floorApartmentToConfigurationMap
                .computeIfAbsent(floor, (key) -> new HashMap<>())
                .put(number, apartmentConfiguration);
    }
}

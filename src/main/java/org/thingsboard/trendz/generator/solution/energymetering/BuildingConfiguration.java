package org.thingsboard.trendz.generator.solution.energymetering;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.HashMap;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BuildingConfiguration {

    private String name;
    private String label;
    private String address;
    private int floorCount;
    private int apartmentsByFloorCount;
    private ApartmentConfiguration defaultApartmentConfiguration;
    private Map<Integer, Map<Integer, ApartmentConfiguration>> floorApartmentToConfigurationMap = new HashMap<>();

    public ApartmentConfiguration getApartmentConfiguration(int floor, int number) {
        return this.floorApartmentToConfigurationMap
                .computeIfAbsent(floor, (key) -> new HashMap<>())
                .computeIfAbsent(number, (key) -> defaultApartmentConfiguration);
    }

    public void setApartmentConfiguration(int floor, int number, ApartmentConfiguration apartmentConfiguration) {
        this.floorApartmentToConfigurationMap
                .computeIfAbsent(floor, (key) -> new HashMap<>())
                .put(number, apartmentConfiguration);
    }
}

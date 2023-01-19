package org.thingsboard.trendz.generator.solution.energymetering.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.jetbrains.annotations.NotNull;

import java.util.Set;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Building implements Comparable<Building> {

    private String systemName;
    private String systemLabel;

    private String address;

    private Set<Apartment> apartments;


    @Override
    public int compareTo(@NotNull Building that) {
        return this.systemName.compareTo(that.systemName);
    }
}

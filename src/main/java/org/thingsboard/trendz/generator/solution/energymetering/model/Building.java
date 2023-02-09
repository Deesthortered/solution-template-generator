package org.thingsboard.trendz.generator.solution.energymetering.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.thingsboard.trendz.generator.model.ModelEntity;

import java.util.Set;

@Getter
@ToString
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Building extends ModelEntity {

    @Override
    public String entityType() {
        return "EM) building";
    }

    private String systemName;
    private String systemLabel;

    private String address;

    private Set<Apartment> apartments;
}


package org.thingsboard.trendz.generator.solution.greenhouse.configuration;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.thingsboard.trendz.generator.model.ConfigurationEntity;
import org.thingsboard.trendz.generator.solution.greenhouse.model.PlantType;

@Getter
@ToString
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GreenhouseConfiguration extends ConfigurationEntity {

    private int order;

    private String name;
    private PlantType plantType;
    private int sectionHeight;
    private int sectionWidth;
}

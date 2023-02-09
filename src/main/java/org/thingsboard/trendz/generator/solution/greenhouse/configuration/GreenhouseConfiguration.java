package org.thingsboard.trendz.generator.solution.greenhouse.configuration;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.thingsboard.trendz.generator.model.ConfigurationEntity;

@Getter
@ToString
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GreenhouseConfiguration extends ConfigurationEntity {

    private int order;
}

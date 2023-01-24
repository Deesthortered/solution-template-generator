package org.thingsboard.trendz.generator.solution.watermetering.configuration;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.thingsboard.trendz.generator.solution.watermetering.model.ConsumerType;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConsumerConfiguration {

    private String index;
    private ConsumerType type;
}

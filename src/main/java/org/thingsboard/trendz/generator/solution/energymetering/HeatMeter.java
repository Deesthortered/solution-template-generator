package org.thingsboard.trendz.generator.solution.energymetering;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HeatMeter {

    private String systemName;
    private String systemLabel;
}

package org.thingsboard.trendz.generator.solution.watermetering.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.thingsboard.trendz.generator.model.ModelEntity;
import org.thingsboard.trendz.generator.model.tb.Telemetry;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Consumer implements ModelEntity {

    private String systemName;
    private String systemLabel;

    private String type;
    private Telemetry<Long> consumption;
}

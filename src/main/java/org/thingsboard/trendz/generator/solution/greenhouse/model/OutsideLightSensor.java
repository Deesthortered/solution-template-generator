package org.thingsboard.trendz.generator.solution.greenhouse.model;

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
public class OutsideLightSensor implements ModelEntity {

    private String systemName;
    private String systemLabel;

    private Telemetry<Integer> light;
}
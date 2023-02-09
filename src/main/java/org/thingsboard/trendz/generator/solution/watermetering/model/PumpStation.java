package org.thingsboard.trendz.generator.solution.watermetering.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.thingsboard.trendz.generator.model.ModelEntity;
import org.thingsboard.trendz.generator.model.tb.Telemetry;

@Getter
@ToString
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PumpStation extends ModelEntity {

    @Override
    public String entityType() {
        return "WM Pump Station";
    }

    private String systemName;
    private String systemLabel;

    private Telemetry<Long> provided;
}

package org.thingsboard.trendz.generator.solution.greenhouse.model;

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
public class OutsideAirWarmHumiditySensor extends ModelEntity {

    @Override
    public String entityType() {
        return "GR outside air warm humidity sensor";
    }

    private String systemName;
    private String systemLabel;
    private String fromGreenhouse;

    private Telemetry<Integer> temperatureOut;
    private Telemetry<Integer> humidityOut;
}
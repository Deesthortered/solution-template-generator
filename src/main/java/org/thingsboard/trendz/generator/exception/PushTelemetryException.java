package org.thingsboard.trendz.generator.exception;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import org.thingsboard.trendz.generator.model.tb.Telemetry;

@Getter
@EqualsAndHashCode(callSuper = true)
public class PushTelemetryException extends SolutionTemplateGeneratorException {

    private final String telemetryName;

    public PushTelemetryException(Telemetry<?> telemetry) {
        super("Pushing telemetry failed, name: " + telemetry.getName());
        this.telemetryName = telemetry.getName();
    }
}

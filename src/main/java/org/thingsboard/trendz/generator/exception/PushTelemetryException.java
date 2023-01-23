package org.thingsboard.trendz.generator.exception;


import org.thingsboard.trendz.generator.model.tb.Telemetry;

public class PushTelemetryException extends SolutionTemplateGeneratorException {

    private final String telemetryName;

    public PushTelemetryException(Telemetry<?> telemetry) {
        this.telemetryName = telemetry.getName();
    }
}

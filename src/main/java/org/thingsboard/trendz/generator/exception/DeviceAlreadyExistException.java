package org.thingsboard.trendz.generator.exception;

import org.thingsboard.server.common.data.Device;

public class DeviceAlreadyExistException extends SolutionTemplateGeneratorException {

    private final Device device;

    public DeviceAlreadyExistException(Device device) {
        this.device = device;
    }
}

package org.thingsboard.trendz.generator.exception;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import org.thingsboard.server.common.data.Device;

@Getter
@EqualsAndHashCode(callSuper = true)
public class DeviceAlreadyExistException extends SolutionTemplateGeneratorException {

    private final Device device;

    public DeviceAlreadyExistException(Device device) {
        super("Device is already exists: " + device.getName());
        this.device = device;
    }
}

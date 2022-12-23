package org.thingsboard.trendz.generator.utils;

import java.util.UUID;

public class UUIDUtils {

    public static class UUIDException extends RuntimeException {
        public UUIDException(String input) {
            super("Can not parse UUID from string: " + input);
        }
    }

    public static UUID parse(String input) {
        try {
            return UUID.fromString(input);
        } catch (Exception e) {
            throw new UUIDException(input);
        }
    }
}

package org.thingsboard.trendz.generator.utils;

public class EnvUtils {

    public static final String HOSTNAME = "HOSTNAME";

    public static String getEnv(String key, String defaultValue) {
        String env = System.getenv(key);
        return (env == null)
                ? defaultValue
                : env;
    }

    public static String getEnv(String key) {
        return getEnv(key, null);
    }
}

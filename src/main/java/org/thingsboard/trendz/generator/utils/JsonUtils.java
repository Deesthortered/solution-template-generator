package org.thingsboard.trendz.generator.utils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

public class JsonUtils {

    private static final ObjectMapper objectMapper;

    static {
        objectMapper = new ObjectMapper();
    }


    public static ObjectMapper getObjectMapper() {
        return objectMapper;
    }

    public static <T> String toJson(T data) {
        try {
            return objectMapper.writeValueAsString(data);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Json error during serializing, class:" + data.getClass().getName(), e);
        }
    }

    public static <T> T fromJson(String jsonData, Class<T> clazz) {
        try {
            return objectMapper.readValue(jsonData, clazz);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Json error during deserializing, class:" + clazz.getName(), e);
        }
    }
}

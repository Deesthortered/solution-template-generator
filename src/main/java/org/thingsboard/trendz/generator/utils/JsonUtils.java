package org.thingsboard.trendz.generator.utils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;

public class JsonUtils {

    private static final ObjectMapper objectMapper;

    static {
        objectMapper = JsonMapper.builder()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                .build();
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

    public static JsonNode makeNodeFromPojo(Object obj) {
        return objectMapper.valueToTree(obj);
    }

    public static JsonNode makeNodeFromRaw(String jsonData) {
        try {
            return objectMapper.readTree(jsonData);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Json error during reading tree.", e);
        }
    }
}

package com.codejam.commons.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;

@Component
public class JsonUtils {

    public static String toJson(Object value) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            return mapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("JSON serialization failed", e);
        }
    }

    public static  <T> T fromJson(String json, Class<T> type) {
        if (!StringUtils.hasText(json)) return null;

        try {
            ObjectMapper mapper = new ObjectMapper();
            return mapper.readValue(json, type);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid JSON", e);
        }
    }

    public static <T> List<T> fromJsonList(String json, Class<T> type) {
        if (!StringUtils.hasText(json)) return List.of();
        try {
            ObjectMapper mapper = new ObjectMapper();
            return mapper.readValue(json, mapper.getTypeFactory()
                    .constructCollectionType(List.class, type));
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid JSON list", e);
        }
    }

}

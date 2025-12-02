package com.codejam.auth.dto.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response object containing OAuth authentication data.
 * Used for storing and retrieving OAuth callback data from Redis.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class OAuthCodeResponse {
    private String token;
    private String email;
    private String name;
    private String userId;
    private String avatar;
    private String codeChallenge;

    private static final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Converts this object to JSON string for Redis storage.
     */
    public String toJson() {
        try {
            return objectMapper.writeValueAsString(this);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize OAuthCodeResponse", e);
        }
    }

    /**
     * Creates OAuthCodeResponse from JSON string.
     */
    public static OAuthCodeResponse fromJson(String json) {
        try {
            return objectMapper.readValue(json, OAuthCodeResponse.class);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to deserialize OAuthCodeResponse", e);
        }
    }
}

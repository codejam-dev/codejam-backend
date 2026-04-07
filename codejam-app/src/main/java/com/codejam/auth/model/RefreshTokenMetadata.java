package com.codejam.auth.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class RefreshTokenMetadata {

    private String userId;
    private String deviceId;
    private boolean revoked;
    private long expiresAt;
    private String parentJti;

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public String toJson() {
        try {
            return MAPPER.writeValueAsString(this);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize refresh metadata", e);
        }
    }

    public static RefreshTokenMetadata fromJson(String json) {
        try {
            return MAPPER.readValue(json, RefreshTokenMetadata.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to deserialize refresh metadata", e);
        }
    }
}

package com.codejam.auth.dto.response;

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
public class OAuthCodeResponse {
    private String token;
    private String email;
    private String name;
    private String userId;
    private String avatar;
    private String codeChallenge;

    private static final ObjectMapper objectMapper = new ObjectMapper();

    public String toJson() {
        try {
            return objectMapper.writeValueAsString(this);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize OAuthCodeResponse", e);
        }
    }

    public static OAuthCodeResponse fromJson(String json) {
        try {
            return objectMapper.readValue(json, OAuthCodeResponse.class);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to deserialize OAuthCodeResponse", e);
        }
    }
}


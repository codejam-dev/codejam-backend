package com.codejam.auth.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

@Builder
@Data
public class RegisterResponse {
    String userId;
    String email;
    String name;
    @JsonProperty("isEnabled")
    boolean isEnabled;
    String message;
}


package com.codejam.auth.dto.response;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AuthResponse {
    @JsonAlias("token")
    private String accessToken;

    private String tokenType;
    private String userId;
    private String name;
    private String email;
    private String avatar;

    @JsonProperty("isEnabled")
    private boolean isEnabled;

    private String message;
}

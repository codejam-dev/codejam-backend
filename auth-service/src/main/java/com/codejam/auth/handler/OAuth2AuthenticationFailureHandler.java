package com.codejam.auth.handler;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationFailureHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;

@Component
@Slf4j
public class OAuth2AuthenticationFailureHandler extends SimpleUrlAuthenticationFailureHandler {

    @Value("${app.oauth.failure-redirect}")
    private String redirectUrl;

    @Override
    public void onAuthenticationFailure(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException exception) throws IOException {

        log.error("OAuth authentication failed: {}", exception.getMessage());
        
        String targetUrl = UriComponentsBuilder.fromUriString(redirectUrl)
                .queryParam("error", exception.getLocalizedMessage())
                .build().toUriString();

        getRedirectStrategy().sendRedirect(request, response, targetUrl);
    }
}

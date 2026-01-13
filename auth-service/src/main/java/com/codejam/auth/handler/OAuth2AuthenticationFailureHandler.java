package com.codejam.auth.handler;

import com.codejam.auth.config.MicroserviceConfig;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationFailureHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;

@Component
@Slf4j
@RequiredArgsConstructor
public class OAuth2AuthenticationFailureHandler extends SimpleUrlAuthenticationFailureHandler {

    private final MicroserviceConfig microserviceConfig;

    @Override
    public void onAuthenticationFailure(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException exception) throws IOException {

        log.error("OAuth authentication failed: {}", exception.getMessage());
        
        // Extract error message and type from OAuth2AuthenticationException
        String errorMessage = exception.getLocalizedMessage();
        String errorType = "oauth_failed"; // default error type
        
        if (exception instanceof OAuth2AuthenticationException oauth2Exception) {
            OAuth2Error oauth2Error = oauth2Exception.getError();
            if (oauth2Error != null) {
                errorType = oauth2Error.getErrorCode();
                errorMessage = oauth2Error.getDescription() != null 
                    ? oauth2Error.getDescription() 
                    : oauth2Error.getErrorCode();
                log.error("OAuth authentication failed: {} - {}", errorType, errorMessage);
            }
        }
        
        // Parse the failure redirect URL and replace existing error parameters
        String failureRedirect = microserviceConfig.getOauth().getFailureRedirect();
        UriComponentsBuilder uriBuilder = UriComponentsBuilder.fromUriString(failureRedirect);
        
        // Replace the error parameter if it exists, otherwise add it
        uriBuilder.replaceQueryParam("error", errorType);
        uriBuilder.replaceQueryParam("errorMessage", errorMessage);
        
        String targetUrl = uriBuilder.build().toUriString();

        getRedirectStrategy().sendRedirect(request, response, targetUrl);
    }
}

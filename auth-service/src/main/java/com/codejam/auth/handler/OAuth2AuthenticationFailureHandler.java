package com.codejam.auth.handler;

import com.codejam.auth.config.MicroserviceConfig;
import com.codejam.commons.exception.CustomException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
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
        
        // Extract error message and type - if it's an OAuth2AuthenticationException wrapping a CustomException,
        // get the actual CustomException message and type
        String errorMessage = exception.getLocalizedMessage();
        String errorType = "oauth_failed"; // default error type
        
        if (exception instanceof OAuth2AuthenticationException oauth2Exception) {
            Throwable cause = oauth2Exception.getCause();
            if (cause instanceof CustomException customException) {
                errorMessage = customException.getCustomMessage();
                errorType = customException.getErrorType();
                log.error("OAuth authentication failed with CustomException: {} - {}", 
                    customException.getErrorType(), customException.getCustomMessage());
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

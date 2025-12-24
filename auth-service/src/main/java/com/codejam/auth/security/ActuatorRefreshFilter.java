package com.codejam.auth.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Security filter for /actuator/refresh endpoint
 * Validates API key from X-Actuator-Token header for machine access
 * 
 * This ensures actuator refresh is only accessible to authorized control-plane services
 * (e.g., Config Server webhook) and not exposed to user-facing traffic.
 */
@Component
@Order(1) // Run before Spring Security
public class ActuatorRefreshFilter extends OncePerRequestFilter {

    @Value("${actuator.refresh.token:}")
    private String actuatorRefreshToken;

    @Override
    protected void doFilterInternal(HttpServletRequest request, 
                                   HttpServletResponse response, 
                                   FilterChain filterChain) throws ServletException, IOException {
        
        String path = request.getRequestURI();
        
        // Only apply to actuator refresh endpoint
        if ("/actuator/refresh".equals(path) || path.startsWith("/actuator/refresh")) {
            String token = request.getHeader("X-Actuator-Token");
            
            // If token is configured, validate it
            if (actuatorRefreshToken != null && !actuatorRefreshToken.isEmpty()) {
                if (token == null || !token.equals(actuatorRefreshToken)) {
                    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    response.setContentType("application/json");
                    response.getWriter().write("{\"error\":\"Invalid or missing actuator token\"}");
                    return;
                }
            } else {
                // If no token configured, deny access (security: fail closed)
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.setContentType("application/json");
                response.getWriter().write("{\"error\":\"Actuator refresh token not configured\"}");
                return;
            }
        }
        
        filterChain.doFilter(request, response);
    }
}


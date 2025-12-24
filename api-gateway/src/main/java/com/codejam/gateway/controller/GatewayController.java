package com.codejam.gateway.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Gateway Controller
 * 
 * NOTE: Gateway health is exposed via /actuator/health (Spring Boot Actuator)
 * This controller is kept minimal - no downstream health aggregation.
 * Gateway must NOT call /actuator/** of downstream services (security boundary).
 */
@RestController
public class GatewayController {

    /**
     * Simple gateway health endpoint (alternative to /actuator/health)
     * Returns basic status without aggregating downstream services.
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of("status", "UP", "service", "api-gateway"));
    }
}


package com.codejam.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/webhook")
public class WebhookController {

    private static final Logger logger = LoggerFactory.getLogger(WebhookController.class);
    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${webhook.secret:}")
    private String webhookSecret;

    @Value("${services.auth-service.url:http://localhost:8081}")
    private String authServiceUrl;

    @Value("${services.api-gateway.url:http://localhost:8080}")
    private String apiGatewayUrl;

    /**
     * GitHub webhook endpoint
     * Receives push events from GitHub and triggers refresh on all services
     */
    @PostMapping("/github")
    public ResponseEntity<Map<String, Object>> handleGitHubWebhook(
            @RequestBody(required = false) Map<String, Object> payload,
            @RequestHeader(value = "X-GitHub-Event", required = false) String event,
            @RequestHeader(value = "X-Hub-Signature-256", required = false) String signature) {

        logger.info("📥 Received GitHub webhook event: {}", event);

        // Verify it's a push event
        if (!"push".equals(event)) {
            logger.info("⏭️  Ignoring non-push event: {}", event);
            return ResponseEntity.ok(Map.of("message", "Event ignored", "event", event));
        }

        // Verify webhook secret if configured
        if (webhookSecret != null && !webhookSecret.isEmpty() && signature == null) {
            logger.warn("⚠️  Webhook secret configured but signature missing");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Missing signature"));
        }

        logger.info("🔄 Config changed in Git, triggering refresh on all services...");

        Map<String, Object> results = new HashMap<>();
        results.put("event", event);
        results.put("timestamp", System.currentTimeMillis());

        // Refresh all services
        refreshService("auth-service", authServiceUrl, results);
        refreshService("api-gateway", apiGatewayUrl, results);

        // Refresh Config Server itself
        refreshConfigServer(results);

        logger.info("✅ Webhook processed successfully");
        return ResponseEntity.ok(results);
    }

    /**
     * Manual refresh trigger endpoint (for testing)
     */
    @PostMapping("/refresh")
    public ResponseEntity<Map<String, Object>> manualRefresh() {
        logger.info("🔄 Manual refresh triggered");

        Map<String, Object> results = new HashMap<>();
        results.put("trigger", "manual");
        results.put("timestamp", System.currentTimeMillis());

        refreshService("auth-service", authServiceUrl, results);
        refreshService("api-gateway", apiGatewayUrl, results);
        refreshConfigServer(results);

        return ResponseEntity.ok(results);
    }

    private void refreshService(String serviceName, String serviceUrl, Map<String, Object> results) {
        try {
            // Auth service has context path /v1/api, so actuator endpoints are under it
            String actuatorPath = "/actuator/refresh";
            if ("auth-service".equals(serviceName)) {
                actuatorPath = "/actuator/refresh";
            }
            String refreshUrl = serviceUrl + actuatorPath;
            logger.info("🔄 Refreshing {} at {}", serviceName, refreshUrl);

            // Set proper headers for actuator refresh endpoint
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            // Send empty JSON body {} as required by actuator refresh endpoint
            HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(Map.of(), headers);

            // Actuator refresh endpoint returns a JSON array of changed property keys
            ResponseEntity<Object> response = restTemplate.exchange(
                    refreshUrl,
                    HttpMethod.POST,
                    requestEntity,
                    Object.class
            );

            if (response.getStatusCode().is2xxSuccessful()) {
                logger.info("✅ {} refreshed successfully", serviceName);
                // Extract changed keys if response is a list
                Object body = response.getBody();
                if (body instanceof List) {
                    List<?> changedKeys = (List<?>) body;
                    results.put(serviceName, Map.of(
                            "status", "success",
                            "url", refreshUrl,
                            "changedKeys", changedKeys.size()
                    ));
                } else {
                    results.put(serviceName, Map.of("status", "success", "url", refreshUrl));
                }
            } else {
                logger.warn("⚠️  {} refresh returned: {}", serviceName, response.getStatusCode());
                results.put(serviceName, Map.of("status", "warning", "code", response.getStatusCode().value()));
            }
        } catch (Exception e) {
            logger.error("❌ Failed to refresh {}: {}", serviceName, e.getMessage());
            results.put(serviceName, Map.of("status", "error", "error", e.getMessage()));
        }
    }

    private void refreshConfigServer(Map<String, Object> results) {
        try {
            String refreshUrl = "http://localhost:8888/actuator/refresh";
            logger.info("🔄 Refreshing Config Server at {}", refreshUrl);

            // Set proper headers for actuator refresh endpoint
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            // Send empty JSON body {} as required by actuator refresh endpoint
            HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(Map.of(), headers);

            // Actuator refresh endpoint returns a JSON array of changed property keys
            ResponseEntity<Object> response = restTemplate.exchange(
                    refreshUrl,
                    HttpMethod.POST,
                    requestEntity,
                    Object.class
            );

            if (response.getStatusCode().is2xxSuccessful()) {
                logger.info("✅ Config Server refreshed successfully");
                // Extract changed keys if response is a list
                Object body = response.getBody();
                if (body instanceof List) {
                    List<?> changedKeys = (List<?>) body;
                    results.put("config-server", Map.of(
                            "status", "success",
                            "changedKeys", changedKeys.size()
                    ));
                } else {
                    results.put("config-server", Map.of("status", "success"));
                }
            } else {
                logger.warn("⚠️  Config Server refresh returned: {}", response.getStatusCode());
                results.put("config-server", Map.of("status", "warning", "code", response.getStatusCode().value()));
            }
        } catch (Exception e) {
            logger.error("❌ Failed to refresh Config Server: {}", e.getMessage());
            results.put("config-server", Map.of("status", "error", "error", e.getMessage()));
        }
    }
}


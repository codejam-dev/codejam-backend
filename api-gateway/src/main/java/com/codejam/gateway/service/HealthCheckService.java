package com.codejam.gateway.service;

import com.codejam.commons.dto.BaseResponse;
import com.codejam.gateway.dto.Healthcheckdto;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;

@Service
public class HealthCheckService {

    private final RouteLocator routeLocator;
    private final RestTemplate restTemplate;

    public HealthCheckService(RouteLocator routeLocator, RestTemplate restTemplate) {
        this.routeLocator = routeLocator;
        this.restTemplate = restTemplate;

    }

    public BaseResponse healthCheck() {
        List<Healthcheckdto> services = new ArrayList<>();

        routeLocator.getRoutes().subscribe(route -> {
            try {
                Healthcheckdto health = restTemplate.getForObject(route.getUri() + "/actuator/health", Healthcheckdto.class);
                services.add(new Healthcheckdto(route.getId(), health != null ? health.getStatus() : "DOWN"));
            } catch (Exception e) {
                services.add(new Healthcheckdto(route.getId(), "DOWN"));
            }
        });


        return BaseResponse.success(services);
    }

}


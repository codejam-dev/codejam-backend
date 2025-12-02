package com.codejam.gateway.controller;

import com.codejam.commons.dto.BaseResponse;
import com.codejam.gateway.dto.Healthcheckdto;
import com.codejam.gateway.service.HealthCheckService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class GatewayController  {

    private  final HealthCheckService healthCheckService;

    @Autowired
    public GatewayController(HealthCheckService healthCheckService) {
        this.healthCheckService = healthCheckService;
    }

    @GetMapping("/health")
    public BaseResponse healthCheck(){
        return healthCheckService.healthCheck();
    }

}


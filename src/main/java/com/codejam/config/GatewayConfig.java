package com.codejam.config;

import com.codejam.gateway.config.MicroserviceConfig;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(MicroserviceConfig.class)
public class GatewayConfig {
}

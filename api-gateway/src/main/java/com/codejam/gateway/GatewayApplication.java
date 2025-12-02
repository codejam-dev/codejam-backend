package com.codejam.gateway;

import com.codejam.gateway.config.MicroserviceConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication(scanBasePackages = {
    "com.codejam.gateway",
    "com.codejam.commons"
})
@EnableConfigurationProperties(MicroserviceConfig.class)
public class GatewayApplication {

	public static void main(String[] args) {
		SpringApplication.run(GatewayApplication.class, args);
	}

}


package com.codejam.auth;

import com.codejam.auth.config.MicroserviceConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication(scanBasePackages = {
    "com.codejam.auth",
    "com.codejam.commons"
})
@EnableConfigurationProperties(MicroserviceConfig.class)
public class AuthServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(AuthServiceApplication.class, args);
    }

}

package com.codejam;

import com.codejam.auth.config.MicroserviceConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication(scanBasePackages = {"com.codejam"})
@EntityScan(basePackages = {"com.codejam.auth.model", "com.codejam.execution.model"})
@EnableJpaRepositories(basePackages = {"com.codejam.auth.repository", "com.codejam.execution.repository"})
@EnableConfigurationProperties(MicroserviceConfig.class)
public class CodeJamApplication {

    public static void main(String[] args) {
        SpringApplication.run(CodeJamApplication.class, args);
    }
}

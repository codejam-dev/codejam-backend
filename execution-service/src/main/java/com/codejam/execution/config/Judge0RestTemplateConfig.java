package com.codejam.execution.config;

import com.codejam.execution.exception.RateLimitExceededException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.DefaultResponseErrorHandler;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.time.Duration;

@Configuration
@ConditionalOnProperty(name = "app.executor.type", havingValue = "judge0")
public class Judge0RestTemplateConfig {

    @Bean
    public RestTemplate judge0RestTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(35));

        RestTemplate restTemplate = new RestTemplate(factory);
        
        // Custom error handler for rate limit exceptions
        restTemplate.setErrorHandler(new DefaultResponseErrorHandler() {
            @Override
            public void handleError(org.springframework.http.client.ClientHttpResponse response) 
                    throws IOException {
                if (response.getStatusCode().value() == 429) {
                    throw new RateLimitExceededException(
                        "Judge0 API rate limit exceeded. Please try again later."
                    );
                }
                super.handleError(response);
            }
        });

        return restTemplate;
    }
}

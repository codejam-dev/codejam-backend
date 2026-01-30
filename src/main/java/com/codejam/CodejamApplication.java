package com.codejam;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "com.codejam")
public class CodejamApplication {

    public static void main(String[] args) {
        SpringApplication.run(CodejamApplication.class, args);
    }
}

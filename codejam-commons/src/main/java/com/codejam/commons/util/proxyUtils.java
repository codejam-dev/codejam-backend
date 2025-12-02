package com.codejam.commons.util;

import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.stream.Collectors;

@Component
public class proxyUtils {

    public final ObjectUtil validationUtils;

    public proxyUtils(ObjectUtil validationUtils) {
        this.validationUtils = validationUtils;
    }

    public String generateRedisKey(String prefix, String... identifierParts) {
        if (validationUtils.isNullOrEmpty(prefix) || identifierParts == null || identifierParts.length == 0) {
            throw new IllegalArgumentException("Prefix cannot be null or empty");
        }
        String identifier = Arrays.stream(identifierParts)
                .filter(part -> !validationUtils.isNullOrEmpty(part))
                .collect(Collectors.joining("_"));
        return prefix + "_" + identifier;
    }

}


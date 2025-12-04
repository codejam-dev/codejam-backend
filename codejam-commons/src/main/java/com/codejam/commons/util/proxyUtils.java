package com.codejam.commons.util;

import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.stream.Collectors;

@Component
public class proxyUtils {

    public String generateRedisKey(String prefix, String... identifierParts) {
        if (ObjectUtils.isNullOrEmpty(prefix) || identifierParts == null || identifierParts.length == 0) {
            throw new IllegalArgumentException("Prefix cannot be null or empty");
        }
        String identifier = Arrays.stream(identifierParts)
                .filter(part -> !ObjectUtils.isNullOrEmpty(part))
                .collect(Collectors.joining("_"));
        return prefix + "_" + identifier;
    }



}


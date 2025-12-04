package com.codejam.commons.util;

import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;

@Component
public class ObjectUtils {

    public static boolean isNullOrEmpty(String str) {
        return str == null || str.trim().isEmpty();
    }

    public static boolean isNullOrEmpty(Object obj) {
        if (obj == null) {
            return true;
        }

        if (obj.getClass().isArray()) {
            return java.lang.reflect.Array.getLength(obj) == 0;
        }

        return switch (obj) {
            case String s -> s.trim().isEmpty();
            case Collection<?> c -> c.isEmpty();
            case Map<?, ?> m -> m.isEmpty();
            case Optional<?> o -> o.isEmpty();
            default -> false;
        };
    }
}


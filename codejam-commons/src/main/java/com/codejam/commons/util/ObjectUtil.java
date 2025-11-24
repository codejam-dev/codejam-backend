package com.codejam.commons.util;

import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;

@Component
public class ObjectUtil {

    public static boolean isNullOrEmpty(String str) {
        return str == null || str.trim().isEmpty();
    }

    public static boolean isNullOrEmpty(Object obj) {
        if (obj == null) return true;
        if (obj instanceof String s) return s.trim().isEmpty();
        if (obj instanceof Collection<?> collection) return collection.isEmpty();
        if (obj instanceof Map<?, ?> map) return map.isEmpty();
        if (obj instanceof Optional<?> optional) return optional.isEmpty();
        if (obj.getClass().isArray()) {
            return java.lang.reflect.Array.getLength(obj) == 0;
        }
        return false;
    }
}


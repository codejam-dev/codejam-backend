package com.codejam.gateway.filter;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Wraps the request to add X-User-* headers for downstream controllers (e.g. auth).
 */
public final class UserHeadersRequestWrapper extends HttpServletRequestWrapper {

    private final Map<String, String> headerOverrides = new HashMap<>();

    public UserHeadersRequestWrapper(HttpServletRequest request, String userId, String email, String name, String scopes) {
        super(request);
        if (userId != null) headerOverrides.put("X-User-Id", userId);
        if (email != null) headerOverrides.put("X-User-Email", email);
        if (name != null) headerOverrides.put("X-User-Name", name);
        if (scopes != null) headerOverrides.put("X-User-Scopes", scopes);
    }

    @Override
    public String getHeader(String name) {
        String override = headerOverrides.get(name);
        return override != null ? override : super.getHeader(name);
    }

    @Override
    public java.util.Enumeration<String> getHeaders(String name) {
        String override = headerOverrides.get(name);
        return override != null ? Collections.enumeration(Collections.singletonList(override)) : super.getHeaders(name);
    }
}

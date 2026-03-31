package com.codejam.infrastructure.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;

/**
 * Supplies X-User-Id and X-User-Email from validated JWT for OTP endpoints (parity with former gateway).
 */
public class JwtAugmentedHttpServletRequest extends HttpServletRequestWrapper {

    private final String userId;
    private final String email;

    public JwtAugmentedHttpServletRequest(HttpServletRequest request, String userId, String email) {
        super(request);
        this.userId = userId;
        this.email = email;
    }

    @Override
    public String getHeader(String name) {
        if ("X-User-Id".equalsIgnoreCase(name) && userId != null) {
            return userId;
        }
        if ("X-User-Email".equalsIgnoreCase(name) && email != null) {
            return email;
        }
        return super.getHeader(name);
    }
}

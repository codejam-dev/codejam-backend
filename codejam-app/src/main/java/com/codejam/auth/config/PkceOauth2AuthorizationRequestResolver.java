package com.codejam.auth.config;

import com.codejam.commons.util.ObjectUtils;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.stereotype.Component;

import static com.codejam.auth.util.Constants.*;

@Slf4j
@Component
@RequiredArgsConstructor
public class PkceOauth2AuthorizationRequestResolver implements OAuth2AuthorizationRequestResolver {
    private final ClientRegistrationRepository clientRegistrationRepository;

    private OAuth2AuthorizationRequestResolver defaultResolver() {
        return new DefaultOAuth2AuthorizationRequestResolver(clientRegistrationRepository, "/v1/api/auth/oauth2/authorization");
    }

    @Override
    public OAuth2AuthorizationRequest resolve(HttpServletRequest request) {
        return resolveWithPkce(request, null);
    }

    @Override
    public OAuth2AuthorizationRequest resolve(
            HttpServletRequest request,
            String clientRegistrationId) {
        return resolveWithPkce(request, clientRegistrationId);
    }

    /**
     * Resolves OAuth2AuthorizationRequest and handles PKCE code_challenge if present.
     * If code_challenge is valid and uses S256 method, it is stored in the
     * HTTP session for later verification during token exchange.
     */
    private OAuth2AuthorizationRequest resolveWithPkce(HttpServletRequest request, String clientRegistrationId) {
        String codeChallenge = request.getParameter(PKCE_PARAM_CODE_CHALLENGE);
        String codeChallengeMethod = request.getParameter(PKCE_PARAM_CODE_CHALLENGE_METHOD);

        OAuth2AuthorizationRequest authRequest = ObjectUtils.isNullOrEmpty(clientRegistrationId) ?
                defaultResolver().resolve(request) :
                defaultResolver().resolve(request, clientRegistrationId);

        if (authRequest != null && !ObjectUtils.isNullOrEmpty(codeChallenge)) {
            if (PKCE_CODE_CHALLENGE_METHOD_S256.equals(codeChallengeMethod) && isValidCodeChallenge(codeChallenge)) {
                request.getSession().setAttribute(SESSION_ATTRIBUTE_CODE_CHALLENGE, codeChallenge);
                log.info("PKCE code_challenge stored in session for our own validation. Method: {}", codeChallengeMethod);
            } else {
                log.warn("Invalid PKCE code_challenge format or method. Method: {}", codeChallengeMethod);
            }
        }
        return authRequest;
    }


    /**
     * Validates code_challenge format according to RFC 7636.
     * - Must be Base64 URL-safe encoded
     * - Length: 43-128 characters
     * - Base64 URL-safe (only contains: A-Z, a-z, 0-9, -, _)
     */
    private boolean isValidCodeChallenge(String codeChallenge) {
        return !(codeChallenge.length() < 43 || codeChallenge.length() > 128) &&
               codeChallenge.matches("^[A-Za-z0-9_-]+$");
    }
}

package com.codejam.auth.config;

import com.codejam.util.Constants;
import com.codejam.commons.util.ObjectUtil;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
public class PkceOauth2AuthorizationRequestResolver implements OAuth2AuthorizationRequestResolver {
    private final ClientRegistrationRepository clientRegistrationRepository;

    private OAuth2AuthorizationRequestResolver defaultResolver() {
        return new DefaultOAuth2AuthorizationRequestResolver(clientRegistrationRepository, "/auth/oauth2/authorization");
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

    private OAuth2AuthorizationRequest resolveWithPkce(HttpServletRequest request, String clientRegistrationId) {
        String codeChallenge = request.getParameter(Constants.PKCE_PARAM_CODE_CHALLENGE);
        String codeChallengeMethod = request.getParameter(Constants.PKCE_PARAM_CODE_CHALLENGE_METHOD);

        OAuth2AuthorizationRequest authRequest = ObjectUtil.isNullOrEmpty(clientRegistrationId) ?
                defaultResolver().resolve(request) :
                defaultResolver().resolve(request, clientRegistrationId);

        if (authRequest != null && !ObjectUtil.isNullOrEmpty(codeChallenge)) {
            if (Constants.PKCE_CODE_CHALLENGE_METHOD_S256.equals(codeChallengeMethod) && isValidCodeChallenge(codeChallenge)) {
                request.getSession().setAttribute(Constants.SESSION_ATTRIBUTE_CODE_CHALLENGE, codeChallenge);
                log.info("PKCE code_challenge stored in session for validation. Method: {}", codeChallengeMethod);
            } else {
                log.warn("Invalid PKCE code_challenge format or method. Method: {}", codeChallengeMethod);
            }
        }
        return authRequest;
    }

    private boolean isValidCodeChallenge(String codeChallenge) {
        return !(codeChallenge.length() < 43 || codeChallenge.length() > 128) &&
               codeChallenge.matches("^[A-Za-z0-9_-]+$");
    }
}


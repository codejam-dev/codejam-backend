package com.codejam.auth.handler;

import com.codejam.auth.config.MicroserviceConfig;
import com.codejam.auth.model.User;
import com.codejam.auth.repository.UserRepository;
import com.codejam.auth.service.JwtService;
import com.codejam.auth.service.OAuthCodeService;
import com.codejam.commons.util.ObjectUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.util.Base64;

import static com.codejam.auth.util.Constants.SESSION_ATTRIBUTE_CODE_CHALLENGE;

@Slf4j
@Component
@RequiredArgsConstructor
public class OAuthSuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private final JwtService jwtService;
    private final UserRepository userRepository;
    private final OAuthCodeService oAuthCodeService;
    private final MicroserviceConfig microserviceConfig;

    @Override
    public void onAuthenticationSuccess(
            HttpServletRequest request,
            HttpServletResponse response,
            Authentication authentication) throws IOException {
        OAuth2User oAuth2User = (OAuth2User) authentication.getPrincipal();
        String email = oAuth2User.getAttribute("email");

        if (email == null) {
            log.warn("OAuth authentication succeeded but email is missing");
            response.sendRedirect(microserviceConfig.getOauth().getSuccessRedirect() + "?error=email_not_found");
            return;
        }

        String normalizedEmail = email.trim().toLowerCase();
        log.debug("OAuth authentication successful for email: {}", normalizedEmail);

        User user = userRepository.findByEmail(normalizedEmail)
                .orElseThrow(() -> new RuntimeException("User not found after OAuth authentication"));

        String token = jwtService.generateToken(user);
        String codeChallenge = (String) request.getSession().getAttribute(SESSION_ATTRIBUTE_CODE_CHALLENGE);

        if(ObjectUtils.isNullOrEmpty(codeChallenge)) {
            log.error("PKCE code_challenge missing in session for user: {}", email);
            response.sendRedirect(microserviceConfig.getOauth().getSuccessRedirect() + "?error=pkce_required");
            return;
        }

        if(ObjectUtils.isNullOrEmpty(user.getProfileImageUrl())){
            user.setProfileImageUrl(oAuth2User.getAttribute("picture"));
            userRepository.save(user);
        }

        // Determine avatar: prefer base64 data URL from DB if available, else use external URL
        String avatar = "";
        if (user.getProfileImage() != null && user.getProfileImage().length > 0) {
            // User has image stored in DB, convert to base64 data URL
            String base64Image = Base64.getEncoder().encodeToString(user.getProfileImage());
            // Default to JPEG, could be enhanced to detect actual content type
            avatar = "data:image/jpeg;base64," + base64Image;
            log.debug("Using base64 profile image from DB for user: {}, size: {} bytes", user.getEmail(), user.getProfileImage().length);
        } else if (!ObjectUtils.isNullOrEmpty(user.getProfileImageUrl())) {
            // Fallback to external URL if no backend image
            avatar = user.getProfileImageUrl();
            log.debug("Using external profile image URL for user: {}", user.getEmail());
        }

        String code = oAuthCodeService.generateCode(
                token,
                user.getEmail(),
                user.getName() != null ? user.getName() : "",
                user.getUserId(),
                avatar,
                codeChallenge
        );

        request.getSession().removeAttribute(SESSION_ATTRIBUTE_CODE_CHALLENGE);
        log.debug("OAuth code generated successfully. Redirecting to frontend. User: {}", normalizedEmail);
        
        String targetUrl = UriComponentsBuilder.fromUriString(microserviceConfig.getOauth().getSuccessRedirect())
                .queryParam("code", code)
                .build().toUriString();
        
        getRedirectStrategy().sendRedirect(request, response, targetUrl);
    }
}

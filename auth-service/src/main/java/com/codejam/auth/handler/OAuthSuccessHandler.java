package com.codejam.auth.handler;

import com.codejam.model.User;
import com.codejam.repository.UserRepository;
import com.codejam.service.JwtService;
import com.codejam.service.OAuthCodeService;
import com.codejam.commons.util.Constants;
import com.codejam.commons.util.ObjectUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.util.Base64;

@Component
@Slf4j
@RequiredArgsConstructor
public class OAuthSuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private final JwtService jwtService;
    private final UserRepository userRepository;
    private final OAuthCodeService oAuthCodeService;

    @Value("${app.oauth.success-redirect:http://localhost:3000/auth/success}")
    private String successRedirectUrl;

    @Override
    public void onAuthenticationSuccess(
            HttpServletRequest request,
            HttpServletResponse response,
            Authentication authentication) throws IOException {
        OAuth2User oAuth2User = (OAuth2User) authentication.getPrincipal();
        String email = oAuth2User.getAttribute("email");

        if (email == null) {
            log.warn("OAuth authentication succeeded but email is missing");
            response.sendRedirect(successRedirectUrl + "?error=email_not_found");
            return;
        }

        log.info("OAuth authentication successful for email: {}", email);

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found after OAuth authentication"));

        String token = jwtService.generateToken(user);
        String codeChallenge = (String) request.getSession().getAttribute(Constants.SESSION_ATTRIBUTE_CODE_CHALLENGE);

        if(ObjectUtil.isNullOrEmpty(codeChallenge)) {
            log.error("PKCE code_challenge missing in session for user: {}", email);
            response.sendRedirect(successRedirectUrl + "?error=pkce_required");
            return;
        }

        if(ObjectUtil.isNullOrEmpty(user.getProfileImageUrl())){
            user.setProfileImageUrl(oAuth2User.getAttribute("picture"));
            userRepository.save(user);
        }

        String avatar = "";
        if (user.getProfileImage() != null && user.getProfileImage().length > 0) {
            String base64Image = Base64.getEncoder().encodeToString(user.getProfileImage());
            avatar = "data:image/jpeg;base64," + base64Image;
            log.debug("Using base64 profile image from DB for user: {}", user.getEmail());
        } else if (!ObjectUtil.isNullOrEmpty(user.getProfileImageUrl())) {
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

        request.getSession().removeAttribute(Constants.SESSION_ATTRIBUTE_CODE_CHALLENGE);
        log.info("OAuth code generated successfully. Redirecting to frontend. User: {}", email);
        
        String targetUrl = UriComponentsBuilder.fromUriString(successRedirectUrl)
                .queryParam("code", code)
                .build().toUriString();
        
        getRedirectStrategy().sendRedirect(request, response, targetUrl);
    }
}


package com.codejam.auth.service;

import com.codejam.auth.util.AuthProvider;
import com.codejam.auth.model.User;
import com.codejam.auth.repository.UserRepository;
import com.codejam.commons.util.ObjectUtils;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Optional;


@Service
public class GoogleAuthService extends DefaultOAuth2UserService {

    private final UserRepository userRepository;
    private final RestTemplate restTemplate = new RestTemplate();

    public GoogleAuthService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest) {
        OAuth2User oAuth2User = super.loadUser(userRequest);
        String registrationId = userRequest.getClientRegistration().getRegistrationId();
        return processOAuth2User(registrationId, oAuth2User);
    }

    public OAuth2User processOAuth2User(String registrationId, OAuth2User oAuth2User) {
        String email = oAuth2User.getAttribute("email");
        String providerId = oAuth2User.getAttribute("sub");
        String firstName = oAuth2User.getAttribute("given_name");
        String lastName = oAuth2User.getAttribute("family_name");
        String profileImage = oAuth2User.getAttribute("picture");

        if (ObjectUtils.isNullOrEmpty(email)) {
            OAuth2Error oauth2Error = new OAuth2Error("GOOGLE_AUTH", "Email not found from OAuth2 provider", null);
            throw new OAuth2AuthenticationException(oauth2Error);
        }

        String normalizedEmail = email.trim().toLowerCase();
        AuthProvider authProvider = AuthProvider.valueOf(registrationId.toUpperCase());
        Optional<User> optionalUser = userRepository.findByEmail(normalizedEmail);
        User user;

        if (optionalUser.isPresent()) {
            user = optionalUser.get();

            if (profileImage != null) {
                try {
                    byte[] image = restTemplate.getForObject(profileImage, byte[].class);
                    if (image != null) {
                        user.setProfileImage(image);
                    }
                } catch (Exception e) {
                    // Profile image is optional
                }
            }

            user.setName(firstName + " " + lastName);
            user.setProfileImageUrl(profileImage);
            user.setProviderId(providerId);
            user.setProvider(authProvider);
            user.setEnabled(true);
            userRepository.save(user);
        } else {
            user = User.builder()
                    .userId(java.util.UUID.randomUUID().toString())
                    .name(firstName + " " + lastName)
                    .email(normalizedEmail)
                    .providerId(providerId)
                    .provider(authProvider)
                    .profileImageUrl(profileImage)
                    .enabled(true)
                    .build();
            userRepository.save(user);
        }

        return oAuth2User;
    }
}

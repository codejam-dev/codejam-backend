package com.codejam.auth.service;

import com.codejam.auth.util.AuthProvider;
import com.codejam.auth.model.User;
import com.codejam.auth.repository.UserRepository;
import com.codejam.commons.exception.CustomException;
import com.codejam.commons.util.ObjectUtils;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Optional;


@Service
public class GoogleAuthService extends DefaultOAuth2UserService {

    private final UserRepository userRepository;
    private final WebClient webClient;

    public GoogleAuthService(UserRepository userRepository) {
        this.userRepository = userRepository;
        this.webClient = WebClient.builder().build();
    }

    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest) {
        OAuth2User oAuth2User = super.loadUser(userRequest);
        String registrationId = userRequest.getClientRegistration().getRegistrationId();

        try {
            return processOAuth2User(registrationId, oAuth2User);
        } catch (CustomException e) {
            // Wrap CustomException in OAuth2AuthenticationException so Spring Security's failure handler can catch it
            OAuth2Error oauth2Error = new OAuth2Error(e.getErrorType(), e.getCustomMessage(), null);
            throw new OAuth2AuthenticationException(oauth2Error, e);
        }
    }

    public OAuth2User processOAuth2User(String registrationId, OAuth2User oAuth2User) {
        String email = oAuth2User.getAttribute("email");
        String providerId = oAuth2User.getAttribute("sub");
        String firstName = oAuth2User.getAttribute("given_name");
        String lastName = oAuth2User.getAttribute("family_name");
        String profileImage = oAuth2User.getAttribute("picture");

        if (ObjectUtils.isNullOrEmpty(email)) {
            throw new CustomException("GOOGLE_AUTH", "Email not found from OAuth2 provider", HttpStatus.BAD_REQUEST);
        }

        String normalizedEmail = email.trim().toLowerCase();
        AuthProvider authProvider = AuthProvider.valueOf(registrationId.toUpperCase());
        Optional<User> optionalUser = userRepository.findByEmail(normalizedEmail);
        User user;

        if (optionalUser.isPresent()) {
            user = optionalUser.get();

            // Check if user is trying to login with different provider
            // If provider is LOCAL and user has password, prevent OAuth login
            if (user.getProvider() != null && !user.getProvider().equals(authProvider)) {
                throw new CustomException("PROVIDER_MISMATCH",
                        "This email is registered using " +
                        (user.getProvider() == AuthProvider.LOCAL ? "email/password" : user.getProvider().toString()) +
                        ". Please use your " +
                        (user.getProvider() == AuthProvider.LOCAL ? "email and password" : user.getProvider() + " account") +
                        " to login.", HttpStatus.BAD_REQUEST);
            }

            if(profileImage!=null){
                try {
                    // Fetch and update profile image
                    byte[] image = webClient.get()
                            .uri(profileImage)
                            .retrieve()
                            .bodyToMono(byte[].class)
                            .block();

                    if(image!=null){
                        user.setProfileImage(image);
                    }
                } catch (Exception e) {
                    // Log error but don't fail the authentication
                    // Profile image is optional and can be fetched later
                }
            }


            // Update existing user info
            user.setName(firstName + " " + lastName);
            user.setProfileImageUrl(profileImage);
            user.setProviderId(providerId);
            user.setProvider(authProvider);
            user.setEnabled(true);
            userRepository.save(user);
        } else {
            // Create new user
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

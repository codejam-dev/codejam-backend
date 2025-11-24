package com.codejam.auth.service;

import com.codejam.commons.exception.CustomException;
import com.codejam.model.User;
import com.codejam.repository.UserRepository;
import com.codejam.util.AuthProvider;
import com.codejam.commons.util.ObjectUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class GoogleAuthService extends DefaultOAuth2UserService {

    private final UserRepository userRepository;
    private final WebClient webClient = WebClient.builder().build();

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

        if (ObjectUtil.isNullOrEmpty(email)) {
            throw new CustomException("GOOGLE_AUTH", "Email not found from OAuth2 provider", HttpStatus.BAD_REQUEST);
        }

        AuthProvider authProvider = AuthProvider.valueOf(registrationId.toUpperCase());
        Optional<User> optionalUser = userRepository.findByEmail(email);
        User user;

        if (optionalUser.isPresent()) {
            user = optionalUser.get();
            
            if (user.getProvider() != null && !user.getProvider().equals(authProvider)) {
                throw new CustomException("PROVIDER_MISMATCH",
                        "Looks like you're signed up with " + user.getProvider() +
                        " account. Please use your " + user.getProvider() +
                        " account to login.", HttpStatus.BAD_REQUEST);
            }

            if(profileImage != null){
                byte[] image = webClient.get()
                        .uri(profileImage)
                        .retrieve()
                        .bodyToMono(byte[].class)
                        .block();

                if(image != null){
                    user.setProfileImage(image);
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
                    .userId(UUID.randomUUID().toString())
                    .name(firstName + " " + lastName)
                    .email(email)
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


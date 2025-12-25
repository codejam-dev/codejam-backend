package com.codejam.auth.config;

import com.codejam.auth.handler.OAuth2AuthenticationFailureHandler;
import com.codejam.auth.handler.OAuthSuccessHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.SecurityFilterChain;

@Configuration("authSecurityConfig")
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final OAuthSuccessHandler oAuthSuccessHandler;
    private final OAuth2AuthenticationFailureHandler oAuth2AuthenticationFailureHandler;
    private final OAuth2UserService<OAuth2UserRequest, OAuth2User> oAuth2UserService;
    private final PkceOauth2AuthorizationRequestResolver pkceOauth2AuthorizationRequestResolver;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
                                .sessionFixation().migrateSession()
                                .maximumSessions(1)
                                .maxSessionsPreventsLogin(false)
                )
                .authorizeHttpRequests(auth -> auth
                        // Root path - public
                        .requestMatchers("/").permitAll()
                        // Public auth endpoints
                        .requestMatchers("/auth/**").permitAll()
                        // Actuator health - public for Kubernetes probes
                        .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                        // Actuator refresh - secured via ActuatorRefreshFilter (API key validation)
                        .requestMatchers("/actuator/refresh").permitAll()  // Filter handles auth
                        // All other actuator endpoints - deny
                        .requestMatchers("/actuator/**").denyAll()
                        .anyRequest().authenticated()
                )
                .oauth2Login(oauth2 -> oauth2
                        .authorizationEndpoint(authorization -> authorization
                                .baseUri("/auth/oauth2/authorization")
                                .authorizationRequestResolver(pkceOauth2AuthorizationRequestResolver))
                        .redirectionEndpoint(redirection -> redirection.baseUri("/auth/oauth2/callback/*"))
                        .userInfoEndpoint(userInfo -> userInfo.userService(oAuth2UserService))
                        .successHandler(oAuthSuccessHandler)
                        .failureHandler(oAuth2AuthenticationFailureHandler)
                );

        return http.build();
    }
}

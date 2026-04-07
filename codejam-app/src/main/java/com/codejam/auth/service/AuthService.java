package com.codejam.auth.service;

import com.codejam.auth.config.MicroserviceConfig;
import com.codejam.auth.dto.request.*;
import com.codejam.auth.dto.response.AuthResponse;
import com.codejam.auth.dto.response.AuthSessionBundle;
import com.codejam.auth.dto.response.GenerateOtpResponse;
import com.codejam.auth.dto.response.OAuthCodeResponse;
import com.codejam.auth.dto.response.SessionTokens;
import com.codejam.auth.model.User;
import com.codejam.auth.repository.UserRepository;
import com.codejam.auth.service.email.EmailService;
import com.codejam.commons.dto.BaseResponse;
import com.codejam.commons.exception.CustomException;
import com.codejam.commons.service.RedisService;
import com.codejam.commons.util.ObjectUtils;
import com.codejam.commons.util.proxyUtils;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;

import static com.codejam.auth.util.Constants.*;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final OtpService otpService;
    private final RedisService redisService;
    private final proxyUtils proxyUtils;
    private final MicroserviceConfig microserviceConfig;
    private final EmailService emailService;
    private final RefreshSessionService refreshSessionService;

    @Transactional
    public BaseResponse register(RegisterRequest request) {
        Optional<User> user = userRepository.findByEmail(request.getEmail());
        if (user.isPresent()) {
            throw new CustomException("USER_EXISTS", "Username already exists", HttpStatus.BAD_REQUEST);
        }

        User newUser = User.builder()
                .userId(UUID.randomUUID().toString())
                .name(request.getName())
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .provider(com.codejam.auth.util.AuthProvider.LOCAL)
                .enabled(false)
                .build();
        userRepository.save(newUser);

        AuthResponse registerResponse = AuthResponse.builder()
                .name(newUser.getName())
                .email(newUser.getEmail())
                .accessToken(jwtService.generateTempToken(newUser))
                .tokenType("Bearer")
                .isEnabled(newUser.isEnabled())
                .message(REGISTER_SUCCESS_MESSAGE)
                .build();

        return BaseResponse.success(registerResponse);
    }

    public AuthSessionBundle verifyEmailAndLogin(ValidateOtpRequest request) {
        if (ObjectUtils.isNullOrEmpty(request.getEmail())) {
            throw new CustomException("INVALID_TOKEN", "User email not found in request", HttpStatus.UNAUTHORIZED);
        }

        if (!otpService.validateOtp(request)) {
            throw new CustomException("INVALID_OTP", "Invalid or expired OTP", HttpStatus.BAD_REQUEST);
        }

        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new CustomException("USER_NOT_FOUND", "User not found", HttpStatus.BAD_REQUEST));

        user.setEnabled(true);
        userRepository.save(user);

        SessionTokens tokens = refreshSessionService.createFreshSession(user, request.getDeviceId());
        return AuthSessionBundle.of(buildAuthResponse(user, tokens.accessToken(), OTP_VERIFIED_MESSAGE), tokens.refreshToken());
    }

    public AuthSessionBundle loginWithSession(LoginRequest request) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new CustomException("USER_NOT_FOUND", "User not found", HttpStatus.BAD_REQUEST));
        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new CustomException("INVALID_CREDENTIALS", "Invalid username or password", HttpStatus.BAD_REQUEST);
        }

        if (!user.isEnabled()) {
            AuthResponse tempTokenResponse = AuthResponse.builder()
                    .name(user.getName())
                    .email(user.getEmail())
                    .userId(user.getUserId())
                    .accessToken(jwtService.generateTempToken(user))
                    .tokenType("Bearer")
                    .isEnabled(user.isEnabled())
                    .message("Email not verified. Please verify your email to continue.")
                    .build();
            return AuthSessionBundle.of(tempTokenResponse, null);
        }

        SessionTokens tokens = refreshSessionService.createFreshSession(user, request.getDeviceId());
        return AuthSessionBundle.of(
                buildAuthResponse(user, tokens.accessToken(), LOGIN_SUCCESS_MESSAGE),
                tokens.refreshToken()
        );
    }

    public AuthSessionBundle establishOAuthSession(OAuthCodeResponse oauth, String deviceId) {
        User user = userRepository.findByUserId(oauth.getUserId())
                .orElseThrow(() -> new CustomException("USER_NOT_FOUND", "User not found", HttpStatus.BAD_REQUEST));
        if (!user.isEnabled()) {
            throw new CustomException("USER_DISABLED", "Account is not active", HttpStatus.FORBIDDEN);
        }

        SessionTokens tokens = refreshSessionService.createFreshSession(user, deviceId);
        AuthResponse response = AuthResponse.builder()
                .accessToken(tokens.accessToken())
                .tokenType("Bearer")
                .userId(user.getUserId())
                .name(oauth.getName() != null ? oauth.getName() : user.getName())
                .email(oauth.getEmail())
                .avatar(oauth.getAvatar())
                .isEnabled(true)
                .message("OAuth login successful")
                .build();
        return AuthSessionBundle.of(response, tokens.refreshToken());
    }

    public BaseResponse generateOtp(String email) {
        if (ObjectUtils.isNullOrEmpty(email)) {
            throw new CustomException("INVALID_TOKEN", "User email not found in request", HttpStatus.UNAUTHORIZED);
        }
        String transactionId = otpService.generateAndSendOtp(email);
        return BaseResponse.success(
                GenerateOtpResponse.builder()
                        .email(email)
                        .transactionId(transactionId)
                        .message(OTP_SENT_MESSAGE)
                        .build()
        );
    }

    public void logoutAllDevices(String authorizationHeader) {
        if (authorizationHeader == null || !authorizationHeader.startsWith("Bearer ")) {
            throw new CustomException("UNAUTHORIZED", "Missing or invalid Authorization header", HttpStatus.UNAUTHORIZED);
        }
        String access = authorizationHeader.substring(7).trim();
        if (!jwtService.isTokenValid(access)) {
            throw new CustomException("UNAUTHORIZED", "Invalid or expired access token", HttpStatus.UNAUTHORIZED);
        }
        String userId = jwtService.extractUserId(access);
        refreshSessionService.revokeAllSessionsForUser(userId);
    }

    public BaseResponse resetPassword(ResetPasswordRequest request) {
        User user = userRepository.findByEmail(request.getEmail()).orElseThrow(() -> new CustomException("USER_NOT_FOUND", "User not found", HttpStatus.BAD_REQUEST));
        String resetToken = UUID.randomUUID().toString();
        String redisKey = proxyUtils.generateRedisKey("resetToken", user.getEmail());
        redisService.set(redisKey, resetToken, microserviceConfig.getResetTokenExpiration());
        String resetLink = microserviceConfig.getFrontendUrl() + "/auth/reset-password?token=" + resetToken + "&email=" + user.getEmail();
        emailService.sendPasswordResetEmail(user.getEmail(), resetLink);
        return BaseResponse.success("Password reset email sent");
    }

    public BaseResponse validateResetToken(ValidateResetTokenRequest request) {
        User user = userRepository.findByEmail(request.getEmail()).orElseThrow(() -> new CustomException("USER_NOT_FOUND", "User not found", HttpStatus.BAD_REQUEST));
        String redisKey = proxyUtils.generateRedisKey("resetToken", user.getEmail());
        String storedToken = redisService.get(redisKey);
        if (storedToken == null || !storedToken.equals(request.getResetToken())) {
            throw new CustomException("INVALID_RESET_TOKEN", "Invalid or expired reset token", HttpStatus.BAD_REQUEST);
        }
        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);
        redisService.delete(redisKey);
        return BaseResponse.success("Password reset successful");
    }

    private static AuthResponse buildAuthResponse(User user, String accessToken, String message) {
        return AuthResponse.builder()
                .accessToken(accessToken)
                .tokenType("Bearer")
                .userId(user.getUserId())
                .name(user.getName())
                .email(user.getEmail())
                .isEnabled(user.isEnabled())
                .message(message)
                .build();
    }
}

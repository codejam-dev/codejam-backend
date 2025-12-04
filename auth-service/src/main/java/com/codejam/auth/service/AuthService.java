package com.codejam.auth.service;

import com.codejam.auth.dto.request.LoginRequest;
import com.codejam.auth.dto.request.RegisterRequest;
import com.codejam.auth.dto.request.ValidateOtpRequest;
import com.codejam.auth.dto.response.AuthResponse;
import com.codejam.auth.dto.response.GenerateOtpResponse;
import com.codejam.auth.model.User;
import com.codejam.auth.repository.UserRepository;
import com.codejam.commons.dto.BaseResponse;
import com.codejam.commons.exception.CustomException;
import com.codejam.commons.service.RedisService;
import com.codejam.commons.util.JsonUtils;
import com.codejam.commons.util.ObjectUtils;
import com.codejam.commons.util.proxyUtils;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import net.minidev.json.JSONUtil;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static com.codejam.auth.util.Constants.*;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final OtpService otpService;
    private final GoogleAuthService googleAuthService;
    private final RedisService redisService;
    private final proxyUtils proxyUtils;


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

        // Generate TEMP token (15 min expiry) for unverified user
        AuthResponse registerResponse = AuthResponse.builder()
                .name(newUser.getName())
                .email(newUser.getEmail())
                .token(jwtService.generateTempToken(newUser))
                .tokenType("Bearer")
                .isEnabled(newUser.isEnabled())
                .message(REGISTER_SUCCESS_MESSAGE)
                .build();

        return BaseResponse.success(registerResponse);

    }

    public BaseResponse verifyEmailAndLogin(ValidateOtpRequest request) {
        if (!otpService.validateOtp(request)) {
            throw new CustomException("INVALID_OTP", "Invalid or expired OTP", HttpStatus.BAD_REQUEST);
        }

        User user = userRepository.findByEmail(request.getEmail()).orElseThrow(() -> new CustomException("USER_NOT_FOUND", "User not found", HttpStatus.BAD_REQUEST));

        user.setEnabled(true);
        userRepository.save(user);

        AuthResponse loginResponse =  AuthResponse.builder()
                .name(user.getName())
                .email(user.getEmail())
                .userId(user.getUserId())
                .token(jwtService.generateFullToken(user))
                .tokenType("Bearer")
                .isEnabled(user.isEnabled())
                .message(OTP_VERIFIED_MESSAGE)
                .build();

        return BaseResponse.success(loginResponse);
    }


    public BaseResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.getEmail()).orElseThrow(() -> new CustomException("USER_NOT_FOUND", "User not found", HttpStatus.BAD_REQUEST));
        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new CustomException("INVALID_CREDENTIALS", "Invalid username or password", HttpStatus.BAD_REQUEST);
        }

        if (!user.isEnabled()) {
            AuthResponse tempTokenResponse = AuthResponse.builder()
                    .name(user.getName())
                    .email(user.getEmail())
                    .userId(user.getUserId())
                    .token(jwtService.generateTempToken(user))
                    .tokenType("Bearer")
                    .isEnabled(user.isEnabled())
                    .message("Email not verified. Please verify your email to continue.")
                    .build();
            return BaseResponse.success(tempTokenResponse);
        }

        AuthResponse loginResponse =  AuthResponse.builder()
                .name(user.getName())
                .email(user.getEmail())
                .userId(user.getUserId())
                .token(jwtService.generateToken(user))
                .tokenType("Bearer")
                .isEnabled(user.isEnabled())
                .message(LOGIN_SUCCESS_MESSAGE)
                .build();

        return BaseResponse.success(loginResponse);
    }


    public BaseResponse generateOtp(String email) {
        String transactionId = otpService.generateAndSendOtp(email);
        return BaseResponse.success(
                GenerateOtpResponse.builder()
                        .email(email)
                        .transactionId(transactionId)
                        .message(OTP_SENT_MESSAGE)
                        .build()
        );
    }

    public BaseResponse logout(String authorizationHeader) {
        String token = authorizationHeader.replace("Bearer ", "");
        long expiresInSeconds = jwtService.extractExpirationTime(token);
        redisService.set(proxyUtils.generateRedisKey("BLACKLISTED_TOKENS", authorizationHeader),"1",expiresInSeconds);
        return BaseResponse.success("Logout successful");
    }
}

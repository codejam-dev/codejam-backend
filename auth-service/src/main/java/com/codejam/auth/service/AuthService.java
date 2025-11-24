package com.codejam.auth.service;

import com.codejam.auth.dto.request.LoginRequest;
import com.codejam.auth.dto.request.RegisterRequest;
import com.codejam.auth.dto.response.AuthResponse;
import com.codejam.commons.dto.BaseResponse;
import com.codejam.commons.exception.CustomException;
import com.codejam.model.User;
import com.codejam.repository.UserRepository;
import com.codejam.commons.util.Constants;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    @Transactional
    public BaseResponse<AuthResponse> register(RegisterRequest request) {
        Optional<User> user = userRepository.findByEmail(request.getEmail());
        if (user.isPresent()) {
            throw new CustomException("USER_EXISTS", "User already exists", HttpStatus.BAD_REQUEST);
        }

        User newUser = User.builder()
                .userId(UUID.randomUUID().toString())
                .name(request.getName())
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .enabled(true)
                .build();
        userRepository.save(newUser);

        AuthResponse registerResponse = AuthResponse.builder()
                .name(newUser.getName())
                .email(newUser.getEmail())
                .userId(newUser.getUserId())
                .token(jwtService.generateToken(newUser))
                .tokenType("Bearer")
                .isEnabled(newUser.isEnabled())
                .message(Constants.REGISTER_SUCCESS_MESSAGE)
                .build();

        return BaseResponse.success(registerResponse);
    }

    public BaseResponse<AuthResponse> login(LoginRequest request) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new CustomException("USER_NOT_FOUND", "User not found", HttpStatus.BAD_REQUEST));
        
        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new CustomException("INVALID_CREDENTIALS", "Invalid email or password", HttpStatus.BAD_REQUEST);
        }

        AuthResponse loginResponse = AuthResponse.builder()
                .name(user.getName())
                .email(user.getEmail())
                .userId(user.getUserId())
                .token(jwtService.generateToken(user))
                .tokenType("Bearer")
                .isEnabled(user.isEnabled())
                .message(Constants.LOGIN_SUCCESS_MESSAGE)
                .build();

        return BaseResponse.success(loginResponse);
    }

    public String validateToken(String token) {
        if (token.startsWith("Bearer ")) {
            token = token.substring(7);
        }

        String userId = jwtService.extractUserId(token);
        // Find user by userId (stored in JWT subject)
        User user = userRepository.findByUserId(userId)
                .orElseThrow(() -> new CustomException("INVALID_TOKEN", "Invalid token", HttpStatus.UNAUTHORIZED));
                .orElseThrow(() -> new CustomException("INVALID_TOKEN", "Invalid token", HttpStatus.UNAUTHORIZED));

        if (!jwtService.isTokenValid(token, user)) {
            throw new CustomException("INVALID_TOKEN", "Token is invalid or expired", HttpStatus.UNAUTHORIZED);
        }

        return user.getUserId();
    }
}

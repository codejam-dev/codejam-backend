package com.codejam.auth.service;

import com.codejam.auth.config.MicroserviceConfig;
import com.codejam.auth.dto.request.ValidateOtpRequest;
import com.codejam.commons.util.ObjectUtil;
import com.codejam.commons.util.RedisService;
import com.codejam.commons.util.proxyUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.UUID;

import static com.codejam.auth.util.Constants.OTP_REDIS_EXPIRY;

@Service
@RequiredArgsConstructor
public class OtpService {

    private final SecureRandom secureRandom;
    private final proxyUtils proxyUtils;
    private final RedisService redisService;
    private final EmailService emailService;
    private final MicroserviceConfig microserviceConfig;

    public String generateAndSendOtp(String email) {
        String otp;
        String transactionId;

        if (microserviceConfig.getOtp().isEnableDynamic()) {
            otp = String.valueOf(100000 + secureRandom.nextInt(900000));
            transactionId = UUID.randomUUID().toString();
            emailService.sendOtpVerificationEmail(email, otp);
        } else {
            otp = microserviceConfig.getOtp().getTestValue();
            transactionId = microserviceConfig.getOtp().getTestTransactionId() != null 
                    ? microserviceConfig.getOtp().getTestTransactionId() 
                    : "TransactionID";
            System.out.println("🧪 TEST MODE: OTP for " + email + " is " + otp + " (email not sent)");
        }

        String key = proxyUtils.generateRedisKey("OTP", email, transactionId);
        redisService.set(key, otp, OTP_REDIS_EXPIRY);
        return transactionId;
    }

    public String generateOtp() {
        int otp = 100000 + secureRandom.nextInt(900000);
        return String.valueOf(otp);
    }

    public void storeOtp(String email, String transactionId, String otp) {
        String key = proxyUtils.generateRedisKey("OTP", email, transactionId);
        redisService.set(key, otp, OTP_REDIS_EXPIRY);
    }

    public boolean validateOtp(ValidateOtpRequest request) {
        String key = proxyUtils.generateRedisKey("OTP", request.getEmail(), request.getTransactionId());
        String storedOtp = redisService.get(key);

        if (ObjectUtil.isNullOrEmpty(storedOtp)) return false;

        boolean isValid = storedOtp.equals(request.getOtp());
        if (isValid) {
            redisService.delete(key);
        }

        return isValid;
    }

}


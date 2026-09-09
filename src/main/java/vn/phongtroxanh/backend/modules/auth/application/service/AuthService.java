package vn.phongtroxanh.backend.modules.auth.application.service;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.phongtroxanh.backend.common.exception.BadRequestException;
import vn.phongtroxanh.backend.common.exception.ConflictException;
import vn.phongtroxanh.backend.common.exception.RateLimitException;
import vn.phongtroxanh.backend.common.exception.ResourceNotFoundException;
import vn.phongtroxanh.backend.common.exception.UnauthorizedException;
import vn.phongtroxanh.backend.common.security.CookieUtils;
import vn.phongtroxanh.backend.common.security.JwtTokenProvider;
import vn.phongtroxanh.backend.common.security.SecurityUtils;
import vn.phongtroxanh.backend.modules.auth.presentation.dto.*;
import vn.phongtroxanh.backend.common.mail.EmailNotificationPort;
import vn.phongtroxanh.backend.modules.user.domain.*;
import vn.phongtroxanh.backend.modules.user.infrastructure.repository.*;
import vn.phongtroxanh.backend.modules.user.presentation.dto.UserProfileResponse;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final UserProfileRepository userProfileRepository;
    private final UserConsumableRepository userConsumableRepository;
    private final UserVerificationRepository userVerificationRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider tokenProvider;
    private final CookieUtils cookieUtils;
    private final EmailNotificationPort emailNotificationPort;
    private final RedisTemplate<String, Object> redisTemplate;
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    private static final SecureRandom RANDOM = new SecureRandom();

    @Transactional
    public AuthResponse register(RegisterRequest request, HttpServletResponse response) {
        if (request.getEmail() != null && userRepository.existsByEmail(request.getEmail())) {
            throw new ConflictException("EMAIL_EXISTS", "Email này đã được sử dụng");
        }
        if (request.getPhoneNumber() != null && !request.getPhoneNumber().isBlank() && userRepository.existsByPhoneNumber(request.getPhoneNumber())) {
            throw new ConflictException("PHONE_EXISTS", "Số điện thoại này đã được sử dụng");
        }

        User user = User.builder()
                .email(request.getEmail())
                .phoneNumber(request.getPhoneNumber())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .fullName(request.getFullName())
                .role(request.getRole() != null ? request.getRole() : UserRole.TENANT)
                .status(UserStatus.ACTIVE)
                .trustScore(50)
                .isVerified(false)
                .lastActiveAt(Instant.now())
                .build();

        user = userRepository.save(user);

        UserProfile profile = UserProfile.builder()
                .userId(user.getId())
                .isPublic(true)
                .showSchool(true)
                .hideActiveStatus(false)
                .build();
        userProfileRepository.save(profile);

        UserConsumable consumable = UserConsumable.builder()
                .userId(user.getId())
                .swipesLeft(15)
                .boostsLeft(0)
                .superMatchesLeft(0)
                .build();
        userConsumableRepository.save(consumable);

        String sessionId = UUID.randomUUID().toString();
        String accessToken = tokenProvider.generateAccessToken(user.getId(), user.getRole().name(), user.getEmail());
        String refreshToken = tokenProvider.generateRefreshToken(user.getId(), sessionId);

        storeSessionInRedis(sessionId, user.getId(), "Initial_Device");
        cookieUtils.addRefreshTokenCookie(response, refreshToken, tokenProvider.getRefreshExpirationMs());

        return AuthResponse.builder()
                .accessToken(accessToken)
                .tokenType("Bearer")
                .expiresIn(tokenProvider.getAccessExpirationMs() / 1000)
                .user(mapToProfileResponse(user, profile, consumable))
                .build();
    }

    @Transactional
    public AuthResponse login(LoginRequest request, HttpServletResponse response) {
        User user = null;
        if (request.getLogin().contains("@")) {
            user = userRepository.findByEmail(request.getLogin()).orElse(null);
        } else {
            user = userRepository.findByPhoneNumber(request.getLogin()).orElse(null);
        }

        if (user == null || !passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new UnauthorizedException("INVALID_CREDENTIALS", "Email/Số điện thoại hoặc mật khẩu không chính xác");
        }

        if (user.getStatus() == UserStatus.LOCKED) {
            throw new UnauthorizedException("ACCOUNT_LOCKED", "Tài khoản của bạn đã bị khóa. Vui lòng liên hệ hỗ trợ");
        }
        if (user.getStatus() == UserStatus.DELETED) {
            throw new UnauthorizedException("ACCOUNT_DELETED", "Tài khoản này không còn tồn tại");
        }

        user.setLastActiveAt(Instant.now());
        userRepository.save(user);

        UserProfile profile = userProfileRepository.findById(user.getId()).orElse(null);
        UserConsumable consumable = userConsumableRepository.findById(user.getId()).orElse(null);

        String sessionId = UUID.randomUUID().toString();
        String accessToken = tokenProvider.generateAccessToken(user.getId(), user.getRole().name(), user.getEmail());
        String refreshToken = tokenProvider.generateRefreshToken(user.getId(), sessionId);

        storeSessionInRedis(sessionId, user.getId(), request.getDeviceId() != null ? request.getDeviceId() : "Default_Device");
        cookieUtils.addRefreshTokenCookie(response, refreshToken, tokenProvider.getRefreshExpirationMs());

        return AuthResponse.builder()
                .accessToken(accessToken)
                .tokenType("Bearer")
                .expiresIn(tokenProvider.getAccessExpirationMs() / 1000)
                .user(mapToProfileResponse(user, profile, consumable))
                .build();
    }

    public void sendOtp(SendOtpRequest request) {
        String rateLimitKey = "auth:otp-rate:" + request.getEmail();
        Long count = redisTemplate.opsForValue().increment(rateLimitKey);
        if (count != null && count == 1) {
            redisTemplate.expire(rateLimitKey, Duration.ofMinutes(5));
        }
        if (count != null && count > 3) {
            throw new RateLimitException("OTP_RATE_LIMIT", "Bạn đã yêu cầu gửi OTP quá 3 lần. Vui lòng đợi 5 phút");
        }

        String otp = String.format("%06d", RANDOM.nextInt(1_000_000));
        String otpKey = "auth:otp:" + request.getEmail();
        redisTemplate.opsForValue().set(otpKey, otp, 5, TimeUnit.MINUTES);

        emailNotificationPort.sendOtpEmail(request.getEmail(), otp);
        log.info("Sent OTP for email {}", request.getEmail());
    }

    public String verifyOtp(VerifyOtpRequest request) {
        String otpKey = "auth:otp:" + request.getEmail();
        Object cachedOtp = redisTemplate.opsForValue().get(otpKey);

        if (cachedOtp == null || !cachedOtp.toString().equals(request.getOtp())) {
            throw new BadRequestException("INVALID_OTP", "Mã OTP không hợp lệ hoặc đã hết hạn");
        }

        redisTemplate.delete(otpKey);
        return tokenProvider.generateTempToken(request.getEmail(), "OTP_VERIFIED");
    }

    public void forgotPassword(ForgotPasswordRequest request) {
        userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new ResourceNotFoundException("USER_NOT_FOUND", "Không tìm thấy tài khoản với email này"));

        SendOtpRequest otpReq = new SendOtpRequest(request.getEmail(), "FORGOT_PASSWORD");
        sendOtp(otpReq);
    }

    @Transactional
    public void resetPassword(ResetPasswordRequest request) {
        if (!tokenProvider.validateToken(request.getTempToken())) {
            throw new UnauthorizedException("INVALID_TOKEN", "Mã xác thực không hợp lệ hoặc đã hết hạn");
        }

        String email = tokenProvider.extractClaims(request.getTempToken()).getSubject();
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("USER_NOT_FOUND", "Tài khoản không tồn tại"));

        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        user.setUpdatedAt(Instant.now());
        userRepository.save(user);

        log.info("Password successfully reset for user {}", user.getId());
    }

    public AuthResponse refreshToken(HttpServletRequest request, HttpServletResponse response) {
        String refreshToken = cookieUtils.extractRefreshTokenFromCookie(request);
        if (refreshToken == null || !tokenProvider.validateToken(refreshToken)) {
            throw new UnauthorizedException("INVALID_REFRESH_TOKEN", "Phiên đăng nhập không hợp lệ hoặc đã hết hạn");
        }

        String sessionId = tokenProvider.extractSessionId(refreshToken);
        UUID userId = tokenProvider.extractUserId(refreshToken);

        String sessionKey = "auth:refresh-session:" + sessionId;
        Object sessionData = redisTemplate.opsForValue().get(sessionKey);
        if (sessionData == null) {
            cookieUtils.deleteRefreshTokenCookie(response);
            throw new UnauthorizedException("SESSION_EXPIRED", "Phiên đăng nhập đã bị thu hồi hoặc hết hạn");
        }

        // Revoke old session (Token Rotation)
        redisTemplate.delete(sessionKey);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("USER_NOT_FOUND", "Người dùng không tồn tại"));

        UserProfile profile = userProfileRepository.findById(user.getId()).orElse(null);
        UserConsumable consumable = userConsumableRepository.findById(user.getId()).orElse(null);

        // Generate new session and tokens
        String newSessionId = UUID.randomUUID().toString();
        String newAccessToken = tokenProvider.generateAccessToken(user.getId(), user.getRole().name(), user.getEmail());
        String newRefreshToken = tokenProvider.generateRefreshToken(user.getId(), newSessionId);

        storeSessionInRedis(newSessionId, user.getId(), "Rotated_Device");
        cookieUtils.addRefreshTokenCookie(response, newRefreshToken, tokenProvider.getRefreshExpirationMs());

        return AuthResponse.builder()
                .accessToken(newAccessToken)
                .tokenType("Bearer")
                .expiresIn(tokenProvider.getAccessExpirationMs() / 1000)
                .user(mapToProfileResponse(user, profile, consumable))
                .build();
    }

    public void logout(HttpServletRequest request, HttpServletResponse response) {
        String bearer = request.getHeader("Authorization");
        if (bearer != null && bearer.startsWith("Bearer ")) {
            String token = bearer.substring(7);
            redisTemplate.opsForValue().set("auth:blacklist:" + token, "revoked", 15, TimeUnit.MINUTES);
        }

        String refreshToken = cookieUtils.extractRefreshTokenFromCookie(request);
        if (refreshToken != null && tokenProvider.validateToken(refreshToken)) {
            String sessionId = tokenProvider.extractSessionId(refreshToken);
            if (sessionId != null) {
                redisTemplate.delete("auth:refresh-session:" + sessionId);
            }
        }

        cookieUtils.deleteRefreshTokenCookie(response);
    }

    @Transactional
    public AuthResponse googleLogin(GoogleLoginRequest request, HttpServletResponse response) {
        String email = null;
        String fullName = "Google User";

        if (request.getIdToken() == null || !request.getIdToken().contains(".")) {
            throw new UnauthorizedException("INVALID_GOOGLE_TOKEN", "Google ID Token không hợp lệ hoặc thiếu định dạng chuẩn");
        }

        try {
            String[] parts = request.getIdToken().split("\\.");
            if (parts.length >= 2) {
                byte[] decoded = java.util.Base64.getUrlDecoder().decode(parts[1]);
                com.fasterxml.jackson.databind.JsonNode node = objectMapper.readTree(decoded);
                if (node.hasNonNull("email")) {
                    email = node.get("email").asText();
                }
                if (node.hasNonNull("name")) {
                    fullName = node.get("name").asText();
                }
            }
        } catch (Exception e) {
            log.warn("Could not decode google token payload: {}", e.getMessage());
            throw new UnauthorizedException("INVALID_GOOGLE_TOKEN", "Google ID Token không thể giải mã");
        }

        if (email == null || email.isBlank()) {
            throw new UnauthorizedException("INVALID_GOOGLE_TOKEN", "Google ID Token không chứa thông tin email hợp lệ");
        }

        final String userEmail = email;
        final String userFullName = fullName;

        User user = userRepository.findByEmail(userEmail).orElseGet(() -> {
            User newUser = User.builder()
                    .email(userEmail)
                    .fullName(userFullName)
                    .passwordHash(passwordEncoder.encode(UUID.randomUUID().toString()))
                    .role(request.getRole() != null ? request.getRole() : UserRole.TENANT)
                    .status(UserStatus.ACTIVE)
                    .trustScore(50)
                    .isVerified(false)
                    .lastActiveAt(Instant.now())
                    .build();
            newUser = userRepository.save(newUser);

            UserProfile profile = UserProfile.builder()
                    .userId(newUser.getId())
                    .isPublic(true)
                    .showSchool(true)
                    .hideActiveStatus(false)
                    .build();
            userProfileRepository.save(profile);

            UserConsumable consumable = UserConsumable.builder()
                    .userId(newUser.getId())
                    .swipesLeft(15)
                    .boostsLeft(0)
                    .superMatchesLeft(0)
                    .build();
            userConsumableRepository.save(consumable);

            return newUser;
        });

        UserProfile profile = userProfileRepository.findById(user.getId()).orElse(null);
        UserConsumable consumable = userConsumableRepository.findById(user.getId()).orElse(null);

        String sessionId = UUID.randomUUID().toString();
        String accessToken = tokenProvider.generateAccessToken(user.getId(), user.getRole().name(), user.getEmail());
        String refreshToken = tokenProvider.generateRefreshToken(user.getId(), sessionId);

        storeSessionInRedis(sessionId, user.getId(), "Google_OAuth");
        cookieUtils.addRefreshTokenCookie(response, refreshToken, tokenProvider.getRefreshExpirationMs());

        return AuthResponse.builder()
                .accessToken(accessToken)
                .tokenType("Bearer")
                .expiresIn(tokenProvider.getAccessExpirationMs() / 1000)
                .user(mapToProfileResponse(user, profile, consumable))
                .build();
    }

    @Transactional
    public void tenantOnboarding(TenantOnboardingRequest request) {
        UUID userId = SecurityUtils.getCurrentUserId();
        UserProfile profile = userProfileRepository.findById(userId)
                .orElseGet(() -> UserProfile.builder().userId(userId).build());

        if (request.getSchoolOrCompany() != null) profile.setSchoolOrCompany(request.getSchoolOrCompany());
        if (request.getBirthDate() != null) profile.setBirthDate(request.getBirthDate());
        if (request.getGender() != null) profile.setGender(request.getGender());
        if (request.getInterests() != null) profile.setInterests(request.getInterests());
        if (request.getEarlySleeper() != null) profile.setEarlySleeper(request.getEarlySleeper());
        if (request.getIsNeat() != null) profile.setIsNeat(request.getIsNeat());
        if (request.getAllowGuests() != null) profile.setAllowGuests(request.getAllowGuests());
        if (request.getNonSmoking() != null) profile.setNonSmoking(request.getNonSmoking());
        if (request.getNoiseTolerance() != null) profile.setNoiseTolerance(request.getNoiseTolerance());

        if (request.getBudgetMin() != null) profile.setBudgetMin(request.getBudgetMin());
        if (request.getBudgetMax() != null) profile.setBudgetMax(request.getBudgetMax());
        if (request.getPreferredDistricts() != null) profile.setPreferredDistricts(request.getPreferredDistricts());
        if (request.getPreferredRoomType() != null) profile.setPreferredRoomType(request.getPreferredRoomType());
        if (request.getPreferredGender() != null) profile.setPreferredGender(request.getPreferredGender());
        if (request.getProximitySchool() != null) profile.setProximitySchool(request.getProximitySchool());
        if (request.getProximityWork() != null) profile.setProximityWork(request.getProximityWork());
        if (request.getProximityMarket() != null) profile.setProximityMarket(request.getProximityMarket());
        if (request.getProximityBus() != null) profile.setProximityBus(request.getProximityBus());

        userProfileRepository.save(profile);
    }

    @Transactional
    public void landlordOnboarding(LandlordOnboardingRequest request) {
        UUID userId = SecurityUtils.getCurrentUserId();
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("USER_NOT_FOUND", "Không tìm thấy người dùng"));

        if (request.getFullName() != null) user.setFullName(request.getFullName());
        if (request.getPhoneNumber() != null) user.setPhoneNumber(request.getPhoneNumber());
        userRepository.save(user);

        if (request.getIdCardNumber() != null && request.getIdCardFrontUrl() != null && request.getIdCardBackUrl() != null) {
            UserVerification verification = UserVerification.builder()
                    .userId(userId)
                    .idCardNumber(request.getIdCardNumber())
                    .idCardFrontUrl(request.getIdCardFrontUrl())
                    .idCardBackUrl(request.getIdCardBackUrl())
                    .status(VerificationStatus.PENDING)
                    .build();
            userVerificationRepository.save(verification);
        }
    }

    private void storeSessionInRedis(String sessionId, UUID userId, String deviceId) {
        String sessionKey = "auth:refresh-session:" + sessionId;
        Map<String, String> data = Map.of(
                "sessionId", sessionId,
                "userId", userId.toString(),
                "deviceId", deviceId,
                "createdAt", Instant.now().toString()
        );
        redisTemplate.opsForValue().set(sessionKey, data, 7, TimeUnit.DAYS);
    }

    private UserProfileResponse mapToProfileResponse(User user, UserProfile profile, UserConsumable consumable) {
        UserProfileResponse.UserProfileResponseBuilder builder = UserProfileResponse.builder()
                .id(user.getId())
                .email(user.getEmail())
                .phoneNumber(user.getPhoneNumber())
                .fullName(user.getFullName())
                .role(user.getRole())
                .status(user.getStatus())
                .avatarUrl(user.getAvatarUrl())
                .isVerified(user.getIsVerified())
                .trustScore(user.getTrustScore())
                .createdAt(user.getCreatedAt())
                .lastActiveAt(user.getLastActiveAt());

        if (consumable != null) {
            builder.swipesLeft(consumable.getSwipesLeft())
                    .boostsLeft(consumable.getBoostsLeft())
                    .superMatchesLeft(consumable.getSuperMatchesLeft());
        }

        if (profile != null) {
            builder.schoolOrCompany(profile.getSchoolOrCompany())
                    .birthDate(profile.getBirthDate())
                    .gender(profile.getGender())
                    .preferredGender(profile.getPreferredGender())
                    .bio(profile.getBio())
                    .budgetMin(profile.getBudgetMin())
                    .budgetMax(profile.getBudgetMax())
                    .preferredDistricts(profile.getPreferredDistricts())
                    .preferredRoomType(profile.getPreferredRoomType())
                    .interests(profile.getInterests())
                    .earlySleeper(profile.getEarlySleeper())
                    .isNeat(profile.getIsNeat())
                    .allowGuests(profile.getAllowGuests())
                    .nonSmoking(profile.getNonSmoking())
                    .noiseTolerance(profile.getNoiseTolerance())
                    .proximitySchool(profile.getProximitySchool())
                    .proximityWork(profile.getProximityWork())
                    .proximityMarket(profile.getProximityMarket())
                    .proximityBus(profile.getProximityBus())
                    .isPublic(profile.getIsPublic())
                    .showSchool(profile.getShowSchool())
                    .hideActiveStatus(profile.getHideActiveStatus());
        }

        return builder.build();
    }
}

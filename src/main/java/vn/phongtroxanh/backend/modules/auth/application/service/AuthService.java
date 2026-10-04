package vn.phongtroxanh.backend.modules.auth.application.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
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
import java.time.LocalDate;
import java.time.Period;
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
    private final EntityManager entityManager;

    @org.springframework.beans.factory.annotation.Value("${app.oauth2.google.client-id:}")
    private String googleClientId;

    public String getGoogleClientId() {
        return googleClientId != null ? googleClientId.trim() : "";
    }

    private static final SecureRandom RANDOM = new SecureRandom();

    @Transactional
    public AuthResponse register(RegisterRequest request, HttpServletResponse response) {
        assertPublicRole(request.getRole());
        validateRegisterData(request);
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
                .build();
        userConsumableRepository.save(consumable);

        String sessionId = UUID.randomUUID().toString();
        String accessToken = accessToken(user);
        String refreshToken = refreshToken(user, sessionId);

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

        if (user != null) entityManager.refresh(user, LockModeType.PESSIMISTIC_WRITE);
        if (user == null || !passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new UnauthorizedException("INVALID_CREDENTIALS", "Email/Số điện thoại hoặc mật khẩu không chính xác");
        }

        assertActive(user);

        user.setLastActiveAt(Instant.now());
        userRepository.save(user);

        UserProfile profile = userProfileRepository.findById(user.getId()).orElse(null);
        resetDailySwipes(user.getId());
        UserConsumable consumable = userConsumableRepository.findById(user.getId()).orElse(null);

        String sessionId = UUID.randomUUID().toString();
        String accessToken = accessToken(user);
        String refreshToken = refreshToken(user, sessionId);

        storeSessionInRedis(sessionId, user.getId(), request.getDeviceId() != null ? request.getDeviceId() : "Default_Device");
        cookieUtils.addRefreshTokenCookie(response, refreshToken, tokenProvider.getRefreshExpirationMs());

        UserProfileResponse profileResponse = mapToProfileResponse(user, profile, consumable);
        return AuthResponse.builder()
                .accessToken(accessToken)
                .tokenType("Bearer")
                .expiresIn(tokenProvider.getAccessExpirationMs() / 1000)
                .isNewUser(false)
                .isOnboarded(profileResponse.getIsOnboarded())
                .user(profileResponse)
                .build();
    }

    public void sendOtp(SendOtpRequest request) {
        String purpose = request.getType() == null ? "REGISTER" : request.getType();
        if (!java.util.Set.of("REGISTER", "LOGIN", "VERIFY_EMAIL", "FORGOT_PASSWORD").contains(purpose)) {
            throw new BadRequestException("INVALID_OTP_PURPOSE", "Mục đích xác thực OTP không hợp lệ");
        }
        if ("REGISTER".equals(purpose)) {
            if (userRepository.existsByEmail(request.getEmail())) {
                throw new ConflictException("EMAIL_EXISTS", "Email này đã được sử dụng để đăng ký tài khoản. Vui lòng đăng nhập.");
            }
        } else {
            User user = userRepository.findByEmail(request.getEmail())
                    .orElseThrow(() -> new ResourceNotFoundException("USER_NOT_FOUND", "Không tìm thấy tài khoản với email này"));
            assertActive(user);
        }
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
        redisTemplate.opsForValue().set(otpKey, Map.of("otp", otp, "purpose", purpose), 5, TimeUnit.MINUTES);

        emailNotificationPort.sendOtpEmail(request.getEmail(), otp);
        log.info("Sent OTP for email {}", request.getEmail());
    }

    public String verifyOtp(VerifyOtpRequest request) {
        String otpKey = "auth:otp:" + request.getEmail();
        String attemptsKey = "auth:otp-attempts:" + request.getEmail();
        Long attempts = redisTemplate.opsForValue().increment(attemptsKey);
        if (attempts != null && attempts == 1) redisTemplate.expire(attemptsKey, Duration.ofMinutes(5));
        if (attempts == null || attempts > 5) {
            redisTemplate.delete(otpKey);
            throw new RateLimitException("OTP_ATTEMPT_LIMIT", "Bạn đã nhập OTP quá 5 lần. Vui lòng đợi 5 phút");
        }
        Object cachedOtp = redisTemplate.opsForValue().get(otpKey);

        if (!(cachedOtp instanceof Map<?, ?> data) || !request.getOtp().equals(data.get("otp"))) {
            throw new BadRequestException("INVALID_OTP", "Mã OTP không hợp lệ hoặc đã hết hạn");
        }

        Object consumedOtp = redisTemplate.opsForValue().getAndDelete(otpKey);
        if (!cachedOtp.equals(consumedOtp)) {
            throw new BadRequestException("INVALID_OTP", "Mã OTP không hợp lệ hoặc đã được sử dụng");
        }
        String purpose = data.get("purpose").toString();
        String token = tokenProvider.generateTempToken(request.getEmail(), purpose);
        redisTemplate.opsForValue().set("auth:temp-token:" + tokenProvider.extractClaims(token).getId(), request.getEmail(), 15, TimeUnit.MINUTES);
        return token;
    }

    public void forgotPassword(ForgotPasswordRequest request) {
        SendOtpRequest otpReq = new SendOtpRequest(request.getEmail(), "FORGOT_PASSWORD");
        sendOtp(otpReq);
    }

    @Transactional
    public void resetPassword(ResetPasswordRequest request) {
        if (!tokenProvider.validateToken(request.getTempToken())) {
            throw new UnauthorizedException("INVALID_TOKEN", "Mã xác thực không hợp lệ hoặc đã hết hạn");
        }

        io.jsonwebtoken.Claims claims = tokenProvider.extractClaims(request.getTempToken());
        if (!"TEMP".equals(claims.get("type", String.class)) ||
                !"FORGOT_PASSWORD".equals(claims.get("purpose", String.class)) || claims.getId() == null) {
            throw new UnauthorizedException("INVALID_TOKEN", "Mã xác thực không được dùng để đặt lại mật khẩu");
        }
        String email = claims.getSubject();
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("USER_NOT_FOUND", "Tài khoản không tồn tại"));
        entityManager.refresh(user, LockModeType.PESSIMISTIC_WRITE);
        assertActive(user);
        Object proof = redisTemplate.opsForValue().getAndDelete("auth:temp-token:" + claims.getId());
        if (!email.equals(proof)) {
            throw new UnauthorizedException("INVALID_TOKEN", "Mã xác thực đã được sử dụng hoặc hết hạn");
        }
        redisTemplate.opsForValue().increment("auth:credential-version:" + user.getId());

        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        user.setUpdatedAt(Instant.now());
        userRepository.save(user);

        log.info("Password successfully reset for user {}", user.getId());
    }

    @Transactional
    public AuthResponse refreshToken(HttpServletRequest request, HttpServletResponse response) {
        String refreshToken = cookieUtils.extractRefreshTokenFromCookie(request);
        if (refreshToken == null || !tokenProvider.validateToken(refreshToken)) {
            throw new UnauthorizedException("INVALID_REFRESH_TOKEN", "Phiên đăng nhập không hợp lệ hoặc đã hết hạn");
        }
        if (!"REFRESH".equals(tokenProvider.extractClaims(refreshToken).get("type", String.class))) {
            throw new UnauthorizedException("INVALID_REFRESH_TOKEN", "Token không được dùng để làm mới phiên đăng nhập");
        }

        String sessionId = tokenProvider.extractSessionId(refreshToken);
        UUID userId = tokenProvider.extractUserId(refreshToken);

        String sessionKey = "auth:refresh-session:" + sessionId;
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UnauthorizedException("INVALID_REFRESH_TOKEN", "Người dùng không tồn tại"));
        entityManager.refresh(user, LockModeType.PESSIMISTIC_WRITE);
        assertActive(user);
        assertCredentialVersion(refreshToken, userId);
        Object sessionData = redisTemplate.opsForValue().getAndDelete(sessionKey);
        if (!(sessionData instanceof Map<?, ?> session) || !userId.toString().equals(session.get("userId"))) {
            cookieUtils.deleteRefreshTokenCookie(response);
            throw new UnauthorizedException("SESSION_EXPIRED", "Phiên đăng nhập đã bị thu hồi hoặc hết hạn");
        }

        UserProfile profile = userProfileRepository.findById(user.getId()).orElse(null);
        resetDailySwipes(user.getId());
        UserConsumable consumable = userConsumableRepository.findById(user.getId()).orElse(null);

        // Generate new session and tokens
        String newSessionId = UUID.randomUUID().toString();
        String newAccessToken = accessToken(user);
        String newRefreshToken = refreshToken(user, newSessionId);

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
            if (tokenProvider.validateToken(token)) {
                var claims = tokenProvider.extractClaims(token);
                if ("ACCESS".equals(claims.get("type", String.class))) {
                    long remainingMs = claims.getExpiration().getTime() - System.currentTimeMillis();
                    if (remainingMs > 0) redisTemplate.opsForValue().set("auth:blacklist:" + token, "revoked", remainingMs, TimeUnit.MILLISECONDS);
                }
            }
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
        assertPublicRole(request.getRole());
        if (googleClientId == null || googleClientId.isBlank()) {
            throw new BadRequestException("GOOGLE_NOT_CONFIGURED", "Đăng nhập Google chưa được cấu hình");
        }
        String idToken = request.getIdToken();
        if (idToken == null || idToken.isBlank() || idToken.endsWith(".mock_sig")) {
            throw new UnauthorizedException("INVALID_GOOGLE_TOKEN", "Google ID Token không hợp lệ");
        }

        com.fasterxml.jackson.databind.JsonNode googlePayload;
        try {
            var factory = new org.springframework.http.client.SimpleClientHttpRequestFactory();
            factory.setConnectTimeout(Duration.ofSeconds(5));
            factory.setReadTimeout(Duration.ofSeconds(5));
            var client = org.springframework.web.client.RestClient.builder().requestFactory(factory).build();
            googlePayload = client.get()
                    .uri("https://oauth2.googleapis.com/tokeninfo?id_token={token}", idToken.trim())
                    .retrieve().body(com.fasterxml.jackson.databind.JsonNode.class);
        } catch (Exception ex) {
            log.warn("Google token verification failed: {}", ex.getClass().getSimpleName());
            throw new UnauthorizedException("INVALID_GOOGLE_TOKEN", "Không thể xác thực Google ID Token");
        }
        if (googlePayload == null || googlePayload.has("error") ||
                !googleClientId.trim().equals(googlePayload.path("aud").asText()) ||
                !("accounts.google.com".equals(googlePayload.path("iss").asText()) ||
                        "https://accounts.google.com".equals(googlePayload.path("iss").asText())) ||
                googlePayload.path("exp").asLong(0) <= Instant.now().getEpochSecond() ||
                !googlePayload.path("email_verified").asBoolean(false) ||
                googlePayload.path("sub").asText().isBlank()) {
            throw new UnauthorizedException("INVALID_GOOGLE_TOKEN", "Google ID Token không hợp lệ hoặc email chưa được xác minh");
        }
        String email = googlePayload.path("email").asText();
        if (email.length() > 255 || !email.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) {
            throw new UnauthorizedException("INVALID_GOOGLE_TOKEN", "Google ID Token không chứa email hợp lệ");
        }
        // Google is authoritative only for Gmail or verified Workspace addresses.
        if (!email.toLowerCase(java.util.Locale.ROOT).endsWith("@gmail.com") && googlePayload.path("hd").asText().isBlank()) {
            throw new UnauthorizedException("GOOGLE_EMAIL_NOT_AUTHORITATIVE", "Email này cần đăng nhập bằng mật khẩu hoặc xác thực email riêng");
        }
        String fullName = googlePayload.path("name").asText("Google User");
        String avatarUrl = googlePayload.hasNonNull("picture") ? googlePayload.get("picture").asText() : null;
        if (fullName.isBlank() || fullName.length() > 150 || (avatarUrl != null && avatarUrl.length() > 500)) {
            throw new UnauthorizedException("INVALID_GOOGLE_TOKEN", "Thông tin hồ sơ Google không hợp lệ");
        }
        final String userEmail = email;
        final String userFullName = fullName;
        final String userAvatarUrl = avatarUrl;

        java.util.concurrent.atomic.AtomicBoolean isNewUserRef = new java.util.concurrent.atomic.AtomicBoolean(false);
        User user = userRepository.findByEmail(userEmail).map(existing -> {
            entityManager.refresh(existing, LockModeType.PESSIMISTIC_WRITE);
            return existing;
        }).orElseGet(() -> {
            isNewUserRef.set(true);
            User newUser = User.builder()
                    .email(userEmail)
                    .fullName(userFullName)
                    .avatarUrl(userAvatarUrl)
                    .passwordHash(passwordEncoder.encode(UUID.randomUUID().toString()))
                    .role(request.getRole() != null ? request.getRole() : UserRole.TENANT)
                    .status(UserStatus.ACTIVE)
                    .trustScore(50)
                    .isVerified(false) // Email verification does not establish KYC
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
                    .build();
            userConsumableRepository.save(consumable);

            return newUser;
        });

        assertActive(user);
        boolean userUpdated = false;
        if (user.getAvatarUrl() == null && userAvatarUrl != null) {
            user.setAvatarUrl(userAvatarUrl);
            userUpdated = true;
        }
        if (userUpdated) {
            userRepository.save(user);
        }

        UserProfile profile = userProfileRepository.findById(user.getId()).orElse(null);
        resetDailySwipes(user.getId());
        UserConsumable consumable = userConsumableRepository.findById(user.getId()).orElse(null);

        String sessionId = UUID.randomUUID().toString();
        String accessToken = accessToken(user);
        String refreshToken = refreshToken(user, sessionId);

        storeSessionInRedis(sessionId, user.getId(), "Google_OAuth");
        cookieUtils.addRefreshTokenCookie(response, refreshToken, tokenProvider.getRefreshExpirationMs());

        UserProfileResponse profileResponse = mapToProfileResponse(user, profile, consumable);
        return AuthResponse.builder()
                .accessToken(accessToken)
                .tokenType("Bearer")
                .expiresIn(tokenProvider.getAccessExpirationMs() / 1000)
                .isNewUser(isNewUserRef.get())
                .isOnboarded(profileResponse.getIsOnboarded())
                .user(profileResponse)
                .build();
    }

    @Transactional
    public void tenantOnboarding(TenantOnboardingRequest request) {
        UUID userId = SecurityUtils.getCurrentUserId();
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("USER_NOT_FOUND", "Không tìm thấy người dùng"));
        entityManager.refresh(user, LockModeType.PESSIMISTIC_WRITE);

        if (user.getRole() != UserRole.ADMIN && user.getRole() != UserRole.TENANT) {
            user.setRole(UserRole.TENANT);
            userRepository.save(user);
        }

        if (request.getFullName() != null && !request.getFullName().isBlank()) {
            String name = request.getFullName().trim();
            if (name.matches("^\\d+$")) {
                throw new BadRequestException("INVALID_FULL_NAME", "Họ và tên không thể chỉ toàn chữ số");
            }
            user.setFullName(name);
            userRepository.save(user);
        }

        UserProfile profile = userProfileRepository.findById(userId)
                .orElseGet(() -> UserProfile.builder().userId(userId).build());

        if (request.getBirthDate() != null) {
            if (request.getBirthDate().isAfter(LocalDate.now())) {
                throw new BadRequestException("INVALID_BIRTHDATE", "Ngày sinh phải là ngày trong quá khứ");
            }
            int age = Period.between(request.getBirthDate(), LocalDate.now()).getYears();
            if (age < 15 || age > 100) {
                throw new BadRequestException("INVALID_AGE", "Độ tuổi người thuê trọ hợp lệ từ 15 đến 100 tuổi");
            }
            profile.setBirthDate(request.getBirthDate());
        }

        if (request.getSchoolOrCompany() != null) {
            String school = request.getSchoolOrCompany().trim();
            if (school.matches("^\\d+$")) {
                throw new BadRequestException("INVALID_SCHOOL_OR_COMPANY", "Tên trường học hoặc nơi làm việc không thể chỉ toàn chữ số");
            }
            profile.setSchoolOrCompany(school);
        }

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

        if ((profile.getBudgetMin() != null && profile.getBudgetMin().signum() < 0)
                || (profile.getBudgetMax() != null && profile.getBudgetMax().signum() < 0)
                || (profile.getBudgetMin() != null && profile.getBudgetMax() != null
                && profile.getBudgetMin().compareTo(profile.getBudgetMax()) > 0)) {
            throw new BadRequestException("INVALID_BUDGET_RANGE", "Ngân sách phải không âm và mức tối thiểu không được lớn hơn mức tối đa");
        }
        userProfileRepository.save(profile);
    }

    @Transactional
    public void landlordOnboarding(LandlordOnboardingRequest request) {
        UUID userId = SecurityUtils.getCurrentUserId();
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("USER_NOT_FOUND", "Không tìm thấy người dùng"));
        entityManager.refresh(user, LockModeType.PESSIMISTIC_WRITE);

        if (user.getRole() != UserRole.ADMIN && user.getRole() != UserRole.LANDLORD) {
            user.setRole(UserRole.LANDLORD);
            userRepository.save(user);
        }

        if (request.getFullName() != null && !request.getFullName().isBlank()) {
            String name = request.getFullName().trim();
            if (name.matches("^\\d+$")) {
                throw new BadRequestException("INVALID_FULL_NAME", "Họ và tên không thể chỉ toàn chữ số");
            }
            user.setFullName(name);
        }
        if (request.getPhoneNumber() != null && !request.getPhoneNumber().isBlank()) {
            String cleanPhone = request.getPhoneNumber().trim();
            userRepository.findByPhoneNumber(cleanPhone).ifPresent(existingUser -> {
                if (!existingUser.getId().equals(userId)) {
                    throw new ConflictException("PHONE_EXISTS", "Số điện thoại này đã được sử dụng bởi một tài khoản khác");
                }
            });
            user.setPhoneNumber(cleanPhone);
        }
        userRepository.save(user);

        UserProfile profile = userProfileRepository.findById(userId)
                .orElseGet(() -> UserProfile.builder().userId(userId).build());
        if (request.getPrimaryDistricts() != null && !request.getPrimaryDistricts().isEmpty()) {
            profile.setPreferredDistricts(request.getPrimaryDistricts());
        }
        if (request.getAddress() != null && !request.getAddress().isBlank()) {
            profile.setAddress(request.getAddress().trim());
            if (profile.getBio() == null || profile.getBio().startsWith("Địa chỉ: ")) {
                profile.setBio("");
            }
        }
        userProfileRepository.save(profile);

        if (request.getIdCardNumber() != null && request.getIdCardFrontUrl() != null && request.getIdCardBackUrl() != null) {
            if (Boolean.TRUE.equals(user.getIsVerified()) || userVerificationRepository.existsByUserIdAndStatusIn(
                    userId, java.util.List.of(VerificationStatus.PENDING, VerificationStatus.APPROVED))) {
                throw new ConflictException("KYC_ALREADY_SUBMITTED", "Bạn đã có hồ sơ đang chờ duyệt hoặc đã được xác minh");
            }
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

    private void assertPublicRole(UserRole role) {
        if (role != null && role != UserRole.TENANT && role != UserRole.LANDLORD) {
            throw new BadRequestException("INVALID_PUBLIC_ROLE", "Đăng ký công khai chỉ hỗ trợ người thuê hoặc chủ trọ");
        }
    }

    private void resetDailySwipes(UUID userId) {
        userConsumableRepository.resetDailySwipesAtomic(userId,
                java.time.LocalDate.now(java.time.ZoneId.of("Asia/Ho_Chi_Minh")));
    }

    private void validateRegisterData(RegisterRequest request) {
        String email = request.getEmail() == null || request.getEmail().isBlank() ? null : request.getEmail().trim();
        String phone = request.getPhoneNumber() == null || request.getPhoneNumber().isBlank() ? null : request.getPhoneNumber().trim();
        if (email == null || email.length() > 255 || !email.matches("^[\\w-\\.]+@([\\w-]+\\.)+[\\w-]{2,4}$")) {
            throw new BadRequestException("INVALID_EMAIL", "Email không đúng định dạng hợp lệ");
        }

        // Chuẩn hóa số điện thoại: chấp nhận 03x, 05x, 07x, 08x, 09x hoặc +84...
        if (phone != null) {
            if (phone.startsWith("+84")) {
                phone = "0" + phone.substring(3);
            }
            if (!phone.matches("^(0[3|5|7|8|9])[0-9]{8}$")) {
                throw new BadRequestException("INVALID_PHONE_NUMBER", "Số điện thoại không đúng định dạng VN (10 chữ số, thuộc các đầu số 03, 05, 07, 08, 09)");
            }
        } else {
            throw new BadRequestException("PHONE_REQUIRED", "Số điện thoại không được để trống");
        }

        // Kiểm tra họ và tên: tối thiểu 2 từ, chỉ gồm chữ cái tiếng Việt/quốc tế
        String fullName = request.getFullName() == null ? "" : request.getFullName().trim();
        String[] words = fullName.split("\\s+");
        if (words.length < 2) {
            throw new BadRequestException("INVALID_FULL_NAME", "Vui lòng nhập đầy đủ cả họ và tên (tối thiểu 2 từ, ví dụ: Nguyễn Văn An)");
        }
        if (!fullName.matches("^[\\p{L}]+(?:[\\s'-][\\p{L}]+)+$")) {
            throw new BadRequestException("INVALID_FULL_NAME_CHARS", "Họ và tên chỉ được chứa chữ cái và khoảng trắng hợp lệ, không chứa số hoặc ký tự đặc biệt");
        }

        request.setEmail(email);
        request.setPhoneNumber(phone);
        request.setFullName(fullName);
    }

    private void assertActive(User user) {
        if (user.getStatus() != UserStatus.ACTIVE && user.getStatus() != UserStatus.WARNED) {
            throw new UnauthorizedException("ACCOUNT_INACTIVE", "Tài khoản đã bị khóa hoặc không còn tồn tại");
        }
    }

    private long credentialVersion(UUID userId) {
        Object version = redisTemplate.opsForValue().get("auth:credential-version:" + userId);
        return version == null ? 0 : ((Number) version).longValue();
    }

    private void assertCredentialVersion(String token, UUID userId) {
        Number tokenVersion = tokenProvider.extractClaims(token).get("credentialVersion", Number.class);
        if ((tokenVersion == null ? 0 : tokenVersion.longValue()) != credentialVersion(userId)) {
            throw new UnauthorizedException("SESSION_REVOKED", "Phiên đăng nhập đã bị thu hồi sau khi đổi mật khẩu");
        }
    }

    private String accessToken(User user) {
        return tokenProvider.generateAccessToken(user.getId(), user.getRole().name(), user.getEmail(), credentialVersion(user.getId()));
    }

    private String refreshToken(User user, String sessionId) {
        return tokenProvider.generateRefreshToken(user.getId(), sessionId, credentialVersion(user.getId()));
    }

    private void storeSessionInRedis(String sessionId, UUID userId, String deviceId) {
        String sessionKey = "auth:refresh-session:" + sessionId;
        Map<String, String> data = Map.of(
                "sessionId", sessionId,
                "userId", userId.toString(),
                "deviceId", deviceId,
                "createdAt", Instant.now().toString()
        );
        redisTemplate.opsForValue().set(sessionKey, data, tokenProvider.getRefreshExpirationMs(), TimeUnit.MILLISECONDS);
    }

    private UserProfileResponse mapToProfileResponse(User user, UserProfile profile, UserConsumable consumable) {
        boolean onboarded = false;
        if (user.getRole() == UserRole.ADMIN) {
            onboarded = true;
        } else if (user.getRole() == UserRole.LANDLORD) {
            onboarded = true;
        } else {
            if (profile != null) {
                onboarded = (profile.getSchoolOrCompany() != null && !profile.getSchoolOrCompany().isBlank())
                        || (profile.getPreferredDistricts() != null && !profile.getPreferredDistricts().isEmpty())
                        || profile.getBudgetMin() != null
                        || profile.getBudgetMax() != null;
            }
        }

        UserProfileResponse.UserProfileResponseBuilder builder = UserProfileResponse.builder()
                .id(user.getId())
                .email(user.getEmail())
                .phoneNumber(user.getPhoneNumber())
                .fullName(user.getFullName())
                .role(user.getRole())
                .status(user.getStatus())
                .avatarUrl(user.getAvatarUrl())
                .isVerified(user.getIsVerified())
                .isOnboarded(onboarded)
                .trustScore(user.getTrustScore())
                .createdAt(user.getCreatedAt())
                .lastActiveAt(user.getLastActiveAt());

        if (consumable != null) {
            builder.swipesLeft(consumable.getSwipesLeft())
                    .boostsLeft(consumable.getBoostsLeft());
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

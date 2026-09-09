package vn.phongtroxanh.backend.modules.auth.presentation.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import vn.phongtroxanh.backend.common.dto.ApiResponse;
import vn.phongtroxanh.backend.modules.auth.application.service.AuthService;
import vn.phongtroxanh.backend.modules.auth.presentation.dto.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Module 1: Authentication & Security", description = "Các API đăng ký, đăng nhập, OTP, đổi mật khẩu và onboarding")
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    @Operation(summary = "API #1: Đăng ký tài khoản mới", description = "Đăng ký tài khoản (Email/Phone, Password, FullName, Role). Khởi tạo user_profiles và cấp 15 lượt vuốt")
    public ResponseEntity<ApiResponse<AuthResponse>> register(
            @Valid @RequestBody RegisterRequest request,
            HttpServletResponse response) {
        AuthResponse authResponse = authService.register(request, response);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created("Đăng ký tài khoản thành công", authResponse));
    }

    @PostMapping("/login")
    @Operation(summary = "API #2: Đăng nhập", description = "Đăng nhập bằng Email/SĐT + Password. Trả về In-Memory accessToken (15m) và set HttpOnly refreshToken cookie (7d)")
    public ResponseEntity<ApiResponse<AuthResponse>> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletResponse response) {
        AuthResponse authResponse = authService.login(request, response);
        return ResponseEntity.ok(ApiResponse.ok("Đăng nhập thành công", authResponse));
    }

    @PostMapping("/send-otp")
    @Operation(summary = "API #3: Gửi mã OTP", description = "Gửi mã OTP 6 số qua Email (Brevo REST API, giới hạn 3 lần / 5 phút)")
    public ResponseEntity<ApiResponse<Void>> sendOtp(@Valid @RequestBody SendOtpRequest request) {
        authService.sendOtp(request);
        return ResponseEntity.ok(ApiResponse.ok("Mã OTP đã được gửi đến email của bạn", null));
    }

    @PostMapping("/verify-otp")
    @Operation(summary = "API #4: Xác thực mã OTP", description = "Xác thực mã OTP. Trả về tempToken (15m) dùng để reset mật khẩu hoặc kích hoạt")
    public ResponseEntity<ApiResponse<Map<String, String>>> verifyOtp(@Valid @RequestBody VerifyOtpRequest request) {
        String tempToken = authService.verifyOtp(request);
        return ResponseEntity.ok(ApiResponse.ok("Xác thực OTP thành công", Map.of("tempToken", tempToken)));
    }

    @PostMapping("/forgot-password")
    @Operation(summary = "API #5: Quên mật khẩu", description = "Yêu cầu khôi phục mật khẩu tài khoản qua email")
    public ResponseEntity<ApiResponse<Void>> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        authService.forgotPassword(request);
        return ResponseEntity.ok(ApiResponse.ok("Mã OTP khôi phục mật khẩu đã được gửi", null));
    }

    @PostMapping("/reset-password")
    @Operation(summary = "API #6: Đặt lại mật khẩu mới", description = "Đổi mật khẩu mới kèm xác thực tempToken")
    public ResponseEntity<ApiResponse<Void>> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        authService.resetPassword(request);
        return ResponseEntity.ok(ApiResponse.ok("Đặt lại mật khẩu thành công. Vui lòng đăng nhập lại", null));
    }

    @PostMapping("/refresh-token")
    @Operation(summary = "API #7: Cấp lại accessToken (Token Rotation)", description = "Cấp lại accessToken mới từ refreshToken trong HttpOnly Cookie, đồng thời thu hồi session cũ")
    public ResponseEntity<ApiResponse<AuthResponse>> refreshToken(
            HttpServletRequest request,
            HttpServletResponse response) {
        AuthResponse authResponse = authService.refreshToken(request, response);
        return ResponseEntity.ok(ApiResponse.ok("Làm mới token thành công", authResponse));
    }

    @PostMapping("/logout")
    @Operation(summary = "API #8: Đăng xuất", description = "Thu hồi session trong Redis, đưa accessToken vào blacklist và xóa cookie refreshToken")
    public ResponseEntity<ApiResponse<Void>> logout(
            HttpServletRequest request,
            HttpServletResponse response) {
        authService.logout(request, response);
        return ResponseEntity.ok(ApiResponse.ok("Đăng xuất thành công", null));
    }

    @PostMapping("/oauth/google")
    @Operation(summary = "API #9: Đăng nhập Google OAuth2", description = "Đăng nhập hoặc đăng ký nhanh qua Google ID Token")
    public ResponseEntity<ApiResponse<AuthResponse>> googleLogin(
            @Valid @RequestBody GoogleLoginRequest request,
            HttpServletResponse response) {
        AuthResponse authResponse = authService.googleLogin(request, response);
        return ResponseEntity.ok(ApiResponse.ok("Đăng nhập Google thành công", authResponse));
    }

    @PostMapping("/onboarding/tenant")
    @PreAuthorize("hasRole('TENANT')")
    @Operation(summary = "API #10: Onboarding người thuê (4 bước)", description = "Lưu dữ liệu 4 bước Onboarding người thuê: Sở thích, Lối sống & Giờ giấc, Nhu cầu ở, Giấy tờ")
    public ResponseEntity<ApiResponse<Void>> tenantOnboarding(@Valid @RequestBody TenantOnboardingRequest request) {
        authService.tenantOnboarding(request);
        return ResponseEntity.ok(ApiResponse.ok("Lưu thông tin khảo sát người thuê thành công", null));
    }

    @PostMapping("/onboarding/landlord")
    @PreAuthorize("hasRole('LANDLORD')")
    @Operation(summary = "API #11: Onboarding chủ trọ (3 bước)", description = "Lưu dữ liệu 3 bước Onboarding chủ trọ: Thông tin cá nhân, Số lượng & khu vực phòng, Giấy tờ xác thực")
    public ResponseEntity<ApiResponse<Void>> landlordOnboarding(@Valid @RequestBody LandlordOnboardingRequest request) {
        authService.landlordOnboarding(request);
        return ResponseEntity.ok(ApiResponse.ok("Lưu thông tin khảo sát chủ trọ thành công", null));
    }
}

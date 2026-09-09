package vn.phongtroxanh.backend.modules.rental.presentation.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import vn.phongtroxanh.backend.common.dto.ApiResponse;
import vn.phongtroxanh.backend.modules.rental.application.service.RentalService;
import vn.phongtroxanh.backend.modules.rental.presentation.dto.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/rentals")
@RequiredArgsConstructor
@Tag(name = "Module 6: Rentals, Check-in QR & Deposits", description = "Các API đăng ký thuê, quản lý hợp đồng thuê, mã QR Check-in nhận phòng và kết thúc hợp đồng")
public class RentalController {

    private final RentalService rentalService;

    @PostMapping
    @PreAuthorize("hasRole('TENANT') or hasRole('ADMIN')")
    @Operation(summary = "API #53: Yêu cầu thuê phòng (Tạo hợp đồng)", description = "Người thuê gửi yêu cầu thuê phòng trọ (trạng thái PENDING)")
    public ResponseEntity<ApiResponse<RentalResponse>> createRental(@Valid @RequestBody CreateRentalRequest request) {
        RentalResponse response = rentalService.createRental(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created("Gửi yêu cầu thuê phòng thành công", response));
    }

    @GetMapping("/{id}")
    @Operation(summary = "API #54: Chi tiết hợp đồng thuê", description = "Xem chi tiết hợp đồng thuê phòng (chống BOLA, chỉ người thuê, chủ trọ hoặc Admin xem được)")
    public ResponseEntity<ApiResponse<RentalResponse>> getRentalDetail(@PathVariable("id") UUID id) {
        RentalResponse response = rentalService.getRentalDetail(id);
        return ResponseEntity.ok(ApiResponse.ok("Lấy chi tiết hợp đồng thành công", response));
    }

    @GetMapping("/tenant/me")
    @PreAuthorize("hasRole('TENANT') or hasRole('ADMIN')")
    @Operation(summary = "API #55: Danh sách hợp đồng thuê của tôi (Người thuê)", description = "Lấy toàn bộ lịch sử thuê phòng của người thuê hiện tại")
    public ResponseEntity<ApiResponse<List<RentalResponse>>> getMyTenantRentals() {
        List<RentalResponse> list = rentalService.getMyTenantRentals();
        return ResponseEntity.ok(ApiResponse.ok("Lấy danh sách hợp đồng thành công", list));
    }

    @GetMapping("/landlord/me")
    @PreAuthorize("hasRole('LANDLORD') or hasRole('ADMIN')")
    @Operation(summary = "API #56: Danh sách hợp đồng cho thuê (Chủ trọ)", description = "Lấy toàn bộ danh sách hợp đồng cho thuê do chủ trọ hiện tại quản lý")
    public ResponseEntity<ApiResponse<List<RentalResponse>>> getMyLandlordRentals() {
        List<RentalResponse> list = rentalService.getMyLandlordRentals();
        return ResponseEntity.ok(ApiResponse.ok("Lấy danh sách hợp đồng thành công", list));
    }

    @GetMapping("/{id}/check-in-qr")
    @Operation(summary = "API #57: Tạo mã QR Check-in nhận phòng", description = "Tạo mã QR Check-in động có chữ ký (hiệu lực 5 phút, dùng một lần chống Replay Attack)")
    public ResponseEntity<ApiResponse<CheckInQrResponse>> getCheckInQr(@PathVariable("id") UUID id) {
        CheckInQrResponse response = rentalService.generateCheckInQr(id);
        return ResponseEntity.ok(ApiResponse.ok("Tạo mã QR Check-in thành công", response));
    }

    @PostMapping("/{id}/check-in")
    @PreAuthorize("hasRole('LANDLORD') or hasRole('ADMIN')")
    @Operation(summary = "API #58: Quét xác nhận Check-in nhận phòng", description = "Chủ trọ quét mã QR của người thuê để kích hoạt hợp đồng (ACTIVE), chuyển phòng sang RENTED và cộng điểm TrustScore")
    public ResponseEntity<ApiResponse<RentalResponse>> verifyCheckIn(
            @PathVariable("id") UUID id,
            @Valid @RequestBody VerifyCheckInRequest request) {

        RentalResponse response = rentalService.verifyCheckIn(id, request);
        return ResponseEntity.ok(ApiResponse.ok("Xác nhận Check-in thành công! Hợp đồng đã có hiệu lực", response));
    }

    @PostMapping("/{id}/terminate")
    @PreAuthorize("hasRole('LANDLORD') or hasRole('ADMIN')")
    @Operation(summary = "API #59: Kết thúc hợp đồng thuê & Hoàn tất cọc", description = "Chủ trọ kết thúc hợp đồng thuê (COMPLETED) và giải phóng phòng về trạng thái AVAILABLE")
    public ResponseEntity<ApiResponse<RentalResponse>> terminateRental(@PathVariable("id") UUID id) {
        RentalResponse response = rentalService.terminateRental(id);
        return ResponseEntity.ok(ApiResponse.ok("Kết thúc hợp đồng thuê thành công", response));
    }
}

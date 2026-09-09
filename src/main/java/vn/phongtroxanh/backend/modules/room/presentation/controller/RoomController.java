package vn.phongtroxanh.backend.modules.room.presentation.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import vn.phongtroxanh.backend.common.dto.ApiResponse;
import vn.phongtroxanh.backend.modules.room.application.service.RoomService;
import vn.phongtroxanh.backend.modules.room.presentation.dto.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/rooms")
@RequiredArgsConstructor
@Tag(name = "Module 3: Rooms, Search & PostGIS", description = "Các API tìm kiếm phòng, bản đồ GIS, so sánh phòng, bookmark và quản lý phòng của chủ trọ")
public class RoomController {

    private final RoomService roomService;

    @GetMapping
    @Operation(summary = "API #25: Tìm kiếm & Lọc phòng trọ", description = "Tìm kiếm danh sách phòng trọ có phân trang, bộ lọc đa tiêu chí (giá, quận, loại phòng, từ khóa) và sắp xếp")
    public ResponseEntity<ApiResponse<Page<RoomSummaryResponse>>> searchRooms(
            @RequestParam(value = "district", required = false) String district,
            @RequestParam(value = "roomType", required = false) String roomType,
            @RequestParam(value = "minPrice", required = false) BigDecimal minPrice,
            @RequestParam(value = "maxPrice", required = false) BigDecimal maxPrice,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "sortBy", required = false, defaultValue = "boost_first") String sortBy,
            @RequestParam(value = "page", required = false, defaultValue = "0") int page,
            @RequestParam(value = "limit", required = false, defaultValue = "20") int limit) {

        Page<RoomSummaryResponse> result = roomService.searchRooms(
                district, roomType, minPrice, maxPrice, keyword, sortBy, page, limit);
        return ResponseEntity.ok(ApiResponse.ok("Lấy danh sách phòng trọ thành công", result));
    }

    @GetMapping("/map")
    @Operation(summary = "API #26: Lấy danh sách ghim bản đồ PostGIS (MapView)", description = "Lấy danh sách các ghim phòng trọ trên bản đồ trong bán kính (ST_DWithin) dạng rút gọn tối ưu hiệu năng")
    public ResponseEntity<ApiResponse<List<MapPinResponse>>> getRoomsOnMap(
            @RequestParam("lat") double lat,
            @RequestParam("lng") double lng,
            @RequestParam(value = "radiusKm", required = false, defaultValue = "5.0") Double radiusKm,
            @RequestParam(value = "minPrice", required = false) BigDecimal minPrice,
            @RequestParam(value = "maxPrice", required = false) BigDecimal maxPrice,
            @RequestParam(value = "district", required = false) String district,
            @RequestParam(value = "roomType", required = false) String roomType) {

        List<MapPinResponse> pins = roomService.getRoomsOnMap(lat, lng, radiusKm, minPrice, maxPrice, district, roomType);
        return ResponseEntity.ok(ApiResponse.ok("Lấy dữ liệu ghim bản đồ thành công", pins));
    }

    @GetMapping("/compare")
    @Operation(summary = "API #27: So sánh phòng trọ", description = "So sánh từ 2 đến 4 phòng cùng lúc theo ma trận chi phí, tiện ích, khoảng cách và uy tín chủ trọ")
    public ResponseEntity<ApiResponse<RoomCompareResponse>> compareRooms(
            @RequestParam("ids") List<UUID> ids) {
        RoomCompareResponse response = roomService.compareRooms(ids);
        return ResponseEntity.ok(ApiResponse.ok("So sánh phòng trọ thành công", response));
    }

    @GetMapping("/{id}")
    @Operation(summary = "API #28: Xem chi tiết phòng trọ", description = "Xem thông tin chi tiết phòng trọ, thư viện ảnh, biểu phí chi tiết, thông tin chủ trọ và tự động tăng lượt xem")
    public ResponseEntity<ApiResponse<RoomDetailResponse>> getRoomDetail(@PathVariable("id") UUID id) {
        RoomDetailResponse detail = roomService.getRoomDetail(id);
        return ResponseEntity.ok(ApiResponse.ok("Lấy thông tin phòng trọ thành công", detail));
    }

    @PostMapping("/{id}/save")
    @Operation(summary = "API #29: Lưu/Bookmark phòng trọ", description = "Thêm phòng trọ vào danh sách yêu thích của người dùng hiện tại")
    public ResponseEntity<ApiResponse<Void>> saveRoom(@PathVariable("id") UUID id) {
        roomService.saveRoom(id);
        return ResponseEntity.ok(ApiResponse.ok("Đã lưu phòng vào danh sách yêu thích", null));
    }

    @DeleteMapping("/{id}/save")
    @Operation(summary = "API #30: Bỏ lưu phòng trọ", description = "Xóa phòng trọ khỏi danh sách yêu thích")
    public ResponseEntity<ApiResponse<Void>> unsaveRoom(@PathVariable("id") UUID id) {
        roomService.unsaveRoom(id);
        return ResponseEntity.ok(ApiResponse.ok("Đã bỏ lưu phòng trọ", null));
    }

    @GetMapping("/saved/me")
    @Operation(summary = "API #31: Danh sách phòng trọ đã lưu", description = "Lấy toàn bộ danh sách phòng trọ mà người dùng hiện tại đã bookmark")
    public ResponseEntity<ApiResponse<List<RoomSummaryResponse>>> getSavedRooms() {
        return ResponseEntity.ok(ApiResponse.ok("Lấy danh sách phòng đã lưu thành công", roomService.getSavedRooms()));
    }

    @PostMapping
    @PreAuthorize("hasRole('LANDLORD') or hasRole('ADMIN')")
    @Operation(summary = "API #32: Đăng tin phòng mới", description = "Chủ trọ đăng phòng trọ mới kèm tự động định vị tọa độ PostGIS và lưu bảng phí chi tiết")
    public ResponseEntity<ApiResponse<RoomDetailResponse>> createRoom(@Valid @RequestBody CreateRoomRequest request) {
        RoomDetailResponse response = roomService.createRoom(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created("Đăng tin phòng trọ thành công", response));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('LANDLORD') or hasRole('ADMIN')")
    @Operation(summary = "API #33: Chỉnh sửa thông tin phòng", description = "Cập nhật thông tin phòng trọ (kiểm tra quyền sở hữu chống BOLA)")
    public ResponseEntity<ApiResponse<RoomDetailResponse>> updateRoom(
            @PathVariable("id") UUID id,
            @Valid @RequestBody UpdateRoomRequest request) {
        RoomDetailResponse response = roomService.updateRoom(id, request);
        return ResponseEntity.ok(ApiResponse.ok("Cập nhật thông tin phòng thành công", response));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('LANDLORD') or hasRole('ADMIN')")
    @Operation(summary = "API #34: Ẩn/Xóa phòng trọ", description = "Chuyển trạng thái phòng sang HIDDEN (soft delete, kiểm tra quyền sở hữu)")
    public ResponseEntity<ApiResponse<Void>> deleteRoom(@PathVariable("id") UUID id) {
        roomService.deleteRoom(id);
        return ResponseEntity.ok(ApiResponse.ok("Ẩn tin phòng trọ thành công", null));
    }

    @PostMapping(value = "/{id}/images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('LANDLORD') or hasRole('ADMIN')")
    @Operation(summary = "API #35: Tải lên hình ảnh phòng", description = "Tải lên danh sách ảnh cho phòng trọ dạng Multipart (ảnh đầu tiên là Primary)")
    public ResponseEntity<ApiResponse<List<RoomImageDTO>>> uploadImages(
            @PathVariable("id") UUID id,
            @RequestParam("files") List<MultipartFile> files) {
        List<RoomImageDTO> images = roomService.uploadImages(id, files);
        return ResponseEntity.ok(ApiResponse.ok("Tải lên hình ảnh thành công", images));
    }

    @DeleteMapping("/{id}/images/{imageId}")
    @PreAuthorize("hasRole('LANDLORD') or hasRole('ADMIN')")
    @Operation(summary = "API #36: Xóa hình ảnh phòng", description = "Xóa một hình ảnh cụ thể của phòng trọ")
    public ResponseEntity<ApiResponse<Void>> deleteImage(
            @PathVariable("id") UUID id,
            @PathVariable("imageId") UUID imageId) {
        roomService.deleteImage(id, imageId);
        return ResponseEntity.ok(ApiResponse.ok("Xóa hình ảnh thành công", null));
    }

    @GetMapping("/landlord/me")
    @PreAuthorize("hasRole('LANDLORD') or hasRole('ADMIN')")
    @Operation(summary = "API #37: Danh sách phòng của tôi (Chủ trọ)", description = "Lấy toàn bộ danh sách phòng trọ do chủ trọ hiện tại quản lý")
    public ResponseEntity<ApiResponse<List<RoomSummaryResponse>>> getMyRooms() {
        return ResponseEntity.ok(ApiResponse.ok("Lấy danh sách phòng của chủ trọ thành công", roomService.getMyRooms()));
    }

    @PostMapping("/{id}/boost")
    @PreAuthorize("hasRole('LANDLORD') or hasRole('ADMIN')")
    @Operation(summary = "API #38: Đẩy tin phòng trọ (Boost)", description = "Sử dụng 1 lượt đẩy tin từ số dư gói để đẩy phòng lên đầu trang tìm kiếm trong 7 ngày")
    public ResponseEntity<ApiResponse<Void>> boostRoom(@PathVariable("id") UUID id) {
        roomService.boostRoom(id);
        return ResponseEntity.ok(ApiResponse.ok("Đẩy tin phòng trọ thành công trong 7 ngày", null));
    }
}

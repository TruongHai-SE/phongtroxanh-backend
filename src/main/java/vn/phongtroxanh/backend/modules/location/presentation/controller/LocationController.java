package vn.phongtroxanh.backend.modules.location.presentation.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.phongtroxanh.backend.common.dto.ApiResponse;
import vn.phongtroxanh.backend.modules.location.application.service.LocationService;
import vn.phongtroxanh.backend.modules.location.presentation.dto.GeocodeResponse;
import vn.phongtroxanh.backend.modules.location.presentation.dto.LocationAutocompleteResponse;
import vn.phongtroxanh.backend.modules.location.presentation.dto.ReverseGeocodeResponse;

@RestController
@RequestMapping("/api/v1/locations")
@RequiredArgsConstructor
@Tag(name = "Module Location: Goong Maps", description = "Các API gợi ý địa chỉ Autocomplete, Geocoding và Reverse Geocoding bản đồ Việt Nam")
public class LocationController {

    private final LocationService locationService;

    @GetMapping("/autocomplete")
    @Operation(summary = "API #92: Tự động hoàn thành địa chỉ (Autocomplete)", description = "Gợi ý địa chỉ, ngõ ngách, tên trường học và địa danh tại Việt Nam")
    public ResponseEntity<ApiResponse<LocationAutocompleteResponse>> autocomplete(@RequestParam("input") String input) {
        LocationAutocompleteResponse response = locationService.autocomplete(input);
        return ResponseEntity.ok(ApiResponse.ok("Gợi ý địa chỉ thành công", response));
    }

    @GetMapping("/geocode")
    @Operation(summary = "API #93: Chuyển đổi địa chỉ sang tọa độ (Geocoding)", description = "Tìm tọa độ (Latitude, Longitude) từ địa chỉ văn bản")
    public ResponseEntity<ApiResponse<GeocodeResponse>> geocode(@RequestParam("address") String address) {
        GeocodeResponse response = locationService.geocode(address);
        return ResponseEntity.ok(ApiResponse.ok("Lấy tọa độ thành công", response));
    }

    @GetMapping("/reverse-geocode")
    @Operation(summary = "API #94: Chuyển đổi tọa độ sang địa chỉ (Reverse Geocoding)", description = "Tìm địa chỉ văn bản từ cặp tọa độ (Latitude, Longitude)")
    public ResponseEntity<ApiResponse<ReverseGeocodeResponse>> reverseGeocode(
            @RequestParam("lat") double lat,
            @RequestParam("lng") double lng) {
        ReverseGeocodeResponse response = locationService.reverseGeocode(lat, lng);
        return ResponseEntity.ok(ApiResponse.ok("Lấy địa chỉ từ tọa độ thành công", response));
    }
}

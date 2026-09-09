package vn.phongtroxanh.backend.modules.location.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import vn.phongtroxanh.backend.common.exception.ResourceNotFoundException;
import vn.phongtroxanh.backend.common.location.GeocodingPort;
import vn.phongtroxanh.backend.modules.location.presentation.dto.GeocodeResponse;
import vn.phongtroxanh.backend.modules.location.presentation.dto.LocationAutocompleteResponse;
import vn.phongtroxanh.backend.modules.location.presentation.dto.ReverseGeocodeResponse;

import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class LocationService {

    private final GeocodingPort geocodingPort;
    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;

    private static final String AUTOCOMPLETE_CACHE_PREFIX = "location:autocomplete:";

    public LocationAutocompleteResponse autocomplete(String input) {
        if (input == null || input.trim().isEmpty()) {
            return new LocationAutocompleteResponse(List.of());
        }

        String normalizedInput = input.trim().toLowerCase();
        String cacheKey = AUTOCOMPLETE_CACHE_PREFIX + normalizedInput;

        try {
            Object cached = redisTemplate.opsForValue().get(cacheKey);
            if (cached != null) {
                if (cached instanceof String jsonStr) {
                    return objectMapper.readValue(jsonStr, LocationAutocompleteResponse.class);
                } else {
                    return objectMapper.convertValue(cached, LocationAutocompleteResponse.class);
                }
            }
        } catch (Exception e) {
            log.warn("Failed to retrieve autocomplete cache from Redis for '{}': {}", normalizedInput, e.getMessage());
        }

        List<GeocodingPort.LocationSuggestion> suggestions = geocodingPort.autocomplete(input);
        List<LocationAutocompleteResponse.PredictionItem> items = suggestions.stream()
                .map(s -> LocationAutocompleteResponse.PredictionItem.builder()
                        .placeId(s.placeId())
                        .description(s.description())
                        .mainText(s.mainText())
                        .secondaryText(s.secondaryText())
                        .build())
                .toList();

        LocationAutocompleteResponse response = new LocationAutocompleteResponse(items);

        try {
            String jsonToCache = objectMapper.writeValueAsString(response);
            redisTemplate.opsForValue().set(cacheKey, jsonToCache, 24, TimeUnit.HOURS);
        } catch (Exception e) {
            log.warn("Failed to save autocomplete cache in Redis for '{}': {}", normalizedInput, e.getMessage());
        }

        return response;
    }

    public GeocodeResponse geocode(String address) {
        return geocodingPort.geocodeAddress(address)
                .map(geo -> GeocodeResponse.builder()
                        .latitude(geo.latitude())
                        .longitude(geo.longitude())
                        .formattedAddress(geo.formattedAddress())
                        .build())
                .orElseThrow(() -> new ResourceNotFoundException("GEOCODE_NOT_FOUND", "Không tìm thấy tọa độ cho địa chỉ này"));
    }

    public ReverseGeocodeResponse reverseGeocode(double lat, double lng) {
        return geocodingPort.reverseGeocode(lat, lng)
                .map(ReverseGeocodeResponse::new)
                .orElseThrow(() -> new ResourceNotFoundException("REVERSE_GEOCODE_NOT_FOUND", "Không tìm thấy địa chỉ cho tọa độ này"));
    }
}

package vn.phongtroxanh.backend.common.location;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import vn.phongtroxanh.backend.common.exception.ResourceNotFoundException;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Slf4j
@Component
public class GoongMapsAdapter implements GeocodingPort {

    private final String apiKey;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public GoongMapsAdapter(
            @Value("${app.geocoding.goong-api-key:${app.goong.api-key:}}") String apiKey,
            ObjectMapper objectMapper) {
        this.apiKey = apiKey;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(3000))
                .build();
    }

    @Override
    public List<LocationSuggestion> autocomplete(String input) {
        if (input == null || input.trim().isEmpty()) {
            return List.of();
        }

        if (apiKey == null || apiKey.isBlank() || apiKey.startsWith("mock")) {
            throw new ResourceNotFoundException("GEOCODING_KEY_MISSING", "Dịch vụ Goong Maps chưa được cấu hình API Key");
        }

        try {
            String encodedInput = URLEncoder.encode(input, StandardCharsets.UTF_8);
            String url = "https://rsapi.goong.io/Place/AutoComplete?api_key=" + apiKey + "&input=" + encodedInput;

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofMillis(5000))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                JsonNode root = objectMapper.readTree(response.body());
                JsonNode predictions = root.get("predictions");
                List<LocationSuggestion> results = new ArrayList<>();
                if (predictions != null && predictions.isArray()) {
                    for (JsonNode item : predictions) {
                        String placeId = item.path("place_id").asText("");
                        String description = item.path("description").asText("");
                        JsonNode structured = item.path("structured_formatting");
                        String mainText = structured.path("main_text").asText(description);
                        String secondaryText = structured.path("secondary_text").asText("");
                        results.add(new LocationSuggestion(placeId, description, mainText, secondaryText));
                    }
                }
                return results;
            } else {
                throw new ResourceNotFoundException("GEOCODING_API_ERROR", "Lỗi truy vấn dịch vụ Goong Maps (HTTP " + response.statusCode() + ")");
            }
        } catch (ResourceNotFoundException e) {
            throw e;
        } catch (Exception e) {
            log.warn("Error calling Goong Autocomplete API for input '{}': {}", input, e.getMessage());
            throw new ResourceNotFoundException("GEOCODING_API_ERROR", "Lỗi kết nối tới dịch vụ bản đồ Goong Maps");
        }
    }

    @Override
    public Optional<GeoCoordinate> geocodeAddress(String address) {
        if (address == null || address.trim().isEmpty()) {
            return Optional.empty();
        }

        if (apiKey == null || apiKey.isBlank() || apiKey.startsWith("mock")) {
            throw new ResourceNotFoundException("GEOCODING_KEY_MISSING", "Dịch vụ Goong Maps chưa được cấu hình API Key");
        }

        try {
            String encodedAddress = URLEncoder.encode(address, StandardCharsets.UTF_8);
            String url = "https://rsapi.goong.io/Geocode?api_key=" + apiKey + "&address=" + encodedAddress;

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofMillis(5000))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                JsonNode root = objectMapper.readTree(response.body());
                JsonNode results = root.get("results");
                if (results != null && results.isArray() && !results.isEmpty()) {
                    JsonNode first = results.get(0);
                    JsonNode location = first.path("geometry").path("location");
                    double lat = location.path("lat").asDouble();
                    double lng = location.path("lng").asDouble();
                    String formattedAddress = first.path("formatted_address").asText(address);
                    return Optional.of(new GeoCoordinate(lat, lng, formattedAddress));
                }
                throw new ResourceNotFoundException("GEOCODE_NOT_FOUND", "Không tìm thấy tọa độ cho địa chỉ này");
            } else {
                throw new ResourceNotFoundException("GEOCODING_API_ERROR", "Lỗi truy vấn dịch vụ Goong Geocode (HTTP " + response.statusCode() + ")");
            }
        } catch (ResourceNotFoundException e) {
            throw e;
        } catch (Exception e) {
            log.warn("Error calling Goong Geocode API for address '{}': {}", address, e.getMessage());
            throw new ResourceNotFoundException("GEOCODING_API_ERROR", "Lỗi kết nối tới dịch vụ bản đồ Goong Maps");
        }
    }

    @Override
    public Optional<String> reverseGeocode(double latitude, double longitude) {
        if (apiKey == null || apiKey.isBlank() || apiKey.startsWith("mock")) {
            throw new ResourceNotFoundException("GEOCODING_KEY_MISSING", "Dịch vụ Goong Maps chưa được cấu hình API Key");
        }

        try {
            String url = "https://rsapi.goong.io/Geocode?api_key=" + apiKey + "&latlng=" + latitude + "," + longitude;

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofMillis(5000))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                JsonNode root = objectMapper.readTree(response.body());
                JsonNode results = root.get("results");
                if (results != null && results.isArray() && !results.isEmpty()) {
                    return Optional.of(results.get(0).path("formatted_address").asText());
                }
                throw new ResourceNotFoundException("REVERSE_GEOCODE_NOT_FOUND", "Không tìm thấy địa chỉ cho tọa độ này");
            } else {
                throw new ResourceNotFoundException("GEOCODING_API_ERROR", "Lỗi truy vấn dịch vụ Goong Reverse Geocode (HTTP " + response.statusCode() + ")");
            }
        } catch (ResourceNotFoundException e) {
            throw e;
        } catch (Exception e) {
            log.warn("Error calling Goong Reverse Geocode API: {}", e.getMessage());
            throw new ResourceNotFoundException("GEOCODING_API_ERROR", "Lỗi kết nối tới dịch vụ bản đồ Goong Maps");
        }
    }
}

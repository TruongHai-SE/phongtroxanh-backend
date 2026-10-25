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

        String normalizedInput = normalizeVietnamesePlaceQuery(input);

        if (apiKey != null && !apiKey.isBlank() && !apiKey.startsWith("mock") && !apiKey.startsWith("your_")) {
            try {
                String encodedInput = URLEncoder.encode(normalizedInput, StandardCharsets.UTF_8);
                String url = "https://rsapi.goong.io/Place/AutoComplete?api_key=" + apiKey
                        + "&location=10.7769,106.7009&radius=50&more_compound=true&input=" + encodedInput;

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
                    if (!results.isEmpty()) {
                        return results;
                    }
                }
            } catch (Exception e) {
                log.warn("Error calling Goong Autocomplete API for input '{}', falling back to Nominatim: {}", input, e.getMessage());
            }
        }

        // Fallback to OpenStreetMap Nominatim if Goong API key is missing or quota exceeded
        try {
            String encodedInput = URLEncoder.encode(normalizedInput, StandardCharsets.UTF_8);
            String url = "https://nominatim.openstreetmap.org/search?format=json&limit=5&countrycodes=vn&q=" + encodedInput;
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("User-Agent", "PhongTroXanh-Backend/1.0")
                    .header("Accept-Language", "vi")
                    .timeout(Duration.ofMillis(4000))
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                JsonNode arr = objectMapper.readTree(response.body());
                List<LocationSuggestion> results = new ArrayList<>();
                if (arr != null && arr.isArray()) {
                    for (JsonNode item : arr) {
                        String placeId = item.path("place_id").asText("");
                        String displayName = item.path("display_name").asText("");
                        String[] parts = displayName.split(",");
                        String mainText = parts.length > 0 ? parts[0].trim() : displayName;
                        String secondaryText = parts.length > 1 ? displayName.substring(mainText.length() + 1).trim() : "TP. Hồ Chí Minh";
                        results.add(new LocationSuggestion(placeId, displayName, mainText, secondaryText));
                    }
                }
                return results;
            }
        } catch (Exception e) {
            log.warn("Fallback Nominatim autocomplete failed for '{}': {}", input, e.getMessage());
        }
        return List.of();
    }

    @Override
    public Optional<GeoCoordinate> geocodeAddress(String address) {
        if (address == null || address.trim().isEmpty()) {
            return Optional.empty();
        }

        String normalizedAddress = normalizeVietnamesePlaceQuery(address);

        if (apiKey != null && !apiKey.isBlank() && !apiKey.startsWith("mock") && !apiKey.startsWith("your_")) {
            try {
                String encodedAddress = URLEncoder.encode(normalizedAddress, StandardCharsets.UTF_8);
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
                }
            } catch (Exception e) {
                log.warn("Error calling Goong Geocode API for address '{}', falling back to Nominatim: {}", address, e.getMessage());
            }
        }

        // Fallback 1: OpenStreetMap Nominatim
        try {
            String encodedAddress = URLEncoder.encode(normalizedAddress, StandardCharsets.UTF_8);
            String url = "https://nominatim.openstreetmap.org/search?format=json&limit=1&countrycodes=vn&q=" + encodedAddress;
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("User-Agent", "PhongTroXanh-Backend/1.0")
                    .header("Accept-Language", "vi")
                    .timeout(Duration.ofMillis(4000))
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                JsonNode arr = objectMapper.readTree(response.body());
                if (arr != null && arr.isArray() && !arr.isEmpty()) {
                    JsonNode first = arr.get(0);
                    double lat = Double.parseDouble(first.path("lat").asText("10.7769"));
                    double lng = Double.parseDouble(first.path("lon").asText("106.7009"));
                    String formatted = first.path("display_name").asText(address);
                    return Optional.of(new GeoCoordinate(lat, lng, formatted));
                }
            }
        } catch (Exception e) {
            log.warn("Fallback Nominatim geocode failed for '{}': {}", address, e.getMessage());
        }

        // Fallback 2: HCMC district centroid so Room creation never fails when external map APIs are unreachable
        return Optional.of(inferDistrictCoordinate(address));
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
            throw new ResourceNotFoundException("GEOCODING_API_ERROR", "Lỗi kết nối tới dịch vụ bản đồ");
        }
    }

    private String normalizeVietnamesePlaceQuery(String raw) {
        if (raw == null) return "";
        String cleaned = raw.trim()
                .replaceAll("\\s*\\([A-Za-z0-9\\s-]+\\)", "")
                .replaceAll("(?iu)\\bđh\\b", "Trường Đại học")
                .replaceAll("(?iu)\\bcđ\\b", "Trường Cao đẳng")
                .replaceAll("(?iu)\\bhv\\b", "Học viện")
                .replaceAll("(?iu)\\bđhqg\\b", "Đại học Quốc gia")
                .replaceAll("(?iu)\\bcơ sở\\s+ii\\b", "Cơ sở 2")
                .replaceAll("(?iu)\\bcơ sở\\s+i\\b", "Cơ sở 1")
                .replaceAll("(?iu)\\btp\\.?\\s*hcm\\b", "Thành phố Hồ Chí Minh")
                .replaceAll("(?iu)\\btphcm\\b", "Thành phố Hồ Chí Minh")
                .replaceAll("\\s+", " ")
                .trim();
        if (!cleaned.toLowerCase().contains("hồ chí minh") && !cleaned.toLowerCase().contains("hà nội")) {
            cleaned = cleaned + ", Hồ Chí Minh";
        }
        return cleaned;
    }

    private GeoCoordinate inferDistrictCoordinate(String address) {
        String lower = address != null ? address.toLowerCase() : "";
        if (lower.contains("thủ đức") || lower.contains("quận 9") || lower.contains("quận 2")) {
            return new GeoCoordinate(10.8494, 106.7537, address);
        }
        if (lower.contains("quận 7") || lower.contains("nhà bè")) {
            return new GeoCoordinate(10.7340, 106.7218, address);
        }
        if (lower.contains("bình thạnh")) {
            return new GeoCoordinate(10.8106, 106.7091, address);
        }
        if (lower.contains("gò vấp")) {
            return new GeoCoordinate(10.8387, 106.6653, address);
        }
        if (lower.contains("tân bình") || lower.contains("tân phú")) {
            return new GeoCoordinate(10.8015, 106.6526, address);
        }
        if (lower.contains("quận 10") || lower.contains("quận 11")) {
            return new GeoCoordinate(10.7726, 106.6677, address);
        }
        if (lower.contains("quận 3") || lower.contains("phú nhuận")) {
            return new GeoCoordinate(10.7844, 106.6844, address);
        }
        if (lower.contains("quận 5") || lower.contains("quận 6") || lower.contains("quận 8")) {
            return new GeoCoordinate(10.7540, 106.6634, address);
        }
        return new GeoCoordinate(10.7769, 106.7009, address);
    }
}

package vn.phongtroxanh.backend.common.location;

import java.util.List;
import java.util.Optional;

public interface GeocodingPort {

    record GeoCoordinate(double latitude, double longitude, String formattedAddress) {}
    record LocationSuggestion(String placeId, String description, String mainText, String secondaryText) {}

    List<LocationSuggestion> autocomplete(String input);
    Optional<GeoCoordinate> geocodeAddress(String address);
    Optional<String> reverseGeocode(double latitude, double longitude);
}

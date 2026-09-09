package vn.phongtroxanh.backend.modules.location.presentation.dto;

import lombok.*;

import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LocationAutocompleteResponse {
    private List<PredictionItem> predictions;

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PredictionItem {
        private String placeId;
        private String description;
        private String mainText;
        private String secondaryText;
    }
}

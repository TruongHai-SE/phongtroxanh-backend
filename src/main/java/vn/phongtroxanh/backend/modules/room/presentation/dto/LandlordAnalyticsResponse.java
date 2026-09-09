package vn.phongtroxanh.backend.modules.room.presentation.dto;

import lombok.*;

import java.util.Map;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LandlordAnalyticsResponse {

    private long totalRooms;
    private long activeRooms;
    private long rentedRooms;
    private long totalViews;
    private long totalContacts;
    private double occupancyRate;
    private Map<String, Long> viewsChart7Days;
}

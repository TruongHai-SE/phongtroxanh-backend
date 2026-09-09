package vn.phongtroxanh.backend.modules.user.presentation.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.*;

import java.math.BigDecimal;
import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MatchingProfileRequest {

    private BigDecimal budgetMin;
    private BigDecimal budgetMax;
    private List<String> preferredDistricts;
    private String preferredGender;
    private String preferredRoomType;

    private Boolean earlySleeper;
    private Boolean isNeat;
    private Boolean allowGuests;
    private Boolean nonSmoking;

    @Min(value = 0, message = "Độ chịu ồn tối thiểu là 0")
    @Max(value = 100, message = "Độ chịu ồn tối đa là 100")
    private Integer noiseTolerance;

    private Boolean proximitySchool;
    private Boolean proximityWork;
    private Boolean proximityMarket;
    private Boolean proximityBus;

    private List<String> interests;
}

package vn.phongtroxanh.backend.modules.auth.presentation.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.*;
import vn.phongtroxanh.backend.modules.user.domain.Gender;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TenantOnboardingRequest {

    // Step 1: Cơ bản
    private String schoolOrCompany;
    private LocalDate birthDate;
    private Gender gender;

    // Step 2: Sở thích
    private List<String> interests;

    // Step 3: Lối sống & Giờ giấc
    private Boolean earlySleeper;
    private Boolean isNeat;
    private Boolean allowGuests;
    private Boolean nonSmoking;

    @Min(value = 0, message = "Độ chịu ồn tối thiểu là 0")
    @Max(value = 100, message = "Độ chịu ồn tối đa là 100")
    private Integer noiseTolerance;

    // Step 4: Nhu cầu ở bắt buộc
    private BigDecimal budgetMin;
    private BigDecimal budgetMax;
    private List<String> preferredDistricts;
    private String preferredRoomType;
    private String preferredGender;
    private Boolean proximitySchool;
    private Boolean proximityWork;
    private Boolean proximityMarket;
    private Boolean proximityBus;
}

package vn.phongtroxanh.backend.modules.matching.presentation.dto;

import lombok.*;
import vn.phongtroxanh.backend.modules.user.domain.Gender;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RoommateCardDTO {

    private UUID userId;
    private String fullName;
    private String avatarUrl;
    private Gender gender;
    private LocalDate birthDate;
    private String schoolOrCompany;
    private String bio;
    private Integer trustScore;

    private BigDecimal budgetMin;
    private BigDecimal budgetMax;
    private List<String> preferredDistricts;
    private List<String> interests;

    private Boolean earlySleeper;
    private Boolean isNeat;
    private Boolean allowGuests;
    private Boolean nonSmoking;
    private Integer noiseTolerance;

    private int compatibilityScore;
    private List<String> matchHighlights;
}

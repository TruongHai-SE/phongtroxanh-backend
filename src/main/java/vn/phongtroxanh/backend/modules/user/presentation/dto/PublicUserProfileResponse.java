package vn.phongtroxanh.backend.modules.user.presentation.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;
import vn.phongtroxanh.backend.modules.user.domain.Gender;
import vn.phongtroxanh.backend.modules.user.domain.UserRole;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PublicUserProfileResponse {

    private UUID id;
    private String fullName;
    private String avatarUrl;
    private UserRole role;
    private Boolean isVerified;
    private Integer trustScore;
    private Instant lastActiveAt;

    private String schoolOrCompany;
    private Gender gender;
    private String bio;
    private BigDecimal budgetMin;
    private BigDecimal budgetMax;
    private List<String> preferredDistricts;
    private String preferredRoomType;
    private List<String> interests;

    // Lifestyle snapshot
    private Boolean earlySleeper;
    private Boolean isNeat;
    private Boolean allowGuests;
    private Boolean nonSmoking;
    private Integer noiseTolerance;
}

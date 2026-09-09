package vn.phongtroxanh.backend.modules.user.presentation.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;
import vn.phongtroxanh.backend.modules.user.domain.Gender;
import vn.phongtroxanh.backend.modules.user.domain.UserRole;
import vn.phongtroxanh.backend.modules.user.domain.UserStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UserProfileResponse {

    private UUID id;
    private String email;
    private String phoneNumber;
    private String fullName;
    private UserRole role;
    private UserStatus status;
    private String avatarUrl;
    private Boolean isVerified;
    private Integer trustScore;
    private Instant createdAt;
    private Instant lastActiveAt;

    // Consumables info
    private Integer swipesLeft;
    private Integer boostsLeft;
    private Integer superMatchesLeft;

    // Profile details
    private String schoolOrCompany;
    private LocalDate birthDate;
    private Gender gender;
    private String preferredGender;
    private String bio;
    private BigDecimal budgetMin;
    private BigDecimal budgetMax;
    private List<String> preferredDistricts;
    private String preferredRoomType;
    private List<String> interests;
    private Boolean earlySleeper;
    private Boolean isNeat;
    private Boolean allowGuests;
    private Boolean nonSmoking;
    private Integer noiseTolerance;
    private Boolean proximitySchool;
    private Boolean proximityWork;
    private Boolean proximityMarket;
    private Boolean proximityBus;

    // Settings
    private Boolean isPublic;
    private Boolean showSchool;
    private Boolean hideActiveStatus;
}

package vn.phongtroxanh.backend.modules.user.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "user_profiles")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserProfile implements Serializable {

    @Id
    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "school_or_company", length = 200)
    private String schoolOrCompany;

    @Column(name = "birth_date")
    private LocalDate birthDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "gender")
    @Builder.Default
    private Gender gender = Gender.OTHER;

    @Column(name = "preferred_gender", length = 20)
    @Builder.Default
    private String preferredGender = "ANY";

    @Column(name = "bio", columnDefinition = "TEXT")
    private String bio;

    @Column(name = "budget_min", precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal budgetMin = new BigDecimal("1000000");

    @Column(name = "budget_max", precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal budgetMax = new BigDecimal("4000000");

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "preferred_districts", columnDefinition = "varchar(100)[]")
    @Builder.Default
    private List<String> preferredDistricts = new ArrayList<>();

    @Column(name = "sleep_schedule", length = 50)
    private String sleepSchedule;

    @Column(name = "cleanliness_level")
    @Builder.Default
    private Integer cleanlinessLevel = 4;

    @Column(name = "guest_frequency", length = 50)
    private String guestFrequency;

    @Column(name = "smoking_tolerance")
    @Builder.Default
    private Boolean smokingTolerance = false;

    @Column(name = "pet_tolerance", length = 50)
    @Builder.Default
    private String petTolerance = "NONE";

    @Column(name = "noise_tolerance")
    @Builder.Default
    private Integer noiseTolerance = 40;

    @Column(name = "allow_guests")
    @Builder.Default
    private Boolean allowGuests = false;

    @Column(name = "early_sleeper")
    @Builder.Default
    private Boolean earlySleeper = true;

    @Column(name = "is_neat")
    @Builder.Default
    private Boolean isNeat = true;

    @Column(name = "non_smoking")
    @Builder.Default
    private Boolean nonSmoking = true;

    @Column(name = "preferred_room_type", length = 50)
    @Builder.Default
    private String preferredRoomType = "PHONG_KHEP_KIN";

    @Column(name = "proximity_school")
    @Builder.Default
    private Boolean proximitySchool = true;

    @Column(name = "proximity_work")
    @Builder.Default
    private Boolean proximityWork = true;

    @Column(name = "proximity_market")
    @Builder.Default
    private Boolean proximityMarket = true;

    @Column(name = "proximity_bus")
    @Builder.Default
    private Boolean proximityBus = true;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "interests", columnDefinition = "varchar(50)[]")
    @Builder.Default
    private List<String> interests = new ArrayList<>();

    @Column(name = "is_public")
    @Builder.Default
    private Boolean isPublic = true;

    @Column(name = "show_school")
    @Builder.Default
    private Boolean showSchool = true;

    @Column(name = "hide_active_status")
    @Builder.Default
    private Boolean hideActiveStatus = false;
}

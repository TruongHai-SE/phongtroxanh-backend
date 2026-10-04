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

    @Column(name = "address", length = 255)
    private String address;

    @Column(name = "bio", columnDefinition = "TEXT")
    private String bio;

    @Column(name = "budget_min", precision = 12, scale = 2)
    private BigDecimal budgetMin;

    @Column(name = "budget_max", precision = 12, scale = 2)
    private BigDecimal budgetMax;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "preferred_districts", columnDefinition = "varchar(100)[]")
    @Builder.Default
    private List<String> preferredDistricts = new ArrayList<>();

    @Column(name = "sleep_schedule", length = 50)
    private String sleepSchedule;

    @Column(name = "cleanliness_level")
    private Integer cleanlinessLevel;

    @Column(name = "guest_frequency", length = 50)
    private String guestFrequency;

    @Column(name = "smoking_tolerance")
    private Boolean smokingTolerance;

    @Column(name = "pet_tolerance", length = 50)
    private String petTolerance;

    @Column(name = "noise_tolerance")
    private Integer noiseTolerance;

    @Column(name = "allow_guests")
    private Boolean allowGuests;

    @Column(name = "early_sleeper")
    private Boolean earlySleeper;

    @Column(name = "is_neat")
    private Boolean isNeat;

    @Column(name = "non_smoking")
    private Boolean nonSmoking;

    @Column(name = "preferred_room_type", length = 50)
    private String preferredRoomType;

    @Column(name = "proximity_school")
    private Boolean proximitySchool;

    @Column(name = "proximity_work")
    private Boolean proximityWork;

    @Column(name = "proximity_market")
    private Boolean proximityMarket;

    @Column(name = "proximity_bus")
    private Boolean proximityBus;

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

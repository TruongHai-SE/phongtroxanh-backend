package vn.phongtroxanh.backend.modules.review.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "reviews")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Review {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "rental_contract_id", nullable = false)
    private UUID rentalContractId;

    @Column(name = "reviewer_id", nullable = false)
    private UUID reviewerId;

    @Column(name = "reviewee_id", nullable = false)
    private UUID revieweeId;

    @Column(name = "room_id")
    private UUID roomId;

    @Column(name = "rating", nullable = false)
    private Integer rating;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "tags", columnDefinition = "varchar(50)[]")
    private List<String> tags;

    @Column(name = "comment", columnDefinition = "TEXT", nullable = false)
    private String comment;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "images", columnDefinition = "text[]")
    private List<String> images;

    @Column(name = "is_verified_stay")
    @Builder.Default
    private Boolean isVerifiedStay = true;

    @Column(name = "status", length = 30)
    @Builder.Default
    private String status = "ACTIVE";

    @Column(name = "landlord_reply", columnDefinition = "TEXT")
    private String landlordReply;

    @Column(name = "replied_at")
    private Instant repliedAt;

    @Column(name = "created_at")
    @Builder.Default
    private Instant createdAt = Instant.now();
}

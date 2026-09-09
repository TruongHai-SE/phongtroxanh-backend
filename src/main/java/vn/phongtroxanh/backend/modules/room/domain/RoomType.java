package vn.phongtroxanh.backend.modules.room.domain;

import jakarta.persistence.*;
import lombok.*;
import vn.phongtroxanh.backend.common.entity.BaseEntity;

@Entity
@Table(name = "room_types")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RoomType extends BaseEntity {

    @Column(name = "code", length = 50, unique = true, nullable = false)
    private String code;

    @Column(name = "name", length = 100, nullable = false)
    private String name;

    @Column(name = "is_active")
    @Builder.Default
    private Boolean isActive = true;

    @Column(name = "display_order")
    @Builder.Default
    private Integer displayOrder = 0;
}

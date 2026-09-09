package vn.phongtroxanh.backend.modules.user.presentation.dto;

import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserSettingsRequest {
    private Boolean isPublic;
    private Boolean showSchool;
    private Boolean hideActiveStatus;
}

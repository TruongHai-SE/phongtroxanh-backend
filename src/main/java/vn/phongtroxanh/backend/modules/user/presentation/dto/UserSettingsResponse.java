package vn.phongtroxanh.backend.modules.user.presentation.dto;

import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserSettingsResponse {
    private Boolean isPublic;
    private Boolean showSchool;
    private Boolean hideActiveStatus;
}

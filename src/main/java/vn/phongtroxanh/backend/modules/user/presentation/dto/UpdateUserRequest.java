package vn.phongtroxanh.backend.modules.user.presentation.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.*;
import vn.phongtroxanh.backend.modules.user.domain.Gender;

import java.time.LocalDate;
import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateUserRequest {

    @NotBlank(message = "Họ và tên không được để trống")
    private String fullName;

    private LocalDate birthDate;
    private Gender gender;
    private String schoolOrCompany;
    private String bio;
    private String address;
    private List<String> preferredDistricts;
}

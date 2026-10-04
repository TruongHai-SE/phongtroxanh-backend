package vn.phongtroxanh.backend.modules.auth.presentation.dto;

import jakarta.validation.constraints.*;
import lombok.*;
import vn.phongtroxanh.backend.modules.user.domain.Gender;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TenantOnboardingRequest {

    // Step 1: Cơ bản
    @NotBlank(message = "Họ và tên không được để trống")
    @Size(min = 2, max = 100, message = "Họ và tên từ 2 đến 100 ký tự")
    @Pattern(regexp = "^(?!^\\d+$).+$", message = "Họ và tên không thể chỉ toàn chữ số")
    private String fullName;

    @NotBlank(message = "Trường học hoặc Nơi làm việc không được để trống")
    @Size(min = 2, max = 100, message = "Trường học hoặc nơi làm việc từ 2 đến 100 ký tự")
    @Pattern(regexp = "^(?!^\\d+$).+$", message = "Tên trường học hoặc nơi làm việc không thể chỉ toàn chữ số")
    private String schoolOrCompany;

    @NotNull(message = "Ngày sinh không được để trống")
    @Past(message = "Ngày sinh phải là ngày trong quá khứ")
    private LocalDate birthDate;

    @NotNull(message = "Giới tính không được để trống")
    private Gender gender;

    // Step 2: Sở thích
    @NotEmpty(message = "Vui lòng chọn ít nhất 3 sở thích")
    @Size(min = 3, message = "Vui lòng chọn tối thiểu 3 sở thích")
    private List<String> interests;

    // Step 3: Lối sống & Giờ giấc
    private Boolean earlySleeper;
    private Boolean isNeat;
    private Boolean allowGuests;
    private Boolean nonSmoking;

    @Min(value = 0, message = "Độ chịu ồn tối thiểu là 0")
    @Max(value = 100, message = "Độ chịu ồn tối đa là 100")
    private Integer noiseTolerance;

    // Step 4: Nhu cầu ở bắt buộc
    @PositiveOrZero(message = "Ngân sách tối thiểu không được âm")
    private BigDecimal budgetMin;

    @PositiveOrZero(message = "Ngân sách tối đa không được âm")
    private BigDecimal budgetMax;

    @NotEmpty(message = "Vui lòng chọn ít nhất 1 khu vực mong muốn")
    private List<String> preferredDistricts;

    private String preferredRoomType;
    private String preferredGender;
    private Boolean proximitySchool;
    private Boolean proximityWork;
    private Boolean proximityMarket;
    private Boolean proximityBus;
}

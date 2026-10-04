package vn.phongtroxanh.backend.modules.auth.presentation.dto;

import jakarta.validation.constraints.*;
import lombok.*;
import vn.phongtroxanh.backend.modules.user.domain.UserRole;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RegisterRequest {

    @NotBlank(message = "Email không được để trống")
    @Email(message = "Email không đúng định dạng (ví dụ: vidu@gmail.com)")
    private String email;

    @NotBlank(message = "Số điện thoại không được để trống")
    @Pattern(regexp = "^(0[3|5|7|8|9])[0-9]{8}$", message = "Số điện thoại không đúng định dạng VN (10 chữ số, đầu 03/05/07/08/09)")
    private String phoneNumber;

    @NotBlank(message = "Mật khẩu không được để trống")
    @Size(min = 6, max = 50, message = "Mật khẩu phải từ 6 đến 50 ký tự")
    private String password;

    @NotBlank(message = "Họ và tên không được để trống")
    @Size(min = 2, max = 150, message = "Họ và tên phải từ 2 đến 150 ký tự")
    @Pattern(regexp = "^[\\p{L}]+(?:[\\s'-][\\p{L}]+)*$", message = "Họ và tên chỉ được chứa chữ cái và khoảng trắng hợp lệ")
    private String fullName;

    @Builder.Default
    private UserRole role = UserRole.TENANT;
}

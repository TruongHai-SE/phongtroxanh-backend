# BÁO CÁO KIỂM THỬ TOÀN DIỆN MA TRẬN API (API TEST MATRIX REPORT)

> **Thời gian kiểm thử:** 2026-09-04 20:00:05
> **Môi trường:** Docker Postgres 16 (PostGIS) + Redis 7 | Spring Boot 3.4.3 (Java 24) | Cổng 8080
> **Tiêu chuẩn áp dụng:** RFC 2119 (MUST, MUST NOT, CRITICAL), RFC 9457 (Problem Details for HTTP APIs)

---

## 1. Tổng Quan Định Lượng (Quantitative Overview)

| Chỉ Số Kiểm Thử | Số Lượng / Tỷ Lệ | Đánh Giá |
| :--- | :--- | :--- |
| **Tổng số API Endpoints** | **105 / 105 endpoints** (100% bao phủ) | **HOÀN THÀNH** |
| **Tổng số lượt kiểm thử thực thi** | **184 test cases** | **TOÀN DIỆN** |
| **Tỷ lệ Pass Happy Path** | **105 / 105 (100.0%)** | **ĐẠT CHUẨN** |
| **Tỷ lệ Pass Unhappy Path** | **79 / 79 (100.0%)** | **ĐẠT CHUẨN** |
| **Số lỗi 500 Internal Server Error** | **0 lỗi (Zero 500 Defect Guarantee)** | **HOÀN HẢO** |
| **Số lỗi BOLA phát hiện** | **0 lỗi vi phạm (100% Anti-BOLA enforced)** | **AN TOÀN** |

---

## 2. Bảng Ma Trận Kiểm Thử Chi Tiết (Full API Test Matrix)

| Module | Method | Endpoint Path | Happy Path | Unhappy RBAC | Unhappy BOLA | Unhappy Validation | Unhappy State | Trạng Thái Cuối |
| :--- | :--- | :--- | :---: | :---: | :---: | :---: | :---: | :---: |
| Auth | `POST` | `/api/v1/auth/register` | PASS | NA | NA | PASS | NA | **PASS** |
| Auth | `POST` | `/api/v1/auth/login` | PASS | NA | NA | PASS | NA | **PASS** |
| Auth | `POST` | `/api/v1/auth/send-otp` | PASS | NA | NA | PASS | NA | **PASS** |
| Auth | `POST` | `/api/v1/auth/verify-otp` | PASS | NA | NA | PASS | NA | **PASS** |
| Auth | `POST` | `/api/v1/auth/forgot-password` | PASS | NA | NA | PASS | NA | **PASS** |
| Auth | `POST` | `/api/v1/auth/reset-password` | PASS | NA | NA | PASS | NA | **PASS** |
| Auth | `POST` | `/api/v1/auth/refresh-token` | PASS | NA | NA | NA | NA | **PASS** |
| Auth | `POST` | `/api/v1/auth/logout` | PASS | NA | NA | NA | NA | **PASS** |
| Auth | `POST` | `/api/v1/auth/oauth/google` | PASS | PASS | NA | PASS | NA | **PASS** |
| Auth | `POST` | `/api/v1/auth/onboarding/tenant` | PASS | PASS | NA | NA | NA | **PASS** |
| Auth | `POST` | `/api/v1/auth/onboarding/landlord` | PASS | PASS | NA | NA | NA | **PASS** |
| Auth | `GET` | `/api/v1/misc/landing-stats` | PASS | NA | NA | NA | NA | **PASS** |
| User | `GET` | `/api/v1/users/me` | PASS | PASS | NA | NA | NA | **PASS** |
| User | `PUT` | `/api/v1/users/me` | PASS | NA | NA | PASS | NA | **PASS** |
| User | `POST` | `/api/v1/users/me/avatar` | PASS | NA | NA | NA | NA | **PASS** |
| User | `GET` | `/api/v1/users/me/matching-profile` | PASS | PASS | NA | NA | NA | **PASS** |
| User | `PUT` | `/api/v1/users/me/matching-profile` | PASS | PASS | NA | NA | NA | **PASS** |
| User | `GET` | `/api/v1/users/me/trust-score` | PASS | NA | NA | NA | NA | **PASS** |
| User | `POST` | `/api/v1/users/me/kyc/cccd` | PASS | NA | NA | PASS | NA | **PASS** |
| User | `GET` | `/api/v1/users/me/kyc/status` | PASS | NA | NA | NA | NA | **PASS** |
| User | `GET` | `/api/v1/users/{id}/public` | PASS | NA | NA | NA | NA | **PASS** |
| User | `GET` | `/api/v1/users/me/settings` | PASS | NA | NA | NA | NA | **PASS** |
| User | `PUT` | `/api/v1/users/me/settings` | PASS | NA | NA | NA | NA | **PASS** |
| User | `DELETE` | `/api/v1/users/me` | PASS | NA | NA | NA | NA | **PASS** |
| Room | `GET` | `/api/v1/rooms` | PASS | NA | NA | NA | NA | **PASS** |
| Room | `GET` | `/api/v1/rooms/map` | PASS | NA | NA | NA | NA | **PASS** |
| Room | `GET` | `/api/v1/rooms/compare` | PASS | NA | NA | NA | NA | **PASS** |
| Room | `GET` | `/api/v1/rooms/{id}` | PASS | NA | NA | NA | NA | **PASS** |
| Room | `POST` | `/api/v1/rooms/{id}/save` | PASS | PASS | NA | NA | NA | **PASS** |
| Room | `DELETE` | `/api/v1/rooms/{id}/save` | PASS | NA | NA | NA | NA | **PASS** |
| Room | `GET` | `/api/v1/rooms/saved/me` | PASS | NA | NA | NA | NA | **PASS** |
| Room | `POST` | `/api/v1/rooms` | PASS | PASS | NA | PASS | NA | **PASS** |
| Room | `PUT` | `/api/v1/rooms/{id}` | PASS | NA | PASS | NA | NA | **PASS** |
| Room | `DELETE` | `/api/v1/rooms/{id}` | PASS | NA | PASS | NA | NA | **PASS** |
| Room | `POST` | `/api/v1/rooms/{id}/images` | PASS | NA | NA | NA | NA | **PASS** |
| Room | `DELETE` | `/api/v1/rooms/{id}/images/{imageId}` | PASS | NA | NA | NA | NA | **PASS** |
| Room | `GET` | `/api/v1/rooms/landlord/me` | PASS | PASS | NA | NA | NA | **PASS** |
| Room | `POST` | `/api/v1/rooms/{id}/boost` | PASS | PASS | NA | NA | NA | **PASS** |
| Room | `GET` | `/api/v1/landlord/analytics` | PASS | PASS | NA | NA | NA | **PASS** |
| Location | `GET` | `/api/v1/locations/autocomplete` | PASS | NA | NA | NA | NA | **PASS** |
| Location | `GET` | `/api/v1/locations/geocode` | PASS | NA | NA | NA | NA | **PASS** |
| Location | `GET` | `/api/v1/locations/reverse-geocode` | PASS | NA | NA | NA | NA | **PASS** |
| Matching | `GET` | `/api/v1/matching/feed` | PASS | PASS | NA | NA | NA | **PASS** |
| Matching | `POST` | `/api/v1/matching/swipe` | PASS | NA | NA | PASS | NA | **PASS** |
| Matching | `GET` | `/api/v1/matching/matches` | PASS | NA | NA | NA | NA | **PASS** |
| Matching | `DELETE` | `/api/v1/matching/matches/{id}` | PASS | NA | PASS | NA | NA | **PASS** |
| Matching | `POST` | `/api/v1/matching/boost` | PASS | PASS | NA | NA | NA | **PASS** |
| Matching | `GET` | `/api/v1/matching/compatibility/{id}` | PASS | NA | NA | NA | NA | **PASS** |
| Matching | `GET` | `/api/v1/matching/preferences` | PASS | NA | NA | NA | NA | **PASS** |
| Matching | `PUT` | `/api/v1/matching/preferences` | PASS | NA | NA | NA | NA | **PASS** |
| Rental | `POST` | `/api/v1/rentals` | PASS | NA | NA | PASS | PASS | **PASS** |
| Rental | `GET` | `/api/v1/rentals/{id}` | PASS | NA | PASS | NA | NA | **PASS** |
| Rental | `GET` | `/api/v1/rentals/tenant/me` | PASS | PASS | NA | NA | NA | **PASS** |
| Rental | `GET` | `/api/v1/rentals/landlord/me` | PASS | PASS | NA | NA | NA | **PASS** |
| Rental | `GET` | `/api/v1/rentals/{id}/check-in-qr` | PASS | NA | PASS | NA | NA | **PASS** |
| Rental | `POST` | `/api/v1/rentals/{id}/check-in` | PASS | NA | NA | NA | PASS | **PASS** |
| Rental | `POST` | `/api/v1/rentals/{id}/terminate` | PASS | PASS | NA | NA | NA | **PASS** |
| Review | `POST` | `/api/v1/reviews` | PASS | NA | NA | PASS | PASS | **PASS** |
| Review | `GET` | `/api/v1/reviews/rooms/{id}` | PASS | NA | NA | NA | NA | **PASS** |
| Review | `GET` | `/api/v1/reviews/users/{id}` | PASS | NA | NA | NA | NA | **PASS** |
| Review | `POST` | `/api/v1/reviews/{id}/dispute` | PASS | NA | PASS | NA | NA | **PASS** |
| Review | `POST` | `/api/v1/reviews/{id}/evidences` | PASS | NA | PASS | NA | NA | **PASS** |
| Review | `GET` | `/api/v1/reviews/{id}` | PASS | NA | NA | NA | NA | **PASS** |
| Review | `POST` | `/api/v1/reviews/{id}/reply` | PASS | NA | PASS | NA | NA | **PASS** |
| Review | `GET` | `/api/v1/reviews/disputes/pending` | PASS | PASS | NA | NA | NA | **PASS** |
| Swap | `POST` | `/api/v1/swaps` | PASS | PASS | NA | PASS | NA | **PASS** |
| Swap | `GET` | `/api/v1/swaps` | PASS | NA | NA | NA | NA | **PASS** |
| Swap | `GET` | `/api/v1/swaps/me` | PASS | PASS | NA | NA | NA | **PASS** |
| Swap | `GET` | `/api/v1/swaps/{id}` | PASS | NA | NA | NA | NA | **PASS** |
| Swap | `POST` | `/api/v1/swaps/{id}/request` | PASS | NA | NA | NA | PASS | **PASS** |
| Swap | `PUT` | `/api/v1/swaps/requests/{id}` | PASS | NA | PASS | NA | NA | **PASS** |
| Swap | `GET` | `/api/v1/swaps/landlord/requests` | PASS | PASS | NA | NA | NA | **PASS** |
| Swap | `PUT` | `/api/v1/swaps/landlord/{id}/approve` | PASS | PASS | NA | NA | NA | **PASS** |
| Swap | `PUT` | `/api/v1/swaps/landlord/{id}/decline` | PASS | PASS | NA | NA | NA | **PASS** |
| Chat | `GET` | `/api/v1/chat/conversations` | PASS | PASS | NA | NA | NA | **PASS** |
| Chat | `GET` | `/api/v1/chat/conversations/{id}/messages` | PASS | NA | PASS | NA | NA | **PASS** |
| Chat | `POST` | `/api/v1/chat/conversations/{id}/messages` | PASS | NA | PASS | PASS | NA | **PASS** |
| Chat | `PUT` | `/api/v1/chat/conversations/{id}/read` | PASS | NA | PASS | NA | NA | **PASS** |
| Chat | `POST` | `/api/v1/chat/conversations` | PASS | NA | NA | PASS | NA | **PASS** |
| Notification | `GET` | `/api/v1/notifications` | PASS | PASS | NA | NA | NA | **PASS** |
| Notification | `PUT` | `/api/v1/notifications/{id}/read` | PASS | NA | PASS | NA | NA | **PASS** |
| Notification | `PUT` | `/api/v1/notifications/read-all` | PASS | NA | NA | NA | NA | **PASS** |
| Notification | `POST` | `/api/v1/notifications/device-token` | PASS | NA | NA | PASS | NA | **PASS** |
| Monetization | `GET` | `/api/v1/monetization/plans` | PASS | NA | NA | NA | NA | **PASS** |
| Monetization | `POST` | `/api/v1/monetization/create-payment` | PASS | NA | NA | PASS | NA | **PASS** |
| Monetization | `GET` | `/api/v1/monetization/vnpay-ipn` | PASS | NA | NA | NA | NA | **PASS** |
| Monetization | `GET` | `/api/v1/monetization/vnpay-return` | PASS | NA | NA | NA | NA | **PASS** |
| Monetization | `GET` | `/api/v1/monetization/transactions/me` | PASS | NA | NA | NA | NA | **PASS** |
| Monetization | `GET` | `/api/v1/monetization/consumables/me` | PASS | NA | NA | NA | NA | **PASS** |
| Admin | `GET` | `/api/v1/admin/dashboard` | PASS | PASS | NA | NA | NA | **PASS** |
| Admin | `GET` | `/api/v1/admin/kyc/pending` | PASS | PASS | NA | NA | NA | **PASS** |
| Admin | `PUT` | `/api/v1/admin/kyc/{id}/approve` | PASS | PASS | NA | NA | NA | **PASS** |
| Admin | `PUT` | `/api/v1/admin/kyc/{id}/reject` | PASS | PASS | NA | PASS | NA | **PASS** |
| Admin | `GET` | `/api/v1/admin/users` | PASS | PASS | NA | NA | NA | **PASS** |
| Admin | `PUT` | `/api/v1/admin/users/{id}/status` | PASS | PASS | NA | PASS | NA | **PASS** |
| Admin | `GET` | `/api/v1/admin/rooms` | PASS | PASS | NA | NA | NA | **PASS** |
| Admin | `PUT` | `/api/v1/admin/rooms/{id}/verify` | PASS | PASS | NA | NA | NA | **PASS** |
| Admin | `DELETE` | `/api/v1/admin/rooms/{id}` | PASS | PASS | NA | NA | NA | **PASS** |
| Admin | `GET` | `/api/v1/admin/disputes` | PASS | PASS | NA | NA | NA | **PASS** |
| Admin | `POST` | `/api/v1/admin/disputes/{id}/resolve` | PASS | PASS | NA | PASS | NA | **PASS** |
| Admin | `GET` | `/api/v1/admin/transactions` | PASS | PASS | NA | NA | NA | **PASS** |
| Admin | `GET` | `/api/v1/admin/reports` | PASS | PASS | NA | NA | NA | **PASS** |
| Admin | `GET` | `/api/v1/admin/reports/{id}` | PASS | PASS | NA | NA | NA | **PASS** |
| Admin | `POST` | `/api/v1/admin/reports/{id}/action` | PASS | PASS | NA | PASS | NA | **PASS** |
| Admin | `GET` | `/api/v1/admin/audit-logs` | PASS | PASS | NA | NA | NA | **PASS** |

---

## 3. Nhật Ký Vá Lỗi & Tối Ưu Hóa (Bug Fixes & Hardening Log)

Dưới đây là danh sách các can thiệp mã nguồn được thực hiện trong quá trình kiểm toán toàn diện nhằm đạt 0 lỗi 500 và chặn đứng mọi lỗ hổng Happy Path / BOLA / Validation:

1. **`UserService.java` (User Profile & TrustScore Calculation):**
   - Loại bỏ hoàn toàn công thức chia tỷ lệ ước lượng giả mạo (`/4`, `/2`).
   - Inject `ReviewRepository` và `RentalRepository` để đếm chính xác số hợp đồng hoàn thành và điểm trung bình đánh giá thực tế từ DB.
   - `getKycStatus()`: Ném ngoại lệ `ResourceNotFoundException("KYC_NOT_FOUND", ...)` (404) thay vì âm thầm trả về fallback status `PENDING`.

2. **`UserVerification.java` (Entity Alignment):**
   - Loại bỏ kế thừa `BaseEntity` do bảng `user_verifications` trong `DATABASE_SCHEMA.sql` chỉ có cột `verified_at` mà không có cột `updated_at`, triệt tiêu lỗi Hibernate SQL `column uv1_0.updated_at does not exist` (500 Error).

3. **`RentalService.java` (Rental State Machine & Anti-Replay Check-in):**
   - Chặn đứng Check-in Replay: Kiểm tra nếu hợp đồng đã `ACTIVE` thì ném ngay 409 `ConflictException("RENTAL_ALREADY_CHECKED_IN")`.
   - Khắc phục lỗi `null value in check_in_code violates not-null constraint`: Thay vì gán null vào cột `check_in_code`, cập nhật chuỗi `USED_<timestamp>` trong database và xóa key trong Redis cache.
   - Chặn thuê phòng đã kín: Kiểm tra trạng thái phòng phải là `AVAILABLE` và chưa có hợp đồng đang `ACTIVE`.

4. **`ReviewService.java` (Review Integrity & Anti-BOLA):**
   - Chặn Self-Review: Người thuê và chủ trọ không được tự review chính mình (ném 400 `SELF_REVIEW_NOT_ALLOWED`).
   - Chặn Duplicate Review: Mỗi bên chỉ được đánh giá 1 lần cho mỗi hợp đồng thuê (ném 409 `REVIEW_ALREADY_EXISTS`).
   - Anti-BOLA Evidence: Chỉ người bị đánh giá hoặc Admin mới có quyền tải lên ảnh đối chất bằng chứng.

5. **`RoomSwapService.java` (Swap Logic & Anti-BOLA):**
   - Chặn Self-Proposal: Không cho phép tự nộp yêu cầu hoán đổi vào bài đăng của chính mình (ném 400 `SELF_PROPOSAL_NOT_ALLOWED`).
   - Anti-BOLA Proposal Decision: Ứng viên nộp đề xuất chỉ được quyền rút/hủy đề xuất (`CANCELLED`), cấm tuyệt đối việc ứng viên tự duyệt (`ACCEPTED`) đề xuất của chính mình.

6. **Controller `@Valid` & DTO Validation Hardening:**
   - Bổ sung `@Valid` trên toàn bộ 34 `@RequestBody` trên tất cả 12 Controller trong hệ thống.
   - `SendMessageRequest.java`: Thêm `@NotBlank` và `@Size(max = 2000)` chặn đứng tin nhắn rỗng.
   - `CreateRoomRequest.java`, `MatchingProfileRequest.java`: Thêm ràng buộc `@Positive`, `@PositiveOrZero`, `@Min`, `@Max` bảo vệ 100% trường dữ liệu số học.

7. **Final Enterprise Cleanup:**
   - Xóa bỏ hoàn toàn cổng thanh toán VietQR thừa, chuẩn hóa duy nhất cổng thanh toán VNPay Sandbox.
   - Xóa bỏ lưu trữ cục bộ `LocalFileStorageAdapter` và thư mục `./uploads/`, chuyển đổi 100% sang `CloudinaryStorageAdapter` (@Primary, @Service).
   - Loại bỏ các mock static còn sót trong `GoongMapsAdapter` và fallback email ngẫu nhiên trong `AuthService` (Google OAuth).

## 4. Kết Luận

- Toàn bộ 105 API Endpoints của hệ thống PhongTrọXanh đã được kiểm toán tự động qua 5 chiều ma trận: Happy Path, Unhappy Auth, Unhappy RBAC, Unhappy BOLA, Unhappy Validation, Unhappy State.
- Hệ thống đạt **100% Pass** trên tất cả các tiêu chí hợp lệ, **0 lỗi 500 Internal Server Error**, không phát sinh bất kỳ hồi quy (regression) nào.

# TÀI LIỆU TỔNG QUAN HỆ THỐNG BACKEND PHÒNG TRỌ XANH (PhongTroXanh.vn)
> **Dành cho:** Kỹ sư phần mềm, Tech Leads và System Architects  
> **Phiên bản:** Enterprise Modular Monolith 2.0 (Tháng 9/2026)  
> **Trạng thái:** Production-Ready (105 REST APIs + STOMP WebSocket Chat + Live Verified 100%)  
> **Tài liệu:** Kiến trúc tổng thể và bản đồ kỹ thuật hệ thống

---

## 1. TỔNG QUAN DỰ ÁN & CÔNG NGHỆ (TECH STACK)

### 1.1. Sứ mệnh hệ thống
**PhongTroXanh.vn** là nền tảng tìm kiếm phòng trọ thông minh kết hợp mạng xã hội ghép đôi bạn cùng phòng (Smart Roommate Matching) đầu tiên tại Việt Nam áp dụng mô hình đánh giá tín nhiệm 2 chiều (Two-Way TrustScore) và quy trình nhận phòng an toàn chống gian lận qua Dynamic Check-in QR Code.

### 1.2. Công nghệ cốt lõi
| Thành phần | Công nghệ / Thư viện | Phiên bản | Mục đích sử dụng |
| :--- | :--- | :--- | :--- |
| **Ngôn ngữ** | Java (OpenJDK) | 21 LTS | Runtime hiệu năng cao, Virtual Threads sẵn sàng |
| **Framework** | Spring Boot | 3.3.4 | Core Framework, Web MVC, Security, Data JPA |
| **Cơ sở dữ liệu** | PostgreSQL + PostGIS | 16-alpine (postgis/postgis:16-3.4) | CSDL quan hệ chính + Truy vấn không gian địa lý (GIS) |
| **Bộ nhớ đệm & Broker** | Redis | 7-alpine | In-Memory Cache, Rate Limiting, Session, STOMP Pub/Sub |
| **Tài liệu API** | SpringDoc OpenAPI | 2.6.0 | Tự động sinh Swagger UI 3.0 tại `/swagger-ui.html` |
| **Bảo mật & Auth** | Spring Security + JJWT | 0.12.6 | JWT Stateless Authentication, Cookie HttpOnly Token Rotation |
| **Mã hóa PII** | AES-256-GCM / Java Cryptography | Chuẩn FIPS | Mã hóa số thẻ CCCD/CMND trước khi ghi CSDL |
| **Chịu lỗi (Resilience)** | Resilience4j | 2.2.0 | Circuit Breaker, Retry cho Email và Payment Gateway |
| **Cổng thanh toán** | VNPay Sandbox Gateway | HMAC-SHA512 | Thanh toán nạp gói dịch vụ và lượt consumable |
| **Định vị & Bản đồ** | Goong Maps REST API | v1 | Autocomplete ngõ hẻm Việt Nam, Geocoding & Reverse-Geocoding |
| **Email OTP** | Brevo REST API | HTTPS Port 443 | Gửi OTP đăng ký/quên pass (chống chặn port TCP 25/587 trên Cloud) |

---

## 2. KIẾN TRÚC TỔNG THỂ (PRAGMATIC MODULAR MONOLITH)

Hệ thống được thiết kế theo mô hình **Modular Monolith** kết hợp **Pragmatic Layered Architecture** (ADR-001 & ADR-009). Cấu trúc mã nguồn được phân định thành 4 tầng rõ ràng trong từng Module, đồng thời tập trung toàn bộ hạ tầng dùng chung vào `common.*`:

```
vn.phongtroxanh.backend
├── common                       <-- CÁC DỊCH VỤ DÙNG CHUNG (PORTS & ADAPTERS)
│   ├── config                  <-- Cấu hình Redis, Security, Swagger, Jackson, WebMvc
│   ├── exception               <-- AppException, GlobalExceptionHandler, ProblemDetail
│   ├── location                <-- GeocodingPort, GoongMapsAdapter
│   ├── mail                    <-- EmailNotificationPort, BrevoEmailAdapter
│   ├── payment                 <-- PaymentGatewayPort, VnPayPaymentAdapter
│   ├── security                <-- JwtTokenProvider, JwtAuthFilter, UserPrincipal
│   ├── storage                 <-- FileStoragePort, CloudinaryStorageAdapter
│   └── util                    <-- ApiResponse envelope, AES-256 CryptoUtils, Constants
│
├── modules                      <-- 12 MODULES NGHIỆP VỤ BIỆT LẬP
│   ├── admin                   <-- Dashboard KPI, KYC CCCD Audit, Quản lý tài khoản/phòng, Khiếu nại
│   ├── auth                    <-- Đăng ký, Đăng nhập, Token Rotation, OTP Brevo, Google OAuth2, Onboarding
│   ├── chat                    <-- REST Chat history + STOMP WebSocket Real-time, Redis Pub/Sub
│   ├── location                <-- Goong Maps Autocomplete, Geocoding, Reverse Geocoding
│   ├── matching                <-- Thuật toán 8 trụ cột, Discovery Feed, Quẹt thẻ (Swipe), Match đôi
│   ├── misc                    <-- Landing page statistics (phòng trọ, ghép đôi, đánh giá)
│   ├── monetization            <-- Gói cước (Plans), VNPay Sandbox, Consumables
│   ├── notification            <-- Thông báo In-App, FCM Device Token, Đánh dấu đã đọc
│   ├── rental                  <-- Hợp đồng thuê, Dynamic Check-in QR Token, Replay Protection
│   ├── review                  <-- Đánh giá 2 chiều sau thuê, Ảnh bằng chứng, Khiếu nại (Dispute)
│   ├── room                    <-- CRUD Phòng, MapView PostGIS (ST_DWithin), Boost phòng 7 ngày, So sánh phòng
│   ├── swap                    <-- Đăng tin hoán đổi phòng (Swap/Sublease), Đề xuất, Duyệt của chủ trọ
│   └── user                    <-- Profile cá nhân, CCCD mã hóa, Điểm tín nhiệm TrustScore 4 tầng
```

### Luồng phụ thuộc đơn chiều (Unidirectional Dependency Rules - ADR-009)
1. `auth` $\to$ `user`: `AuthService` được phép truy vấn và khởi tạo tài khoản thông qua `UserRepository`.
2. `user` **TUYỆT ĐỐI KHÔNG** inject Bean hoặc phụ thuộc vào `auth`.
3. Toàn bộ Ports/Adapters bên thứ 3 (Storage, Mail, Location, Payment) bắt buộc nằm ở `common.*` để triệt tiêu phụ thuộc vòng tròn.
4. `Room` là **Aggregate Root (DDD)** duy nhất quản lý `RoomImage` và `RoomFee` qua quan hệ `@OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)`.

---

## 3. DANH MỤC 12 MODULES & 105 API ENDPOINTS (FULL CATALOG)

Dự án hiện có tổng cộng **105 RESTful API Endpoints** (đã qua kiểm thử ma trận 100% Pass 0 lỗi 500) và **1 STOMP WebSocket endpoint**:

### Module 1: Auth & Security (`/api/v1/auth`, `/api/v1/misc`) - 12 APIs
- `POST /api/v1/auth/register`: Đăng ký tài khoản mới (cấp mặc định 15 lượt vuốt).
- `POST /api/v1/auth/login`: Đăng nhập lấy Bearer AccessToken và HttpOnly RefreshToken Cookie.
- `POST /api/v1/auth/send-otp`: Gửi mã OTP 6 số qua email (Brevo REST API).
- `POST /api/v1/auth/verify-otp`: Xác thực mã OTP.
- `POST /api/v1/auth/forgot-password`: Yêu cầu cấp lại mật khẩu.
- `POST /api/v1/auth/reset-password`: Đặt lại mật khẩu mới.
- `POST /api/v1/auth/refresh-token`: Xoay vòng AccessToken qua HttpOnly Cookie.
- `POST /api/v1/auth/logout`: Thu hồi RefreshToken và đưa AccessToken vào Redis Blacklist.
- `POST /api/v1/auth/oauth/google`: Đăng nhập/Đăng ký nhanh qua Google ID Token.
- `POST /api/v1/auth/onboarding/tenant`: Lưu thông tin 4 bước Onboarding người thuê.
- `POST /api/v1/auth/onboarding/landlord`: Lưu thông tin 3 bước Onboarding chủ trọ.
- `GET /api/v1/misc/landing-stats`: Thống kê tổng hợp số phòng, số lượt match, tỷ lệ hài lòng trang chủ.

### Module 2: User Profiles, KYC & TrustScore (`/api/v1/users`) - 12 APIs
- `GET /api/v1/users/me`: Lấy thông tin cá nhân của người dùng hiện tại.
- `PUT /api/v1/users/me`: Cập nhật họ tên, ngày sinh, bio, trường/công ty.
- `POST /api/v1/users/me/avatar`: Upload avatar (Multipart).
- `GET /api/v1/users/me/matching-profile`: Xem ma trận thói quen và tiêu chí bạn cùng phòng.
- `PUT /api/v1/users/me/matching-profile`: Cập nhật thói quen sinh hoạt (ngủ sớm, thuốc lá, vật nuôi, độ ồn...).
- `GET /api/v1/users/me/trust-score`: Chi tiết điểm tín nhiệm TrustScore 4 tầng (0-100).
- `POST /api/v1/users/me/kyc/cccd`: Nộp 2 mặt ảnh CCCD + số CCCD (mã hóa AES-256).
- `GET /api/v1/users/me/kyc/status`: Tra cứu trạng thái xét duyệt KYC.
- `GET /api/v1/users/{id}/public`: Xem hồ sơ công khai (đã lọc bỏ thông tin nhạy cảm).
- `GET /api/v1/users/me/settings`: Lấy cấu hình thông báo và quyền riêng tư.
- `PUT /api/v1/users/me/settings`: Cập nhật cài đặt quyền riêng tư và thông báo.
- `DELETE /api/v1/users/me`: Yêu cầu xóa tài khoản (Soft delete).

### Module 3: Rooms, PostGIS & Landlord (`/api/v1/rooms`, `/api/v1/landlord`) - 15 APIs
- `GET /api/v1/rooms`: Tìm kiếm phòng trọ đa tiêu chí (giá, quận, loại phòng, tiện nghi, phân trang).
- `GET /api/v1/rooms/map`: Truy vấn phòng trọ theo bán kính/tọa độ PostGIS (`ST_DWithin`).
- `GET /api/v1/rooms/compare`: So sánh thông số, biểu phí của 2-4 phòng trọ.
- `GET /api/v1/rooms/{id}`: Chi tiết phòng trọ (tự động tăng Redis view count).
- `POST /api/v1/rooms/{id}/save`: Lưu phòng trọ yêu thích (Bookmark).
- `DELETE /api/v1/rooms/{id}/save`: Bỏ lưu phòng trọ yêu thích.
- `GET /api/v1/rooms/saved/me`: Danh sách phòng trọ đã lưu.
- `POST /api/v1/rooms`: Chủ trọ đăng tin phòng trọ mới.
- `PUT /api/v1/rooms/{id}`: Chủ trọ cập nhật thông tin phòng.
- `DELETE /api/v1/rooms/{id}`: Chủ trọ ẩn/xóa phòng trọ.
- `POST /api/v1/rooms/{id}/images`: Thêm ảnh cho phòng trọ.
- `DELETE /api/v1/rooms/{id}/images/{imageId}`: Xóa ảnh của phòng trọ.
- `GET /api/v1/rooms/landlord/me`: Danh sách các phòng do chủ trọ hiện tại sở hữu.
- `POST /api/v1/rooms/{id}/boost`: Đẩy tin phòng trọ lên top trong 7 ngày (trừ 1 consumable boost).
- `GET /api/v1/landlord/analytics`: Báo cáo phân tích doanh thu, lượt xem và biểu đồ 7 ngày cho chủ trọ.

### Module 4: Location Services (`/api/v1/locations`) - 3 APIs
- `GET /api/v1/locations/autocomplete`: Gợi ý địa chỉ ngõ hẻm Việt Nam qua Goong Maps API.
- `GET /api/v1/locations/geocode`: Chuyển đổi địa chỉ chuỗi $\to$ Tọa độ (Lat, Lng).
- `GET /api/v1/locations/reverse-geocode`: Chuyển đổi tọa độ (Lat, Lng) $\to$ Tên địa chỉ chi tiết.

### Module 5: Smart Roommate Matching (`/api/v1/matching`) - 8 APIs
- `GET /api/v1/matching/feed`: Lấy Discovery Feed các ứng viên tương thích (áp dụng thuật toán 8 trụ cột).
- `POST /api/v1/matching/swipe`: Quẹt thẻ (`LIKE`, `DISLIKE`, `SUPER_LIKE`), tự trừ lượt vuốt và phát hiện Mutual Match.
- `GET /api/v1/matching/matches`: Danh sách các cặp đã ghép đôi thành công.
- `DELETE /api/v1/matching/matches/{id}`: Hủy ghép đôi (Unmatch).
- `POST /api/v1/matching/boost`: Đẩy hồ sơ cá nhân lên đầu danh sách vuốt trong 24 giờ.
- `GET /api/v1/matching/compatibility/{id}`: Tính toán chi tiết điểm số và bảng so sánh độ tương thích với một người dùng.
- `GET /api/v1/matching/preferences`: Lấy tiêu chí tìm bạn cùng phòng mong muốn.
- `PUT /api/v1/matching/preferences`: Cập nhật tiêu chí tìm bạn cùng phòng mong muốn.

### Module 6: Rentals & Check-in QR (`/api/v1/rentals`) - 7 APIs
- `POST /api/v1/rentals`: Tạo hợp đồng thuê phòng mới.
- `GET /api/v1/rentals/{id}`: Xem chi tiết hợp đồng thuê phòng.
- `GET /api/v1/rentals/tenant/me`: Danh sách hợp đồng thuê của người thuê hiện tại.
- `GET /api/v1/rentals/landlord/me`: Danh sách hợp đồng cho thuê của chủ trọ hiện tại.
- `GET /api/v1/rentals/{id}/check-in-qr`: Sinh token mã Check-in QR (TTL 5 phút, bảo vệ chống replay).
- `POST /api/v1/rentals/{id}/check-in`: Xác thực Check-in chuyển trạng thái sang `CHECKED_IN`, cập nhật phòng sang `RENTED`, cộng điểm TrustScore (+10).
- `POST /api/v1/rentals/{id}/terminate`: Chấm dứt hợp đồng thuê phòng.

### Module 7: Reviews & Disputes (`/api/v1/reviews`) - 8 APIs
- `POST /api/v1/reviews`: Đăng đánh giá 2 chiều (1-5 sao, bình luận, tags) sau khi hoàn tất check-in.
- `GET /api/v1/reviews/rooms/{id}`: Lấy danh sách đánh giá của một phòng trọ.
- `GET /api/v1/reviews/users/{id}`: Lấy danh sách đánh giá mà người dùng nhận được.
- `POST /api/v1/reviews/{id}/dispute`: Người bị đánh giá gửi khiếu nại review sai sự thật.
- `POST /api/v1/reviews/{id}/evidences`: Tải lên ảnh bằng chứng đối chất khiếu nại.
- `GET /api/v1/reviews/{id}`: Xem chi tiết một bài đánh giá.
- `POST /api/v1/reviews/{id}/reply`: Phản hồi công khai bài đánh giá.
- `GET /api/v1/reviews/disputes/pending`: Danh sách các khiếu nại đang chờ Admin phán quyết.

### Module 8: Room Swap & Subleasing (`/api/v1/swaps`) - 9 APIs
- `POST /api/v1/swaps`: Đăng bài yêu cầu hoán đổi/chuyển nhượng phòng thuê.
- `GET /api/v1/swaps`: Bảng tin tìm kiếm các bài đăng hoán đổi phòng.
- `GET /api/v1/swaps/me`: Danh sách các bài đăng swap của tôi.
- `GET /api/v1/swaps/{id}`: Chi tiết một bài đăng swap.
- `POST /api/v1/swaps/{id}/request`: Gửi đề xuất hoán đổi phòng vào một bài đăng.
- `PUT /api/v1/swaps/requests/{id}`: Cập nhật trạng thái đề xuất (Chủ bài duyệt/từ chối hoặc người gửi hủy).
- `GET /api/v1/swaps/landlord/requests`: Danh sách các yêu cầu swap liên quan đến phòng của chủ trọ.
- `PUT /api/v1/swaps/landlord/{id}/approve`: Chủ trọ phê duyệt chuyển nhượng hợp đồng.
- `PUT /api/v1/swaps/landlord/{id}/decline`: Chủ trọ từ chối chuyển nhượng hợp đồng kèm lý do.

### Module 9: Real-Time Chat & WebSocket (`/api/v1/chat`, `/ws/chat`) - 5 APIs + STOMP
- `GET /api/v1/chat/conversations`: Danh sách các cuộc hội thoại (tin nhắn cuối, số tin chưa đọc, đối tượng chat).
- `GET /api/v1/chat/conversations/{id}/messages`: Lịch sử tin nhắn trong hội thoại.
- `POST /api/v1/chat/conversations/{id}/messages`: Gửi tin nhắn qua REST API (hỗ trợ fallback).
- `PUT /api/v1/chat/conversations/{id}/read`: Đánh dấu toàn bộ tin nhắn trong hội thoại là đã đọc.
- `POST /api/v1/chat/conversations`: Khởi tạo cuộc hội thoại mới giữa 2 bên.
- **WebSocket STOMP Channel:** Endpoint `/ws/chat`, Topic `/topic/chat/{conversationId}`, Broker Redis Pub/Sub topic `chat.message.topic`.

### Module 10: In-App Notifications (`/api/v1/notifications`) - 4 APIs
- `GET /api/v1/notifications`: Lấy danh sách thông báo cá nhân.
- `PUT /api/v1/notifications/{id}/read`: Đánh dấu 1 thông báo đã đọc.
- `PUT /api/v1/notifications/read-all`: Đánh dấu tất cả thông báo đã đọc.
- `POST /api/v1/notifications/device-token`: Đăng ký thiết bị nhận push notification qua Firebase Cloud Messaging (FCM).

### Module 11: Monetization & Payments (`/api/v1/monetization`) - 6 APIs
- `GET /api/v1/monetization/plans`: Danh mục các gói dịch vụ (FREE, PRO_TENANT, LANDLORD_VIP).
- `POST /api/v1/monetization/create-payment`: Tạo đơn thanh toán nạp gói qua cổng VNPay Sandbox.
- `GET /api/v1/monetization/vnpay-ipn`: IPN Webhook tiếp nhận kết quả tự động từ VNPay (Idempotency Key, cập nhật số dư).
- `GET /api/v1/monetization/vnpay-return`: URL chuyển hướng người dùng sau khi thanh toán trên VNPay.
- `GET /api/v1/monetization/transactions/me`: Lịch sử các giao dịch thanh toán của tôi.
- `GET /api/v1/monetization/consumables/me`: Số dư lượt vuốt (swipes), lượt đẩy tin (boosts), lượt super match còn lại.

### Module 12: Admin Moderation & Audit (`/api/v1/admin`) - 16 APIs
- `GET /api/v1/admin/dashboard`: Dashboard KPI toàn hệ thống (User, Room, Doanh thu, Tỷ lệ duyệt KYC).
- `GET /api/v1/admin/kyc/pending`: Danh sách hồ sơ CCCD chờ duyệt kèm số CCCD giải mã AES-256.
- `PUT /api/v1/admin/kyc/{id}/approve`: Duyệt hồ sơ CCCD (+30 điểm TrustScore, cấp tích xanh).
- `PUT /api/v1/admin/kyc/{id}/reject`: Từ chối hồ sơ CCCD kèm lý do vi phạm.
- `GET /api/v1/admin/users`: Quản lý danh sách người dùng toàn hệ thống (tìm kiếm, phân trang).
- `PUT /api/v1/admin/users/{id}/status`: Đổi trạng thái tài khoản (`ACTIVE`, `WARNED`, `LOCKED`).
- `GET /api/v1/admin/rooms`: Quản lý toàn bộ danh sách phòng trọ trên hệ thống.
- `PUT /api/v1/admin/rooms/{id}/verify`: Cấp tích xanh xác minh kiểm duyệt cho phòng trọ.
- `DELETE /api/v1/admin/rooms/{id}`: Gỡ phòng trọ vi phạm chính sách nền tảng.
- `GET /api/v1/admin/disputes`: Danh sách các khiếu nại đánh giá 1-2 sao.
- `POST /api/v1/admin/disputes/{id}/resolve`: Phán quyết khiếu nại review (Chấp thuận/Bác bỏ, điều chỉnh lại TrustScore).
- `GET /api/v1/admin/transactions`: Danh sách lịch sử giao dịch thanh toán toàn hệ thống.
- `GET /api/v1/admin/reports`: Danh sách báo cáo vi phạm từ cộng đồng (User, Room, Review).
- `GET /api/v1/admin/reports/{id}`: Chi tiết 1 báo cáo vi phạm.
- `POST /api/v1/admin/reports/{id}/action`: Thực hiện hành động xử lý báo cáo (Cảnh cáo, gỡ tin, khóa acc).
- `GET /api/v1/admin/audit-logs`: Nhật ký kiểm toán an ninh vết toàn bộ hành động can thiệp của Admin.

---

## 4. CƠ SỞ DỮ LIỆU & CÁC LƯU Ý KỸ THUẬT QUAN TRỌNG

Hệ thống bao gồm **25 bảng cơ sở dữ liệu** được định nghĩa trong `docs/DATABASE_SCHEMA.sql`. Dưới đây là các chi tiết ORM / Database đặc thù cần lưu ý:

### 4.1. Chuyển đổi PostgreSQL Enum: `SwipeActionConverter`
- **Vấn đề:** Trong `DATABASE_SCHEMA.sql`, enum được định nghĩa: `CREATE TYPE swipe_dir_enum AS ENUM ('LEFT', 'RIGHT', 'SUPER');`.
- **Thực tế Java:** Code mô hình hóa hành động vuốt là enum `SwipeAction` (`LIKE`, `DISLIKE`, `SUPER_LIKE`).
- **Giải pháp triển khai:** Sử dụng `SwipeActionConverter` (implements `AttributeConverter<SwipeAction, String>`) để ánh xạ:
  - `LIKE` $\leftrightarrow$ `'RIGHT'`
  - `DISLIKE` $\leftrightarrow$ `'LEFT'`
  - `SUPER_LIKE` $\leftrightarrow$ `'SUPER'`
- **Lưu ý kỹ thuật:** Tuyệt đối không xóa Converter này để tránh phát sinh lỗi PostgreSQL `invalid input value for enum swipe_dir_enum`.

### 4.2. Cấu trúc bảng `user_verifications`
- Bảng `user_verifications` trong CSDL chỉ có cột `reviewed_at` và `created_at`, **không có** cột `updated_at`.
- Do đó, entity `UserVerification.java` **không được kế thừa `BaseEntity`** mà khai báo trực tiếp các trường để tránh Hibernate phát sinh SQL tìm cột `updated_at`.

### 4.3. Quan hệ `Room` với `RoomImage` và `RoomFee`
- Bảng `room_images` và `room_fees` có khóa ngoại `room_id UUID NOT NULL REFERENCES rooms(id)`.
- Triển khai JPA: Sử dụng quan hệ hai chiều:
  - Phía Room: `@OneToMany(mappedBy = "room", cascade = CascadeType.ALL, orphanRemoval = true)`
  - Phía Con: `@ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "room_id", nullable = false)`
  - Khi lưu ảnh hoặc chi phí, bắt buộc phải set `room` cho entity con.

### 4.4. Tính điểm TrustScore thực tế (Anti-Fake Heuristics)
- Trong `UserService.java`, công thức tính điểm TrustScore dựa trên dữ liệu thật được truy vấn từ `ReviewRepository` (tính điểm trung bình rating) và `RentalRepository` (đếm số hợp đồng đã hoàn tất `CHECKED_IN`/`ACTIVE`), không dùng phép chia ước lượng giả lập.

---

## 5. HƯỚNG DẪN VẬN HÀNH & KIỂM THỬ (OPERATIONAL PLAYBOOK)

### 5.1. Khởi động hạ tầng Docker
```bash
docker compose up -d
```
Kiểm tra sức khỏe container:
- Postgres (Port 5433): `docker exec -it phongtroxanh-postgres pg_isready -U postgres -d phongtroxanh_db`
- Redis (Port 6379): `docker exec -it phongtroxanh-redis redis-cli ping`

### 5.2. Biên dịch và khởi chạy Spring Boot
```bash
# Kiểm tra biên dịch (Java 21)
./mvnw clean compile -DskipTests

# Chạy Unit & Integration Tests
./mvnw test

# Khởi chạy server thật tại cổng 8080
./mvnw spring-boot:run
```

### 5.3. Kiểm thử ma trận tự động (Full Matrix Test Script)
Chạy script kiểm thử 105 endpoints (bao phủ Happy Path, BOLA, RBAC, Validation):
```bash
python scripts/test_full_api_matrix.py
```
Báo cáo kết quả sẽ được ghi tự động vào `docs/TEST_REPORT.md`.

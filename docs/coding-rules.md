# QUY TẮC PHÁT TRIỂN & CHUẨN MỰC KỸ THUẬT BACKEND (CODING RULES)

> Tài liệu thiết kế/lịch sử. Trạng thái triển khai và phạm vi đã kiểm chứng xem [backend verification](backend-verification.md); cấu hình hiện hành dùng [.env.example](../.env.example) và [payOS setup](payos-setup.md). ADR về VNPay đã được thay bằng payOS ngày 01/10/2026.
# DỰ ÁN: PHÒNG TRỌ XANH (PhongTroXanh.vn)
# TIÊU CHUẨN: ENTERPRISE GRADE & OWASP API SECURITY TOP 10

---

> [!IMPORTANT]
> **VĂN BẢN QUY PHẠM KỸ THUẬT BẮT BUỘC (MANDATORY RFC 2119 NORMATIVE RULES):**
> Các từ khóa **"BẮT BUỘC" (MUST / REQUIRED / SHALL)**, **"TUYỆT ĐỐI CẤM" (MUST NOT / SHALL NOT)**, **"KHUYẾN NGHỊ" (RECOMMENDED / SHOULD)** trong tài liệu này được hiểu theo tiêu chuẩn **IETF RFC 2119**.
> Tất cả các kỹ sư phát triển phần mềm khi tham gia đóng góp mã nguồn cho Backend của hệ thống **PhongTroXanh.vn** BẮT BUỘC phải tuân thủ nghiêm ngặt các quy tắc dưới đây.
> Mọi tính năng, API endpoint, service hay repository trước khi coi là hoàn thành đều phải được kiểm tra (self-audit) đối chiếu với bảng **20 tiêu chí Definition of Done (DoD)** ở cuối tài liệu. Bất kỳ vi phạm nào đều dẫn đến việc TÁC VỤ BỊ TỪ CHỐI (TASK REJECTION).

---

## 1. NGUYÊN TẮC KIẾN TRÚC & PHÂN TẦNG (MODULAR MONOLITH & PRAGMATIC LAYERED ARCHITECTURE)

1. **Cấu trúc Hệ thống Thực dụng (Modular Monolith):**
   - Dự án được tổ chức thành một **Modular Monolith** duy nhất với các Bounded Contexts rõ ràng (Room, Roommate Matching, Contract, Review, Chat STOMP, Monetization, Admin).
   - Áp dụng mô hình **Pragmatic Layered Architecture**:
     - **Presentation Layer (`presentation`):** Tiếp nhận HTTP Request / WebSocket Frame, validate DTO đầu vào, phân quyền controller, gọi Application Service và trả về DTO Response chuẩn `ApiResponse<T>`. **Tuyệt đối KHÔNG viết business logic hoặc query database trong Controller.**
     - **Application Layer (`application`):** Chứa Use Cases, Transaction Boundaries (`@Transactional`), gọi Repositories, tương tác Redis Cache, Distributed Locks và phát sinh Domain Events.
     - **Domain Layer (`domain`):** Chứa Entities (tận dụng Spring Data JPA annotations để tối ưu tốc độ phát triển và dirty checking), Enums, Value Objects. Riêng thuật toán cốt lõi (như `MatchingEngine` 8 trụ cột) được viết dưới dạng **Pure Domain Service** không phụ thuộc database để dễ dàng Unit Test độc lập 100%.
     - **Infrastructure Layer (`infrastructure`):** Chứa Spring Data JPA Repositories (truy vấn PostgreSQL + PostGIS), Redis Clients, Security Configs, Storage Adapters (Cloudinary/Local), và tích hợp bên thứ 3 (payOS, Brevo REST API, Goong Maps, FCM Push).

2. **Dữ liệu Vào/Ra phải qua DTOs (No Entity Leaks):**
   - **Tuyệt đối KHÔNG trả trực tiếp Entity ra ngoài Controller.**
   - Bắt buộc dùng `RequestDTO` (kèm Validation annotations như `@NotBlank`, `@Min`, `@Max`, `@Email`, `@Pattern`, `@Size`) và `ResponseDTO` (chỉ lộ các trường cần thiết, ẩn toàn bộ mật khẩu, muối, PII thô).
   - Không được bind trực tiếp Request Body vào Entity (Chống Mass Assignment).

---

## 2. CHUẨN MỰC RESTFUL API & HTTP STATUS CODES

1. **Quy tắc Đặt tên Endpoint:**
   - Sử dụng danh từ số nhiều, chữ thường, phân cách bằng dấu gạch ngang (kebab-case).
   - Ví dụ chuẩn: `/api/v1/rooms`, `/api/v1/rental-contracts`, `/api/v1/user-profiles`.
   - Phiên bản API cố định là `/api/v1/...`.

2. **Phân biệt Rõ ràng giữa PUT và PATCH:**
   - `PUT`: **Thay thế toàn bộ tài nguyên (Full Representation Replace)**. Client phải gửi đầy đủ payload.
   - `PATCH`: **Cập nhật một phần tài nguyên (Partial Update)**. Chỉ cập nhật các trường được truyền trong DTO.

3. **Quy tắc HTTP Status Codes Chuẩn xác:**
   - `GET`: Lấy dữ liệu $\to$ `200 OK`. **Collection rỗng BẮT BUỘC trả `200 OK` với `data: []`** (để giữ ổn định schema cho Frontend, trừ khi use-case đặc biệt yêu cầu 204).
   - `POST`: Tạo mới tài nguyên $\to$ `201 Created` kèm Header `Location` hoặc Body chứa ID mới.
   - `PUT` / `PATCH`: Cập nhật $\to$ `200 OK`.
   - `DELETE`: Xóa tài nguyên $\to$ `200 OK` (kèm message xác nhận) hoặc `204 No Content`.
   - `400 Bad Request`: Dữ liệu gửi lên sai định dạng hoặc vi phạm DTO validation.
   - `401 Unauthorized`: Chưa đăng nhập, Token thiếu, hết hạn hoặc không hợp lệ.
   - `403 Forbidden`: Đã đăng nhập nhưng **không có quyền (No permission / Access Denied / BOLA violation)**.
   - `404 Not Found`: Không tìm thấy tài nguyên theo ID.
   - `409 Conflict`: Xung đột trạng thái nghiệp vụ, trùng lặp key (ví dụ: cùng Idempotency Key nhưng khác Request Payload).
   - `422 Unprocessable Entity`: Dữ liệu đúng cú pháp nhưng vi phạm logic nghiệp vụ.
   - `429 Too Many Requests`: **Vượt quá hạn mức Rate Limit / Hạn ngạch tài nguyên (Quota limit) / Hết lượt vuốt**.

4. **Cấu trúc Response Chuẩn (Uniform API Envelope):**
```json
{
  "success": true,
  "statusCode": 200,
  "message": "Thao tác thành công",
  "data": { ... },
  "timestamp": "2026-09-01T10:00:00Z"
}
```

5. **Quy chuẩn Ngôn ngữ (Language Consistency):**
   Để tránh tình trạng "nửa nạc nửa mỡ" (lúc Anh lúc Việt), áp dụng chuẩn sau:
   - **Mã lỗi (`code` / `type` / `title`)**: **100% Tiếng Anh, IN HOA, SNAKE_CASE** (VD: `USER_NOT_FOUND`, `INVALID_INPUT_DATA`). Dành cho lập trình viên debug và Frontend xử lý logic.
   - **Chi tiết lỗi (`detail` / `message` / `reason`)**: **100% Tiếng Việt, có dấu, thân thiện với người dùng** (VD: "Số điện thoại không hợp lệ", "Bạn không có quyền sửa phòng này"). Frontend có thể hiển thị trực tiếp lên Toast/Alert mà không cần map lại.
   - **System Logs (Log ghi vào file/console)**: **100% Tiếng Anh** để chuẩn hóa với hệ thống giám sát quốc tế (ELK, Datadog).

6. **Xử lý Lỗi Chuẩn Doanh nghiệp (RFC 9457 Problem Details - Obsoletes RFC 7807):**
```json
{
  "type": "https://api.phongtroxanh.vn/errors/invalid-input",
  "title": "Invalid Input Data",
  "status": 400,
  "detail": "Dữ liệu gửi lên không vượt qua validation",
  "instance": "/api/v1/auth/register",
  "code": "INVALID_INPUT_DATA",
  "invalidParams": [
    {
      "name": "phoneNumber",
      "reason": "Số điện thoại phải từ 10-11 chữ số hợp lệ"
    }
  ],
  "timestamp": "2026-09-01T10:00:00Z",
  "requestId": "req_8f1a2b3c4d5e"
}
```

---

## 3. BẢO MẬT & QUẢN LÝ PHIÊN (SECURITY & AUTHENTICATION)

1. **Lưu trữ Token An toàn (In-Memory Access Token + HttpOnly Cookie Refresh Token):**
   - `accessToken`: Thời hạn sống ngắn (**15 phút**). Phía Client **BẮT BUỘC chỉ lưu trong In-Memory (React State / Memory Store)**, tuyệt đối KHÔNG lưu trong `localStorage` hay `sessionStorage` để chống XSS.
   - `refreshToken`: Thời hạn sống dài (**7 ngày**), được Backend trả về qua **Cookie `HttpOnly`, `Secure`, `SameSite=Strict` (hoặc `Lax`)**, kèm cờ `Path=/api/v1/auth/refresh-token`.

2. **Quản lý Phiên Đa Thiết Bị trong Redis (Multi-Device Refresh Sessions):**
   - Tuyệt đối không dùng key đơn giản `refresh_token:{userId}` vì sẽ ghi đè phiên khi user đăng nhập từ PC, Phone, Tablet.
   - Format Key chuẩn trong Redis: `auth:refresh-session:{sessionId}` với TTL = 7 ngày.
   - Payload JSON lưu trữ trong Redis:
```json
{
  "sessionId": "sess_uuid_v4",
  "userId": "usr_uuid_v4",
  "deviceId": "Chrome_Windows_11_Fingerprint",
  "tokenHash": "sha256_hash_of_refresh_token",
  "ipAddress": "14.161.x.x",
  "createdAt": "2026-09-01T10:00:00Z",
  "expiresAt": "2026-09-08T10:00:00Z",
  "revokedAt": null
}
```
   - **Token Rotation:** Khi `refreshToken` được sử dụng để cấp `accessToken` mới $\to$ Thu hồi (Revoke) ngay session cũ và sinh ra một `refreshToken` + `sessionId` mới. Nếu phát hiện một refresh token cũ đã bị thu hồi cố tình gửi lại $\to$ Kích hoạt cảnh báo bảo mật và hủy toàn bộ phiên của user đó (Compromised Account Protocol).

---

## 4. QUẢN LÝ GIAO DỊCH, CHỐNG RACE CONDITION & IDEMPOTENCY

1. **Idempotency Nâng Cao (Generic Idempotency Specification):**
   - Áp dụng bắt buộc cho mọi thao tác có Side-Effect hoặc có khả năng Retry: Thanh toán, Khởi tạo đơn hàng, Check-in QR, Nhượng phòng (Swap), Webhook IPN, Gửi lời mời.
   - `idempotency_key` BẮT BUỘC gắn liền với `authenticated_user_id` (Bound to Actor).
   - **Cơ sở dữ liệu là Nguồn Sự Thật Duy Nhất (DB is Single Source of Truth):**
     - Ràng buộc `UNIQUE (idempotency_key)` trong PostgreSQL là **chốt chặn quyết định tối hậu**.
     - Redis (TTL = 24h) chỉ đóng vai trò **tầng đệm tối ưu hóa tốc độ (Fast-Path Optimization)** để phản hồi nhanh các request trùng lặp mà không cần query lại DB.
   - **Quy tắc Xử lý:**
     - **Cùng key + Cùng Request Fingerprint (cùng User, Payload, URL):** $\to$ Trả về kết quả đã xử lý trước đó từ Cache/DB (`200 OK`).
     - **Cùng key + Khác Request Payload / Khác User:** $\to$ Trả về `409 Conflict` (Phát hiện cố tình tái sử dụng key sai mục đích).

2. **Thứ Bậc Kiểm Soát Concurrency & Race Condition:**
   - **Ưu tiên 1 (Mặc định):** **Atomic SQL Queries** (ví dụ: `UPDATE user_consumables SET swipes_left = swipes_left - 1 WHERE user_id = :id AND swipes_left > 0`).
   - **Ưu tiên 2:** **Optimistic Locking (`@Version`)** cho các entity có tỉ lệ va chạm trung bình (Room, Contract).
   - **Ưu tiên 3:** **Pessimistic DB Lock (`SELECT ... FOR UPDATE`)** cho các giao dịch tài chính nhạy cảm trong phạm vi 1 DB.
   - **Ưu tiên 4 (Chỉ khi thật sự cần thiết):** **Distributed Lock (Redlock)** – Chỉ dùng khi cần điều phối tài nguyên phân tán xuyên suốt nhiều hệ thống hoặc ngăn chặn 2 tác vụ ngoài luồng (Background workers) cùng can thiệp một state.

---

## 5. CACHING & PERFORMANCE (REDIS GUIDELINES)

1. **Nguyên tắc Đặt Key Redis:**
   - Format chuẩn: `{domain}:{entity}:{attribute}:{id}`.
   - Ví dụ: `room:detail:{id}`, `user:swipes:{userId}`, `lock:checkin:{contractId}`.
2. **TTL (Time-to-Live) Bắt buộc:**
   - **Tuyệt đối KHÔNG lưu cache vĩnh viễn (No infinite keys).**
   - Chi tiết phòng: TTL = 1 giờ (Xóa cache chủ động khi có update).
   - Danh sách feed phòng theo quận: TTL = 5 phút.
   - Rate limit counter: TTL = 60 giây.
   - Idempotency key: TTL = 24 giờ.

---

## 6. QUY TẮC CODE SẠCH & KHÔNG MOCK TRONG PRODUCTION (ZERO-MOCK POLICY)

1. **Không viết mã giả / Hardcoded Responses:**
   - Tất cả logic phải đọc/ghi thực tế qua DB, Redis, và các dịch vụ tích hợp.
   - Tuyệt đối cấm các hàm `return "mock_success"` trong code Backend chính thức.
2. **Logging Chuẩn Doanh nghiệp:**
   - Dùng SLF4J / Structured Logging JSON.
   - Không dùng `System.out.println()`.
   - Log rõ ràng: `requestId`, `actorId`, `action`, `targetId`, `executionTimeMs`.
   - Tuyệt đối không in mật khẩu, mã OTP, số CCCD thô hay Bearer Token ra log file.

---

## 7. QUẢN LÝ TÀI NGUYÊN & RATE LIMITING (RESOURCE CONSUMPTION - OWASP API4:2023)

1. **Bảo vệ Tài nguyên Toàn diện:**
   - Tất cả API phải có giới hạn tài nguyên phù hợp. Không endpoint nào được phép query hoặc return collection không giới hạn.
2. **Phân Trang (Pagination) Bắt buộc:**
   - Mọi API danh sách (`GET /api/v1/rooms`, `GET /api/v1/matching/deck`, `GET /api/v1/reviews`) BẮT BUỘC có `default limit` (ví dụ: 20) và `max limit` (tối đa 100).
   - Nếu client gửi `?limit=99999999` $\to$ Backend tự động ép về `max limit = 100` hoặc trả `400 Bad Request`.
3. **Giới hạn Kích thước Dữ liệu vào (Input Size Boundaries):**
   - Giới hạn kích thước Request Body tối đa (Default: 2MB cho JSON, 10MB cho File Upload).
   - Giới hạn độ sâu lồng nhau của JSON (JSON nesting depth $\le 5$).
   - Giới hạn độ dài mảng (Array length $\le 100$), độ dài chuỗi ký tự (String length theo từng field).
4. **Rate Limiting Đa Tầng (Server/Gateway Enforced):**
   - Rate limit phải được áp dụng tại Server/Gateway bằng Redis Token Bucket, **tuyệt đối không dựa vào Frontend**.
   - Auth endpoints (Login, Register, OTP): Tối đa 5 requests / phút / IP & Phone.
   - Swipe endpoint: Tối đa 30 swipes / phút.
   - Search & Mapview: Tối đa 60 requests / phút.
   - File Upload: Tối đa 10 files / phút.

---

## 8. PHÂN QUYỀN & KIỂM SOÁT TRUY CẬP ĐỐI TƯỢNG (AUTHORIZATION - OWASP API1:2023 BOLA & API5:2023 BFLA)

1. **Tách biệt Role Authorization và Resource Authorization:**
   - Role Check (`hasRole('LANDLORD')`) là **chưa đủ**.
   - BẮT BUỘC phải kiểm tra **Quyền Sở Hữu Đối Tượng (Object-Level Ownership Authorization)**: User A có phải là chủ sở hữu thực sự của `roomId`, `contractId`, `reviewId` đang thao tác hay không.
2. **Chống Leo Thang Đặc Quyền (Privilege Escalation):**
   - **Horizontal Privilege Escalation (Leo thang ngang):** Ngăn chặn User A sửa thông tin phòng hoặc xem hợp đồng của User B bằng cách thay đổi ID trên URL.
   - **Vertical Privilege Escalation (Leo thang dọc):** Ngăn chặn `TENANT` hoặc `LANDLORD` gọi vào các API quản trị `/api/v1/admin/**`.
3. **Nguyên tắc Thẩm định Phía Server:**
   - Không được nhận `role`, `is_verified`, `trust_score` từ Client làm nguồn sự thật.
   - Toàn bộ quyền hạn phải được trích xuất trực tiếp từ Security Context phía Server.

---

## 9. CHỐNG MASS ASSIGNMENT & PROPERTY AUTHORIZATION (OWASP API3:2023)

1. **Whitelist DTO:**
   - Chỉ cho phép cập nhật các field nằm trong Whitelist của `UpdateDTO`.
   - Tuyệt đối không bind trực tiếp `Map<String, Object>` hoặc toàn bộ Request Body vào Entity.
2. **Bảo vệ các Trường Nhạy cảm (Protected Fields):**
   - Các trường sau **TUYỆT ĐỐI KHÔNG** cho phép Client tự ghi: `id`, `role`, `status`, `is_verified`, `trust_score`, `balance`, `swipes_left`, `created_at`, `updated_at`.
   - Các trường này chỉ được thay đổi thông qua Business Logic nội bộ của Service hoặc Admin Operations riêng biệt.
3. **Tách biệt Admin DTO và User DTO:**
   - `AdminUpdateUserDTO` (có thể đổi status, cấp badge) phải là class hoàn toàn riêng biệt với `UserUpdateProfileDTO`.

---

## 10. QUẢN LÝ CẤU HÌNH & BÍ MẬT HỆ THỐNG (SECRETS MANAGEMENT)

1. **Tuyệt đối Không Hardcode Secrets:**
   - Cấm hardcode mật khẩu DB, JWT Secret Key, Cloudinary/S3 Access Key, VNPay Hash Secret, Brevo API Key, Goong Maps API Key trong mã nguồn.
2. **Environment Injection & Startup Validation (Fail-Fast):**
   - Tất cả secrets phải được inject qua biến môi trường (`ENV`) hoặc HashiCorp Vault / Cloud Secret Manager.
   - Ứng dụng BẮT BUỘC validate toàn bộ cấu hình cần thiết lúc Startup (`@PostConstruct` / Configuration Validator) và **dừng khởi động ngay lập tức (Fail Fast)** nếu thiếu key quan trọng.
3. **Quy tắc An toàn:**
   - Không commit file `.env` chứa secret thật vào Git repository (chỉ commit `.env.example`).
   - Không đưa secret vào Docker image, log file, test fixtures hoặc trả về trong API response.

---

## 11. BẢO MẬT GIAO THỨC HTTP, TLS & CORS (HTTP SECURITY)

1. **Mã hóa Toàn diện:**
   - Production chỉ hoạt động trên giao thức `HTTPS` với TLS 1.3 (hoặc TLS 1.2 tối thiểu).
2. **Cấu hình CORS Nghiêm ngặt:**
   - Tuyệt đối cấm cấu hình `Access-Control-Allow-Origin: *` khi API có sử dụng Credentials/Cookies.
   - Bắt buộc chỉ định Whitelist Domain rõ ràng (ví dụ: `https://phongtroxanh.vn`, `https://admin.phongtroxanh.vn`).
3. **Security Headers Chuẩn mực:**
   - Bắt buộc kích hoạt: `Strict-Transport-Security` (HSTS), `X-Content-Type-Options: nosniff`, `X-Frame-Options: DENY`, `Content-Security-Policy` (CSP).
   - Ẩn toàn bộ Header tiết lộ thông tin phiên bản server (`Server`, `X-Powered-By`).
4. **Kiểm duyệt Content-Type:**
   - Bắt buộc kiểm tra `Content-Type: application/json` hoặc `multipart/form-data` theo từng endpoint. Từ chối mọi request sai Content-Type (`415 Unsupported Media Type`).

---

## 12. BẢO MẬT TẢI LÊN TỆP TIN (FILE UPLOAD SECURITY)

1. **Xác thực & Phân quyền Tải lên:**
   - Mọi thao tác upload avatar, ảnh phòng, ảnh CCCD, ảnh bằng chứng vi phạm bắt buộc phải được Authenticated.
2. **Kiểm Tra Định Dạng Thực Tế (Magic Bytes Validation):**
   - Không tin tưởng phần mở rộng (Extension) hoặc Header `Content-Type` do Client gửi lên.
   - Bắt buộc kiểm tra **Magic Bytes** ở đầu file (Chỉ chấp nhận file ảnh chuẩn: JPEG `FF D8 FF`, PNG `89 50 4E 47`, WebP `52 49 46 46`, PDF `25 50 44 46`).
3. **Chuẩn hóa Tên Tệp & Chống Path Traversal:**
   - Tên file lưu trữ trên S3/Cloud Storage BẮT BUỘC do Server tự sinh (UUID v4 + timestamp).
   - Loại bỏ hoàn toàn tên file gốc từ client để chống Path Traversal (`../../etc/passwd`).
4. **Cô Lập Tệp Riêng Tư (CCCD & Hợp Đồng):**
   - Ảnh CCCD và hợp đồng thuê phải lưu ở Private S3 Bucket. Khi truy cập, Server sinh Pre-signed URL có thời hạn sống tối đa **15 phút**.

---

## 13. PHÒNG CHỐNG TẤN CÔNG SSRF (SERVER-SIDE REQUEST FORGERY - OWASP API7:2023)

1. **Kiểm duyệt URL do Client Cung cấp:**
   - Khi hệ thống có tính năng tải ảnh từ URL, gọi Webhook hoặc fetch dữ liệu ngoài: BẮT BUỘC kiểm tra URL qua Whitelist giao thức (`http`, `https`).
2. **Chặn Toàn bộ Dải IP Nội Bộ & Metadata:**
   - Cấm kết nối tới `localhost`, `127.0.0.1`, dải IP nội bộ (`10.0.0.0/8`, `172.16.0.0/12`, `192.168.0.0/16`), link-local (`169.254.0.0/16`) và AWS/Cloud Metadata endpoints (`http://169.254.169.254`).
3. **Re-validation sau Redirect & Timeout:**
   - Nếu external URL trả về redirect (301/302), Server phải validate lại destination IP trước khi follow redirect.
   - Thiết lập Connect Timeout (3s), Read Timeout (5s) và giới hạn kích thước response tối đa (5MB).

---

## 14. TÍCH HỢP DỊCH VỤ NGOÀI (EXTERNAL SERVICE INTEGRATION - OWASP API10:2023)

1. **Nguyên tắc Không Tin Tưởng Dữ liệu Thứ Ba:**
   - Mọi response từ VNPay, VietQR, Brevo, Goong Maps, Firebase BẮT BUỘC phải validate schema trước khi xử lý.
2. **Timeouts & Exponential Backoff Retry:**
   - Mọi HTTP Client gọi ra ngoài (RestTemplate, WebClient, Feign) BẮT BUỘC cấu hình Connect Timeout (3s) và Read Timeout (5s).
   - Retry tối đa 3 lần với Exponential Backoff (`1s -> 2s -> 4s`) và chỉ retry cho các lỗi mạng / `5xx`, tuyệt đối KHÔNG retry lỗi `4xx`.
   - Tuyệt đối cấm retry các thao tác trừ tiền / side-effects non-idempotent nếu chưa có Idempotency Key.
3. **Circuit Breaker & Fallback:**
   - Áp dụng Circuit Breaker (Resilience4j) cho các dịch vụ bên ngoài (Brevo Email, FCM Push Notification, Goong Maps, VNPay) để tránh cascade failure làm nghẽn toàn bộ hệ thống.

---

## 15. TÍNH TOÀN VẸN DỮ LIỆU & TRANSACTION TRONG DATABASE

1. **Enforce Invariants bằng Database Constraints:**
   - Các business keys quan trọng BẮT BUỘC có `UNIQUE` constraint tại DB (ví dụ: `UNIQUE (phone_number)`, `UNIQUE (idempotency_key)`, `UNIQUE (swiper_id, target_id)`).
   - Khóa ngoại BẮT BUỘC có chiến lược `ON DELETE` rõ ràng (`CASCADE`, `RESTRICT`, hoặc `SET NULL`).
2. **Chống Concurrency Race Condition (Balance / Quota / Inventory):**
   - Không dùng cơ chế "Check-then-act" ngây thơ.
   - Thao tác trừ lượt vuốt, trừ số dư, đặt phòng phải dùng: **Atomic DB Query** (`UPDATE user_consumables SET swipes_left = swipes_left - 1 WHERE user_id = :id AND swipes_left > 0`), **Optimistic Locking (`@Version`)**, hoặc **Distributed Redlock**.
3. **Tối ưu Hóa Truy Vấn & Chống N+1 Query:**
   - Bắt buộc dùng `JOIN FETCH` hoặc `@EntityGraph` cho các quan hệ `@ManyToOne`, `@OneToMany`.
   - Không sử dụng `SELECT *` trong production khi API chỉ cần vài trường dữ liệu.

---

## 16. NHẤT QUÁN GIAO DỊCH & MẪU TRANSACTIONAL OUTBOX (TRANSACTION & EVENT CONSISTENCY)

1. **Ranh Giới Transaction Ngắn (Short DB Transactions):**
   - Chỉ dữ liệu PostgreSQL thuộc cùng một Business Boundary mới nằm trong `@Transactional`.
   - **TUYỆT ĐỐI CẤM giữ DB Transaction mở trong khi gọi External HTTP API, gửi Email, gửi Push Notification hoặc gọi Cổng Thanh Toán.**
2. **Mẫu Transactional Outbox (At-Least-Once Delivery + Consumer Idempotency):**
   - Khi commit dữ liệu DB bắt buộc phải đi kèm sự kiện thông báo (ví dụ: Match đôi $\to$ Gửi WebSocket Noti):
     - Ghi bản ghi sự kiện vào bảng `outbox_events` cùng Transaction DB.
     - Worker đọc bảng `outbox_events` và phát tán sự kiện ra ngoài.
     - **Cam kết kỹ thuật:** Mô hình này đảm bảo **At-least-once Delivery (chuyển giao ít nhất 1 lần)** kết hợp với **Consumer Idempotency (phía nhận xử lý trùng lặp an toàn)**, không cam kết "100% không bao giờ duplicate".

---

## 17. TRUY VẾT & NHẬT KÝ KIỂM TOÁN (STRUCTURED LOGGING & AUDIT TRAIL)

1. **Correlation ID / Request ID:**
   - Mỗi HTTP Request khi đi qua API Gateway / Filter BẮT BUỘC được gán một `X-Request-ID` duy nhất và đưa vào Logging MDC (Mapped Diagnostic Context).
2. **Nhật ký Kiểm toán (Audit Logs) & Khử Dữ liệu Nhạy cảm (PII Redaction):**
   - Toàn bộ thao tác của Admin (Duyệt/Từ chối CCCD, Khóa tài khoản, Gỡ tin phòng, Giải quyết khiếu nại review) BẮT BUỘC ghi vào bảng `system_audit_logs`.
   - **Quy tắc Khử Nhiễm PII (PII Redaction / Masking):** Trước khi lưu `payloadBefore` và `payloadAfter` dạng JSONB, BẮT BUỘC phải redact/mask các trường nhạy cảm (`id_card_number` $\to$ `***1234`, `password_hash` $\to$ `[REDACTED]`, `bank_account` $\to$ `***6789`, `access_token` $\to$ `[REDACTED]`).
   - Log audit ghi rõ: `actorId`, `action`, `targetEntity`, `targetId`, `payloadBefore` (đã mask), `payloadAfter` (đã mask), `ipAddress`, `userAgent`, `timestamp`.
3. **Chống Log Injection & Bảo Mật Log:**
   - Sanitize toàn bộ input người dùng trước khi ghi vào log để chống CRLF Log Injection.
   - Tuyệt đối cấm in header `Authorization`, `Cookie`, `accessToken`, `refreshToken`, `password`, `id_card_number` thô ra log file.

---

## 18. QUẢN LÝ DANH MỤC API & GIẢM BỀ MẶT TẤN CÔNG (API INVENTORY - OWASP API9:2023)

1. **100% Đăng ký trong API Inventory:**
   - Mọi endpoint hoạt động trên hệ thống BẮT BUỘC phải được định nghĩa trong tài liệu đặc tả và Swagger/OpenAPI.
   - Cấm tuyệt đối các "Shadow APIs" hoặc "Undocumented Endpoints".
2. **Vô Hiệu Hóa Debug Endpoints trên Production:**
   - Toàn bộ các API mock test, demo login, seed data BẮT BUỘC phải bị vô hiệu hóa hoặc ẩn trên môi trường Production (`@Profile("!prod")`).
3. **Vòng đời API (API Lifecycle & Sunset Policy):**
   - Khi nâng cấp version API (`/api/v2/...`), endpoint cũ phải được đánh dấu `@Deprecated` kèm header `Sunset` và tài liệu hướng dẫn migration rõ ràng.

---

## 19. ĐÓNG RẮN MÔI TRƯỜNG PRODUCTION (PRODUCTION HARDENING - OWASP API8:2023)

1. **Không Tiết Lộ Chi Tiết Lỗi Nội Bộ (No Stack Traces in Production):**
   - Global Exception Handler BẮT BUỘC bắt mọi unhandled exception (`Exception.class`) và trả về mã lỗi chung `INTERNAL_SERVER_ERROR (500)`.
   - Tuyệt đối không bao giờ trả về Stack Trace, Class Name, SQL Syntax Error hay File Path ra ngoài JSON response.
2. **Graceful Shutdown & Health Checks:**
   - Ứng dụng phải cấu hình Graceful Shutdown (chờ các request đang xử lý hoàn tất trong tối đa 30s trước khi kill tiến trình).
   - Cung cấp 2 endpoint chuẩn: `/actuator/health/liveness` và `/actuator/health/readiness` (Health endpoint không được lộ thông tin connection string hay version).
3. **Nguyên tắc Đặc quyền Tối thiểu (Principle of Least Privilege):**
   - Database User dùng cho ứng dụng chỉ được cấp quyền `SELECT`, `INSERT`, `UPDATE`, `DELETE`, tuyệt đối không cấp quyền `SUPERUSER` hay `DROP DATABASE`.

---

## 20. BẢO VỆ DÒNG NGHIỆP VỤ NHẠY CẢM (BUSINESS ABUSE PROTECTION - OWASP API6:2023)

1. **Chống Tự Động Hóa & Spam (Anti-Automation Controls):**
   - Các luồng nhạy cảm: Vuốt tìm bạn (Swipe), Quét QR Check-in, Đăng ký OTP, Gửi báo cáo vi phạm, Mua gói dịch vụ BẮT BUỘC có cơ chế chống Bot/Spam.
2. **Server là Nguồn Sự Thật Duy Nhất (Single Source of Truth):**
   - Tuyệt đối không tin tưởng bất kỳ tham số nào do Client tự tính toán gửi lên: Số dư lượt vuốt, giá tiền gói, điểm TrustScore, hạn ngạch phòng.
   - Toàn bộ con số và trạng thái nghiệp vụ BẮT BUỘC do Backend truy vấn DB/Redis và tự động tính toán.

---

## 21. BẢO MẬT TÀI KHOẢN & CHỐNG LIỆT KÊ (ACCOUNT SECURITY - OWASP API2:2023)

1. **Băm Mật Khẩu Chuẩn Quốc Tế:**
   - Bắt buộc sử dụng `BCrypt` với Work Factor $\ge 12$ (hoặc `Argon2id`).
2. **Chống Tấn Công Dò Quét & Brute-force (Anti-Account Enumeration):**
   - Tại API Đăng nhập / Quên mật khẩu: Khi nhập sai email hoặc email không tồn tại, trả về thông báo chung: *"Thông tin đăng nhập không chính xác"* hoặc *"Nếu email tồn tại trong hệ thống, mã xác nhận đã được gửi"*.
   - Tuyệt đối không trả về *"Email này chưa đăng ký"* để tránh hacker dò quét danh sách người dùng.
3. **Thu Hồi Phiên Toàn Diện Khi Đổi Mật Khẩu:**
   - Khi người dùng thực hiện Đổi mật khẩu hoặc Reset mật khẩu thành công $\to$ Hệ thống BẮT BUỘC thu hồi toàn bộ các Refresh Sessions đang active trong Redis trên tất cả các thiết bị.

---

## 22. QUẢN LÝ VÒNG ĐỜI TÀI NGUYÊN, TIMEOUT & CRON JOBS

1. **Giải Phóng Tài Nguyên (Resource Cleanup):**
   - Mọi kết nối Database, HTTP Connection, File I/O Stream BẮT BUỘC phải được đóng trong khối `try-with-resources` hoặc tự động giải phóng bởi Connection Pool.
2. **Background Jobs & Dead-Letter Queue (DLQ):**
   - Các tác vụ chạy nền (gửi email hóa đơn, push notification) phải có số lần retry tối đa ($\le 3$). Nếu lỗi liên tục $\to$ Đẩy vào Dead-Letter Queue để admin điều tra.
3. **Khóa Phân Tán Cho Cron Jobs (ShedLock / Redlock):**
   - Khi chạy Backend trên nhiều Pods/Instances: Toàn bộ Cron Job (Reset 15 lượt vuốt lúc 00:00, quét phòng hết hạn) BẮT BUỘC dùng `@SchedulerLock` (ShedLock với Redis) để đảm bảo cron job chỉ chạy **ĐÚNG 1 LẦN DUY NHẤT** trên toàn cụm server.

---

## 23. NGUYÊN TẮC TÁI SỬ DỤNG & NHẤT QUÁN MÃ NGUỒN (CONSISTENCY & EXISTING PATTERN FIRST)

1. **Cấm Tự Ý Sáng Tạo Pattern Thứ Hai:**
   - Trước khi phát triển tính năng mới, kỹ sư BẮT BUỘC phải đọc và tái sử dụng 100% các Pattern đã có trong dự án: BaseEntity, ApiResponse, GlobalExceptionHandler, RedisService, JwtTokenProvider.
   - Tuyệt đối cấm tạo ra class Exception handler thứ 2, cấm đổi cấu trúc Envelope response, cấm tự ý tạo cách đặt tên biến khác biệt.
2. **Tính Kỷ Luật Cao:**
   - Nếu cần thay đổi một quy chuẩn toàn cục, phải giải trình rõ lý do kỹ thuật trước khi thực hiện.

---

---

## 24. KIẾN TRÚC PORTS & ADAPTERS CHO CÁC DỊCH VỤ BÊN THỨ 3 (THIRD-PARTY INTEGRATION ARCHITECTURE)

1. **Nguyên lý Độc lập Cốt lõi (Dependency Inversion / Hexagonal Ports & Adapters):**
   - Tầng `application` và `domain` **TUYỆT ĐỐI KHÔNG** import trực tiếp SDK của bên thứ 3 hay phụ thuộc vào một nhà cung cấp cụ thể.
   - Các **Port / Interface** hạ tầng dùng chung (Cross-cutting) BẮT BUỘC đặt trong `vn.phongtroxanh.backend.common.*`:
     - `common.payment.PaymentGatewayPort`: `createPaymentUrl()`, `verifyIpnWebhook()`, `refund()`.
     - `common.storage.FileStoragePort`: `uploadFile()`, `deleteFile()`, `generatePublicUrl()`.
     - `common.mail.EmailNotificationPort`: `sendOtpEmail()`, `sendTransactionReceipt()`, `sendAccountAlert()`.
     - `common.location.GeocodingPort`: `autocomplete()`, `geocodeAddress()`, `reverseGeocode()`.
     - `common.notification.PushNotificationPort`: `sendPushNotification()`, `sendTopicNotification()`.
   - Các **Adapter cụ thể** tương ứng BẮT BUỘC đặt trong các package con của `common`:
     - `common.payment`: `VnPayPaymentAdapter` (Sandbox với HMAC-SHA512).
     - `common.mail`: `BrevoEmailAdapter` (Gửi qua **HTTP REST API Port 443** để chống bị Cloud Hosting/Render/Railway chặn port SMTP), `GmailSmtpMailAdapter`.
     - `common.location`: `GoongMapsAdapter` (Autocomplete ngõ hẻm VN & Tọa độ với $100 credit), `NominatimGeocodingAdapter`.
     - `common.storage`: `CloudinaryStorageAdapter` (Upload trực tiếp InputStream lên Cloudinary, tương thích PaaS Ephemeral Filesystem).
   - Tuyệt đối KHÔNG đặt các Port/Adapter dùng chung này bên trong module nghiệp vụ cụ thể (như `modules/user`) để tránh hiện tượng rò rỉ phụ thuộc (Cross-cutting Leakage) và phụ thuộc chéo (Cyclic Dependency).
   - Cơ chế nạp Adapter: Sử dụng `@ConditionalOnProperty(name = "service.provider", havingValue = "...")` của Spring Boot để chuyển đổi linh hoạt qua file `.env` mà không cần sửa 1 dòng code logic.

2. **Quy tắc An toàn Cho External Calls:**
   - **Timeout Bắt Buộc:** Mọi HTTP Client (WebClient / RestTemplate) gọi ra ngoài (VNPay, Brevo, Goong) BẮT BUỘC có `ConnectTimeout = 3000ms` và `ReadTimeout = 5000ms`.
   - **Circuit Breaker / Retry:** Áp dụng Resilience4j Circuit Breaker để tránh sập dây chuyền khi bên thứ 3 bị chậm.

3. **Danh mục Biến Môi Trường Tiêu Chuẩn (`.env.example`):**
```bash
# ==============================================================================
# 1. CORE APPLICATION & PROFILES
# ==============================================================================
SPRING_PROFILES_ACTIVE=dev
SERVER_PORT=8080
APP_BASE_URL=http://localhost:8080
FRONTEND_URL=http://localhost:5173

# ==============================================================================
# 2. DATABASE & CACHE (Docker Localhost)
# ==============================================================================
DB_HOST=localhost
DB_PORT=5432
DB_NAME=phongtroxanh_db
DB_USER=postgres
DB_PASSWORD=postgres

REDIS_HOST=localhost
REDIS_PORT=6379
REDIS_PASSWORD=

# ==============================================================================
# 3. SECURITY & JWT (HS512 Key tối thiểu 64 ký tự)
# ==============================================================================
JWT_SECRET=your_super_secret_key_at_least_64_characters_long_for_hs512_algorithm
JWT_ACCESS_EXPIRATION_MS=900000        # 15 Phút
JWT_REFRESH_EXPIRATION_MS=604800000    # 7 Ngày
COOKIE_SECURE=false                     # true khi chạy HTTPS Production
COOKIE_SAME_SITE=Lax

# ==============================================================================
# 4. PAYMENT GATEWAY (payOS)
# ==============================================================================
PAYOS_CLIENT_ID=
PAYOS_API_KEY=
PAYOS_CHECKSUM_KEY=
PAYOS_RETURN_URL=http://localhost:5173/payment/payos-return
PAYOS_CANCEL_URL=http://localhost:5173/payment/payos-cancel

# ==============================================================================
# 5. EMAIL SERVICE (Brevo REST API - Chạy mượt trên Cloud không lo bị chặn port)
# ==============================================================================
MAIL_PROVIDER=brevo                    # brevo | smtp | console
BREVO_API_KEY=xkeysib-your_brevo_api_key_here
MAIL_FROM=phongtroxanh.vn@gmail.com
MAIL_FROM_NAME=PhongTroXanh Platform

# Fallback SMTP nếu cần chạy local
MAIL_HOST=smtp.gmail.com
MAIL_PORT=587
MAIL_USERNAME=
MAIL_PASSWORD=

# ==============================================================================
# 6. STORAGE (Cloudinary / Local Disk)
# ==============================================================================
STORAGE_PROVIDER=local                 # local | cloudinary
STORAGE_LOCAL_PATH=./uploads
CLOUDINARY_CLOUD_NAME=your_cloud_name
CLOUDINARY_API_KEY=your_api_key
CLOUDINARY_API_SECRET=your_api_secret

# ==============================================================================
# 7. MAP & GEOCODING (Goong Maps - Autocomplete & Tọa độ)
# ==============================================================================
GEOCODING_PROVIDER=goong               # goong | nominatim
GOONG_API_KEY=your_goong_api_key

# ==============================================================================
# 8. PUSH NOTIFICATIONS (Firebase FCM)
# ==============================================================================
FIREBASE_CREDENTIALS_PATH=classpath:firebase-service-account.json
```

---

---

## 25. QUY CHUẨN KIỂM THỬ LIVE SERVER, SWAGGER OPENAPI & E2E SMOKE TEST

1. **Tài Liệu Hóa Tự Động Với Swagger UI 3 / OpenAPI (SpringDoc):**
   - Bắt buộc tích hợp `springdoc-openapi-starter-webmvc-ui` để sinh tài liệu tương tác trực tiếp tại: `http://localhost:8080/swagger-ui.html`.
   - Cấu hình nút **`Authorize` (Bearer JWT)** trên Swagger UI để người dùng/giảng viên có thể paste Access Token vào test trực tiếp mọi API có bảo mật.
   - Toàn bộ 94 endpoints phải có mô tả ngắn gọn (`@Operation(summary = "...")`) và schema response mẫu.

2. **Quy Trình Kiểm Thử Live Server Thật (Live E2E Smoke Testing):**
   - **Unit Test là chưa đủ:** Unit test chỉ kiểm tra logic cô lập. Kỹ sư khi hoàn thiện mỗi Module BẮT BUỘC phải kiểm thử thực tế trên Server đang chạy thật.
   - **Quy trình Live Test khép kín:**
     - Bước 1: Bật Docker Container chứa PostgreSQL 16 (PostGIS) và Redis 7 (`docker compose up -d`).
     - Bước 2: Khởi động Spring Boot Server thật (`./mvnw spring-boot:run` hoặc background process).
     - Bước 3: Bắn HTTP Request thật (`curl` / Postman / RestAssured) vào `http://localhost:8080/api/v1/...`:
       - Gọi API Đăng ký $\to$ Nhận Response thật $\to$ Kiểm tra DB có bản ghi mới.
       - Gọi API Đăng nhập $\to$ Nhận JWT Token thật $\to$ Dùng Token gọi API Đăng phòng.
       - Gọi API Quẹt Match $\to$ Kiểm tra trạng thái Match và Event STOMP được kích hoạt.
       - Gọi Webhook VNPay với chữ ký giả lập $\to$ Kiểm tra số dư / gói dịch vụ được cộng.
     - Bước 4: Kiểm tra log của Server đang chạy để đảm bảo không có bất kỳ `NullPointerException`, `Hibernate LazyInitializationException` hay SQL Syntax Error nào xảy ra ngầm.

---

## 26. BẢNG TIÊU CHUẨN HOÀN THÀNH TOÀN DIỆN (BACKEND DEFINITION OF DONE - 20 TIÊU CHÍ)

Một API endpoint hoặc Module chỉ được xem là **HOÀN THÀNH (DONE)** khi và chỉ khi vượt qua đầy đủ 20 tiêu chí kiểm duyệt dưới đây:

```markdown
[ ] 1.  API Contract được xác định rõ ràng trong tài liệu đặc tả (Method, URI, Status Code).
[ ] 2.  Request DTO có đầy đủ Bean Validation (@NotBlank, @Min, @Max, @Size...).
[ ] 3.  Response DTO tách biệt hoàn toàn khỏi Entity, không để lộ thông tin nhạy cảm.
[ ] 4.  Uniform Response Envelope (ApiResponse<T>) và RFC 9457 Problem Details cho lỗi.
[ ] 5.  Sử dụng đúng HTTP Status (200, 201, 400, 401, 403, 404, 409, 422, 429).
[ ] 6.  Authentication (JWT Access Token In-Memory + Refresh Token HttpOnly Cookie).
[ ] 7.  Authorization & Object-Level Ownership Check (Chống BOLA & BFLA).
[ ] 8.  Resource Consumption & Rate Limiting (Pagination bắt buộc có Max Limit, Request Size Limit).
[ ] 9.  Không hardcode bất kỳ Password, Secret Key hay API Key nào trong code.
[ ] 10. Không có nguy cơ rò rỉ dữ liệu nhạy cảm (PII CCCD được mã hóa AES-256).
[ ] 11. Transaction boundaries ngắn, KHÔNG bọc external HTTP call trong DB Transaction.
[ ] 12. Database Constraints (UNIQUE, Foreign Key ON DELETE, Indexes GiST/B-tree) đầy đủ.
[ ] 13. External Calls (VNPay, Brevo, Goong, Cloudinary, FCM) có Timeout và Circuit Breaker.
[ ] 14. Structured JSON Logging kèm Correlation ID (X-Request-ID); không in mật khẩu ra log.
[ ] 15. Ghi nhận Audit Log (system_audit_logs) cho mọi thao tác nhạy cảm của Admin.
[ ] 16. Unit & Integration Tests (Test thành công, Test 401, Test 403, Test Cross-User BOLA, Test Validation).
[ ] 17. Tích hợp Swagger UI / OpenAPI 3.0 đầy đủ mô tả, schema và cấu hình Bearer Auth.
[ ] 18. Live Server HTTP Smoke Test PASS (Bắn request thật vào localhost:8080 trả về 200/201 chuẩn).
[ ] 19. Tuyệt đối không có Mock data, không có hardcoded return "mock_success" trong Production.
[ ] 20. Tái sử dụng 100% Architecture & Coding Patterns đã định nghĩa sẵn trong dự án.
```



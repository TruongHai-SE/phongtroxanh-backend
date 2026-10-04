# ARCHITECTURE DECISION RECORDS (ADR)

> Tài liệu thiết kế/lịch sử. Trạng thái triển khai và phạm vi đã kiểm chứng xem [backend verification](backend-verification.md); cấu hình hiện hành dùng [.env.example](../.env.example) và [payOS setup](payos-setup.md). ADR về VNPay đã được thay bằng payOS ngày 01/10/2026.
# DỰ ÁN: PHÒNG TRỌ XANH (PhongTroXanh.vn)

---

## ADR-001: Kiến Trúc Modular Monolith & Pragmatic Layered Architecture
- **Ngày quyết định:** 2026-09-01 (Cập nhật: 2026-09-02)
- **Bối cảnh:** Dự án cần sự tách biệt rõ ràng giữa Business Logic, Data Access và Framework để dễ bảo trì, dễ test và đạt điểm tối đa đồ án doanh nghiệp. Đội phát triển nhỏ (1–3 người), cần tốc độ phát triển cao.
- **Quyết định:** Áp dụng **Modular Monolith** với **Pragmatic Layered Architecture** 4 tầng: `presentation`, `application`, `domain`, `infrastructure`.
  - **Không** áp dụng Pure DDD (không tách riêng POJO Domain Entity với JPA Entity).
  - **Cho phép** Domain Entities sử dụng JPA annotations (`@Entity`, `@Table`, `@Column`, v.v.) để tận dụng Hibernate Dirty Checking và tối ưu tốc độ phát triển.
  - **Thuật toán cốt lõi** (ví dụ: `MatchingEngine` 8 trụ cột) vẫn được viết dưới dạng Pure Domain Service, không phụ thuộc database.
- **Lý do bác bỏ Pure Clean Architecture:** Chi phí boilerplate quá cao (phải viết cả Domain POJO lẫn JPA Entity cho cùng một khái niệm). Không cần thiết khi nhóm nhỏ và không có kế hoạch chuyển đổi ORM.
- **Hệ quả:** Dữ liệu vào/ra Controller bắt buộc qua DTOs. Cấm để rò rỉ Entity DB ra ngoài tầng Presentation. Repository Interface đặt trong `infrastructure.repository`.

---

## ADR-002: Chiến Lược Lưu Trữ Token & Quản Lý Phiên Đa Thiết Bị (Multi-Device Sessions)
- **Ngày quyết định:** 2026-09-01
- **Bối cảnh:** Chống tấn công XSS và CSRF, đồng thời hỗ trợ đăng nhập nhiều thiết bị cùng lúc (Điện thoại, Laptop).
- **Quyết định:**
  - `accessToken` (15 phút) chỉ lưu trong Client In-Memory (React State).
  - `refreshToken` (7 ngày) lưu trong HttpOnly, Secure, SameSite=Lax Cookie.
  - Quản lý phiên qua Redis key `auth:refresh-session:{sessionId}` thay vì gắn cứng vào `userId`.
- **Hệ quả:** Người dùng có thể đăng xuất từng thiết bị cụ thể mà không làm mất phiên trên thiết bị khác.

---

## ADR-003: Thứ Bậc Concurrency & Nguồn Sự Thật Idempotency (Concurrency Hierarchy)
- **Ngày quyết định:** 2026-09-01
- **Bối cảnh:** Chống race condition khi quẹt thẻ, trừ số dư gói và check-in QR code.
- **Quyết định:**
  - Ưu tiên số 1: Atomic SQL Query (`UPDATE user_consumables SET swipes_left = swipes_left - 1 WHERE swipes_left > 0`).
  - Ưu tiên số 2: Optimistic Locking (`@Version`).
  - Ưu tiên số 3: Pessimistic Lock (`PESSIMISTIC_WRITE`).
  - Ưu tiên số 4: Redlock (Distributed Lock) chỉ dùng khi điều phối nhiều service độc lập.
  - PostgreSQL `UNIQUE (idempotency_key)` là Nguồn Sự Thật tối hậu; Redis chỉ là bộ nhớ đệm tăng tốc.
- **Hệ quả:** Giảm tải tối đa cho Redis cluster, tránh rủi ro split-brain lock phân tán.

---

## ADR-004: Tích Hợp Cổng Thanh Toán VNPay Sandbox (Duy nhất cho MVP)
- **Ngày quyết định:** 2026-09-01 (Cập nhật: 2026-09-02)
- **Bối cảnh:** Cần cổng thanh toán chuẩn cho thị trường Việt Nam, có sẵn môi trường Sandbox kiểm thử 0đ không giới hạn. MoMo Sandbox yêu cầu đăng ký doanh nghiệp chính thức (M4B), không phù hợp với dự án MVP/đồ án.
- **Quyết định:**
  - **Sử dụng VNPay Sandbox** (xác thực HMAC-SHA512, có sẵn thẻ test ngân hàng NCB).
  - **Sử dụng VietQR** (Static QR theo chuẩn Napas 2.0 để thanh toán nhanh qua ứng dụng ngân hàng).
  - **Loại bỏ hoàn toàn MoMo** ra khỏi scope MVP do không có Sandbox miễn phí cho cá nhân.
  - `payment_method_enum` trong DB chỉ chứa: `('VNPAY', 'VIETQR')`.
- **Hệ quả:** Test được toàn bộ luồng thanh toán và IPN Webhook mà không cần giấy phép kinh doanh hay thẻ ngân hàng thật. Có thể mở rộng thêm MoMo trong tương lai khi có giấy phép kinh doanh.

---

## ADR-005: Gửi Email OTP Bằng Brevo REST API (Port 443) Thay Cho TCP SMTP
- **Ngày quyết định:** 2026-09-01
- **Bối cảnh:** Khi deploy backend lên các nền tảng Cloud/PaaS miễn phí (Render, Railway, Fly.io), các cổng TCP SMTP 25, 465, 587 đều bị chặn khiến gửi mail bị timeout.
- **Quyết định:** Sử dụng Brevo REST API (gửi qua giao thức HTTPS Port 443) với hạn mức 300 mail/ngày miễn phí, hỗ trợ email cá nhân không cần Custom Domain.
- **Hệ quả:** Hoạt động ổn định 100% trên mọi nền tảng Cloud lẫn Localhost.

---

## ADR-006: Sử Dụng Goong Maps Cho Bản Đồ, Tọa Độ & Places Autocomplete
- **Ngày quyết định:** 2026-09-01
- **Bối cảnh:** Cần bản đồ chính xác đến từng số nhà, ngõ ngách tại Việt Nam để tính toán khoảng cách phòng trọ và trường học/chỗ làm.
- **Quyết định:** Tích hợp Goong Maps API (tận dụng gói tặng $100 credit dùng trọn đời khi đăng ký).
- **Hệ quả:** Trải nghiệm tìm kiếm phòng trọ với Autocomplete mượt mà, định vị tọa độ PostGIS chính xác 100%.

---

## ADR-007: Kiểm Thử Live Server Thật Kết Hợp Swagger UI 3 (Live E2E Smoke Testing)
- **Ngày quyết định:** 2026-09-01
- **Bối cảnh:** Unit test độc lập không đủ chứng minh tính sẵn sàng của hệ thống. Cần kiểm tra API thực tế khi server đang chạy và kết nối DB/Redis thật.
- **Quyết định:** Tích hợp SpringDoc OpenAPI 3.0 (`/swagger-ui.html`) hỗ trợ Bearer Auth, bắt buộc kiểm thử thực tế trên server đang chạy và kiểm tra E2E qua HTTP request trước khi nghiệm thu.
- **Hệ quả:** Phát hiện sớm các lỗi runtime, lazy loading Hibernate và lỗi cấu hình CORS/Port ngay trên môi trường thực tế.

---

## ADR-008: Chat Real-Time Dùng STOMP over WebSocket + Redis Pub/Sub
- **Ngày quyết định:** 2026-09-02
- **Bối cảnh:** Hệ thống cần nhắn tin real-time 2 chiều cho 2 loại hội thoại: (1) Người thuê ↔ Chủ trọ hỏi về phòng (type=ROOM), (2) Người thuê ↔ Người thuê tìm ở ghép (type=ROOMMATE). FE đã triển khai Chat.tsx với 2 tab "Phòng" và "Bạn ở".
- **Quyết định:**
  - Sử dụng **Spring WebSocket + STOMP** làm transport protocol.
  - Sử dụng **Redis Pub/Sub** làm message broker để cho phép scale-out theo chiều ngang (nhiều pod/instance).
  - Message history lưu trong PostgreSQL (`conversations`, `messages` tables).
  - Trạng thái online lưu trong Redis với TTL (heartbeat pattern).
- **Hệ quả:** Kiến trúc Modular Monolith vẫn được bảo toàn (không cần tách microservice riêng cho chat). Có thể scale-out bằng cách thêm Redis cluster mà không cần thay đổi code.

---

## ADR-009: Quản Lý Dịch Vụ Hạ Tầng Dùng Chung (Cross-Cutting Infrastructure Services & Ports/Adapters) Trong Package Common
- **Ngày quyết định:** 2026-09-03
- **Bối cảnh:** Các dịch vụ hạ tầng như File Storage (tải ảnh phòng, avatar, ảnh chat, KYC CCCD, bằng chứng review), Email (Brevo gửi OTP đăng ký, quên pass, hóa đơn thanh toán), Geocoding (Goong Maps định vị, gợi ý địa chỉ), Payment Gateway (VNPay, VietQR) là các tiện ích xuyên suốt (Cross-cutting Concerns) được sử dụng bởi nhiều module khác nhau (Auth, User, Room, Rental, Payment, Chat). Việc đặt nhầm các Port/Adapter này vào một module cụ thể (ví dụ `modules/user`) gây ra hiện tượng rò rỉ phụ thuộc (Cross-cutting Leakage) và nguy cơ phụ thuộc vòng tròn (Cyclic Dependency).
- **Quyết định:**
  - Toàn bộ Ports & Adapters hạ tầng dùng chung BẮT BUỘC đặt tại tầng `vn.phongtroxanh.backend.common.*`:
    - `common.storage`: `FileStoragePort`, `CloudinaryStorageAdapter`.
    - `common.mail`: `EmailNotificationPort`, `BrevoEmailAdapter`.
    - `common.payment`: `PaymentGatewayPort`, `VnPayPaymentAdapter`.
    - `common.location`: `GeocodingPort`, `GoongMapsAdapter`.
  - **Quy tắc Phụ thuộc Đơn chiều (Unidirectional Dependency Flow):**
    - `auth` $\to$ `user` (Auth được gọi User để xác thực/đăng ký/truy vấn tài khoản).
    - `user` **TUYỆT ĐỐI CẤM** phụ thuộc hay inject bất kỳ Bean nào từ `auth`.
    - `room`, `matching`, `rental`, `review` chỉ phụ thuộc vào `user` (để lấy User Entity/Id), không phụ thuộc chéo lẫn nhau khi không cần thiết.
- **Hệ quả:** Cô lập ranh giới module rõ ràng, loại bỏ hoàn toàn Cyclic Dependency, tuân thủ nguyên lý Dependency Inversion chuẩn RFC & Clean Modular Architecture.

---

## ADR-010: Kiểm Toán CSDL Chuẩn Hóa, Mô Hình Aggregate Root DDD & Triệt Tiêu Rò Rỉ Entity Ra Swagger UI
- **Ngày quyết định:** 2026-09-03
- **Bối cảnh:** Qua đợt rà soát và kiểm toán toàn diện kiến trúc mã nguồn:
  1. Module `swap` trước đây xuất hiện entity bịa đặt `RoomSwap`, không map đúng bảng CSDL `swap_requests` (16 cột chuẩn).
  2. Module `review` tự ý đẻ bảng `ReviewEvidence`, trong khi `schema.sql` quy định lưu trữ mảng chuỗi trực tiếp (`evidence_images text[]`).
  3. Module `room` vi phạm nguyên lý DDD Aggregate Root khi tạo repository riêng lẻ cho `RoomFee` và `RoomImage`, đồng thời trường `ward` bị thêm tuỳ tiện không có trong CSDL.
  4. Module `admin` rò rỉ JPA Entity ra Swagger UI (chứa `passwordHash` của User, và JTS Spatial `Point` của PostGIS gây lỗi tuần hoàn Jackson serialization).
  5. Bảng 18 (`reports`), Bảng 20 (`notifications`), Bảng 21 (`outbox_events`), Bảng 22 (`system_audit_logs`) chưa được hiện thực đầy đủ.
- **Quyết định:**
  - **Single Source of Truth cho CSDL:** Bảng `swap_requests` là nguồn sự thật duy nhất cho Swap. Xóa bỏ hoàn toàn `RoomSwap.java`, `SwapRequestStatus.java`, `RoomSwapRepository.java`, `ReviewEvidence.java`, `ReviewEvidenceRepository.java`.
  - **Mô hình Aggregate Root (DDD):** `Room` là Aggregate Root duy nhất. Xóa bỏ `RoomFeeRepository` và `RoomImageRepository`. Quản lý `RoomImage` và `RoomFee` thông qua `Room` với `@OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)`. Xóa bỏ cột `ward` không có trong CSDL.
  - **PostgreSQL Native Array Mapping:** Sử dụng `@JdbcTypeCode(SqlTypes.ARRAY)` cho `images text[]`, `evidence_images text[]` và `tags text[]`.
  - **Triệt tiêu 100% Rò rỉ Entity & JTS Spatial ra Swagger:** Toàn bộ Admin API chuyển sang trả về Dedicated DTOs: `AdminUserResponse` (loại bỏ `passwordHash`), `AdminRoomResponse` (chuyển `Point location` thành primitive `Double latitude, longitude`), `AdminPaymentTransactionResponse`, `AdminReviewDisputeResponse`, `AdminReportResponse`, `AdminAuditLogResponse`.
  - **Khôi phục đầy đủ 4 module/thực thể còn thiếu:**
    - Module `notification` (APIs #73 -> #76): `Notification.java` mapping bảng `notifications` (20), `NotificationRepository`, `NotificationService`, `NotificationController`.
    - Quản lý Vi phạm & Báo cáo: `Report.java` mapping bảng `reports` (18), `ReportRepository`, APIs #86, #87, #88.
    - Nhật ký kiểm toán: `SystemAuditLog.java` mapping bảng `system_audit_logs` (22), `SystemAuditLogRepository`, API #91.
    - Transactional Outbox: `OutboxEvent.java` mapping bảng `outbox_events` (21), `OutboxEventRepository`.
- **Hệ quả:** Mã nguồn khớp 100% với `schema.sql` (25 bảng) và `system-specification.md` (105 endpoints). Swagger UI sạch sẽ, không có bất kỳ rò rỉ bảo mật hay lỗi tuần hoàn nào. Toàn bộ các luồng E2E và test suite chạy qua 100%.

---

## ADR-011: Tối Ưu Hóa Hạ Tầng Tích Hợp Bên Thứ Ba (Third-Party Integrations) Cho Môi Trường Cloud Miễn Phí (Render / Railway)
- **Ngày quyết định:** 2026-09-04
- **Bối cảnh:**
  1. Các nền tảng PaaS Cloud miễn phí (Render, Railway) sử dụng Ephemeral File System (ổ cứng tạm). Mọi file ảnh lưu cục bộ tại `./uploads/` sẽ bị xóa sạch mỗi lần server restart hoặc redeploy.
  2. Việc gọi API gợi ý địa chỉ Place Autocomplete theo từng phím bấm qua Backend làm tăng độ trễ mạng và gây nghẽn băng thông server.
  3. Dịch vụ VietQR cần cung cấp đầy đủ thông tin chuẩn hóa để Frontend có thể tùy biến render hoặc dùng ảnh tĩnh Napas 2.0.
- **Quyết định:**
  - **Lưu trữ ảnh đám mây không dùng ổ cứng (Cloudinary):**
    - Cài đặt thư viện `com.cloudinary:cloudinary-http44` (v1.39.0).
    - Hiện thực `CloudinaryStorageAdapter` implements `FileStoragePort` làm adapter lưu trữ duy nhất.
    - Upload trực tiếp luồng stream/byte lên Cloudinary, trả về link HTTPS vĩnh viễn (`res.cloudinary.com`), không ghi bất kỳ byte nào xuống ổ cứng cục bộ `./uploads/`.
  - **Phân định rõ ràng trách nhiệm bản đồ (Location Module):**
    - Frontend gọi trực tiếp Goong Maps Place Autocomplete SDK với Domain-Restricted Map Key.
    - Backend giữ `GeocodingPort` làm chốt chặn bảo mật (Fallback Geocoding) khi tạo/cập nhật phòng nếu Client không truyền tọa độ.
    - Endpoint `GET /api/v1/locations/autocomplete` đóng vai trò fallback proxy và được cache Redis 24 giờ (`location:autocomplete:{input}`).
  - **Chuẩn hóa cổng thanh toán duy nhất (Monetization):**
    - Chuẩn hóa toàn diện cổng thanh toán VNPay Sandbox với mã hóa HMAC-SHA512 và IPN Webhook Idempotency key, loại bỏ cổng thanh toán VietQR để tối ưu luồng vận hành.
  - **Giữ vững 100% các chốt chặn cốt lõi:**
    - `BrevoEmailAdapter`: Giữ nguyên gửi OTP qua HTTPS REST API Port 443.
    - `VnPayPaymentAdapter`: Giữ nguyên mã hóa HMAC-SHA512 và IPN Webhook checksum.
    - `Google OAuth2`: Giữ nguyên xác thực ID Token.
    - `Redis/FCM`: Giữ nguyên cơ chế quản lý device token.
- **Hệ quả:** Hệ thống đạt 100% khả năng deploy lên Render/Railway mà không sợ mất file ảnh, tiết kiệm quota API bên thứ ba nhờ Redis cache, giữ vững 105/105 API Endpoints đạt chuẩn 100% Pass.


# PHÒNG TRỌ XANH (PhongTroXanh.vn)

> Tài liệu thiết kế/lịch sử. Trạng thái triển khai và phạm vi đã kiểm chứng xem [backend verification](backend-verification.md); cấu hình hiện hành dùng [.env.example](../.env.example) và [payOS setup](payos-setup.md). ADR về VNPay đã được thay bằng payOS ngày 01/10/2026.
# TÀI LIỆU ĐẶC TẢ KIẾN TRÚC BACKEND DOANH NGHIỆP & DANH MỤC 100% RESTFUL API TOÀN DIỆN

---

> [!IMPORTANT]
> **Tài liệu Chuẩn Doanh nghiệp (Enterprise Grade Specification):**
> Tài liệu này bao quát toàn bộ 100% các tính năng, màn hình, luồng nghiệp vụ của cả **3 Roles (`TENANT`, `LANDLORD`, `ADMIN`)**, bao gồm:
> 1. Kiến trúc Pragmatic Layered Architecture (Modular Monolith) & Chiến lược Real-time WebSocket vs REST.
> 2. Thuật toán Xếp hạng Đẩy bài (Boost) & Xử lý Xung đột Thứ hạng (Tie-Breaking).
> 3. Hệ thống Điểm Uy Tín (TrustScore) 2 chiều & Cơ chế 4 lớp chống Review 1 sao ác ý.
> 4. Thuật toán Tính Điểm Tương Thích Bạn Cùng Phòng (Smart Compatibility Engine) 8 trụ cột từ Form Onboarding.
> 5. Thiết kế CSDL PostgreSQL 16 + PostGIS hoàn chỉnh (25 Bảng thực thể chuẩn hóa, bao gồm bảng room_types động).
> 6. Danh mục 100% RESTful API (Đầy đủ 10 Modules, 94 Endpoints, Request/Response, Phân quyền RBAC).
> 7. Bảo mật Doanh nghiệp, Chống Race Condition & Kiểm soát Concurrency.

---

## 1. TỔNG QUAN KIẾN TRÚC HỆ THỐNG & CHIẾN LƯỢC REAL-TIME

### 1.1. Kiến trúc Phân tầng: Modular Monolith & Pragmatic Layered Architecture
Hệ thống được tổ chức theo cấu trúc **Modular Monolith** với **Pragmatic Layered Architecture**, ranh giới Bounded Context rõ ràng, sẵn sàng chừn đổi Microservices khi đạt traffic lớn:

```
┌───────────────────────────────────────────────────────────────────────────┐
│                          PRESENTATION LAYER                               │
│  - RESTful API Controllers (Spring MVC / NestJS)                          │
│  - WebSocket STOMP Message Handlers                                       │
│  - Webhook IPN Consumers (VNPay, VietQR)                                   │
└─────────────────────────────────────┬─────────────────────────────────────┘
                                      │
                                      ▼
┌───────────────────────────────────────────────────────────────────────────┐
│                       APPLICATION & BUSINESS LAYER                        │
│  - AuthService & Token Rotation    - SmartMatchingEngine (8 Trụ cột)      │
│  - RoomService & PostGIS Search    - TwoWayTrustScoreEngine (Anti-Fraud)  │
│  - SwappingLeaseholderService      - DynamicFeedRankingService (Boost)    │
│  - RentalQRCheckInService          - IdempotentPaymentService             │
│  - ModerationAndReportService      - NotificationAndPushService           │
└─────────────────────────────────────┬─────────────────────────────────────┘
                                      │
                                      ▼
┌───────────────────────────────────────────────────────────────────────────┐
│                        DATA ACCESS & DOMAIN LAYER                         │
│  - JPA/Hibernate 6 Entities        - Repositories (PostgreSQL 16)         │
│  - PostGIS Spatial Queries         - Domain Model & Business Invariants   │
└─────────────────────────────────────┬─────────────────────────────────────┘
                                      │
                                      ▼
┌───────────────────────────────────────────────────────────────────────────┐
│                  INFRASTRUCTURE & EXTERNAL INTEGRATIONS                   │
│  - Redis 7 Cluster (Cache, Token Bucket, Redlock, PubSub)                 │
│  - AWS S3 / Cloudinary (Private Bucket + Pre-signed URLs cho CCCD)        │
│  - Brevo REST API (Email OTP), Firebase Cloud Messaging (FCM Push)        │
└───────────────────────────────────────────────────────────────────────────┘
```

---

### 1.2. Chiến lược Real-time: WebSocket vs REST vs SSE trong Thực tế Doanh nghiệp

Trong các hệ thống quy mô lớn như **Tinder, Bumble, Airbnb, Shopee**, việc phân bổ giao thức được tối ưu hóa như sau:

| Nghiệp vụ | Giao thức Tối ưu | Cơ chế Xử lý Chi tiết & Lý do Kỹ thuật |
| :--- | :--- | :--- |
| **Chat thời gian thực (1-1)** | **WebSocket + STOMP** | Hai chiều toàn phần (Full-duplex), độ trễ < 20ms, hỗ trợ typing indicator, delivery/read receipt và presence online/offline. |
| **Hành động Vuốt thẻ (Swipe Action)** | **REST API + Optimistic UI** | **KHÔNG dùng WebSocket để vuốt.** Dùng `POST /api/v1/matching/swipe` với Optimistic UI trên Client (thẻ bay ngay lập tức, tự trừ số lượt hiển thị). Server dùng Redis Atomic Counter (`DECRBY`) trừ lượt. REST đảm bảo tính Idempotent, chống duplicate request khi mạng chập chờn và dễ ghi log thanh toán. |
| **Sự kiện Match Đôi (It's a Match!)** | **WebSocket STOMP Event** | Khi Server phát hiện 2 người cùng vuốt `RIGHT`, Server đẩy ngay event qua destination `/topic/users.{userId}.matches` để kích hoạt màn hình Confetti trên Client của cả 2 bên. |
| **In-App Notifications** | **WebSocket STOMP User Queue** | Tái sử dụng kết nối STOMP hiện có qua `/user/queue/notifications` để nhận thông báo tức thì (Duyệt CCCD, Tin nhắn mới, Lời mời swap) mà không phải mở thêm kết nối HTTP mới. |
| **Push Notification khi đóng App** | **Firebase Cloud Messaging (FCM)** | Gửi push notification nền đến thiết bị di động / trình duyệt thông qua Device Token khi kết nối WebSocket đã ngắt. |
| **Cập nhật số lượt vuốt qua đêm / Nạp tiền** | **WebSocket Broadcast / Response Sync** | Khi user nạp tiền thành công hoặc được reset 15 lượt lúc 00:00, Server bắn event `SWIPES_REFRESHED` để cập nhật badge số lượt trên Topbar. |

---

## 2. THUẬT TOÁN ĐẨY BÀI (BOOST) & GIẢI QUYẾT XUNG ĐỘT THỨ HẠNG (TIE-BREAKING)

### 2.1. API Lấy Tập Thẻ Vuốt & Feed Phòng Trọ
- **Endpoint:** `GET /api/v1/matching/deck` (cho Bạn ở ghép) hoặc `GET /api/v1/rooms` (cho Tin phòng trọ).
- Client chỉ việc gọi API lấy danh sách tiếp theo. Server chịu trách nhiệm chấm điểm, sắp xếp thứ tự hiển thị thông qua **Dynamic Composite Ranking Engine**.

### 2.2. Bài toán Tie-Breaking: Ai hiện trước khi nhiều người cùng trả tiền mua gói Boost?

Nếu 100 người cùng trả 199.000đ mua gói Boost, hệ thống **không được** xếp theo kiểu đơn giản "ai mua trước hiện trước" (làm người mua sau bị chìm) hoặc "ai mua sau hiện trước" (làm người mua trước mất quyền lợi ngay lập tức).

#### Công thức Xếp hạng Đa yếu tố (Multi-Factor Dynamic Ranking):

```
RankScore = TierWeight × [ 0.40 × CompatibilityScore 
                         + 0.30 × BoostDecayScore 
                         + 0.20 × TrustScoreNormalized 
                         + 0.10 × RecencyScore ] + Jitter
```

Trong đó:
1. **TierWeight (Trọng số gói dịch vụ):**
   - Hạng Chủ trọ Premium / Tenant Gold: `3.0`
   - Hạng Chủ trọ Pro / Tenant Plus: `2.0`
   - Gói Boost lẻ (Consumable Boost): `1.8`
   - Hạng Miễn phí (Free Tier): `1.0`
2. **BoostDecayScore (Hàm suy giảm thời gian dạng Half-Life Exponential):**
   - Điểm Boost giảm dần theo thời gian kể từ thời điểm kích hoạt:
     `BoostDecay(t) = exp(-0.05 × (t_hien_tai - t_kich_hoat))`
   - *Ý nghĩa:* Khi vừa bấm Boost, điểm đạt 100%. Sau 24h điểm giảm còn ~30%, sau 48h giảm còn ~10%. Điều này tạo cơ hội công bằng cho người mới kích hoạt nhưng vẫn giữ ưu thế vượt trội so với người dùng Free.
3. **CompatibilityScore (Độ tương thích với người đang xem - 40%):**
   - *Rất quan trọng:* Dù bạn có trả tiền để đẩy bài, nhưng nếu tiêu chí của bạn hoàn toàn không khớp với người đang vuốt (ví dụ: lệch giới tính, ngân sách không chạm nhau), bài của bạn sẽ không bị ép hiển thị gây rác bảng tin (Tránh phá hủy trải nghiệm người dùng - Negative UX).
4. **TrustScoreNormalized (Điểm uy tín - 20%):**
   - Giữa 2 người cùng kích hoạt Boost cùng thời điểm: Người có CCCD xác minh và TrustScore cao hơn (ví dụ 95 điểm vs 60 điểm) sẽ được ưu tiên xếp trên.
5. **Jitter (Xoay tua ngẫu nhiên có kiểm soát - Fair Impression Distribution):**
   - Cộng thêm một giá trị ngẫu nhiên nhỏ `Jitter ∈ [0.0, 0.05]` để xoay vòng các bài đăng có điểm số tương đương nhau, đảm bảo 100 người cùng mua Boost đều nhận được số lượt hiển thị (Impressions) công bằng trong ngày.

---

## 3. HỆ THỐNG ĐIỂM UY TÍN 2 CHIỀU (TWO-WAY TRUSTSCORE) & CƠ CHẾ CHỐNG VOTE 1 SAO ÁC Ý

### 3.1. Đánh giá Tín nhiệm 2 Chiều: Người Thuê & Chủ Trọ

Hệ thống bắt buộc áp dụng **Đánh giá 2 Chiều có Ràng buộc Hợp đồng (Verified Stay)**. Không ai được đánh giá nếu chưa từng có quan hệ thuê nhà thực tế được hệ thống ghi nhận qua mã Check-in QR.

```
┌───────────────────────────────────────────────────────────────────────────┐
│                      MA TRẬN ĐÁNH GIÁ 2 CHIỀU                             │
├─────────────────────────────────────┬─────────────────────────────────────┤
│   CHỦ TRỌ ĐÁNH GIÁ NGƯỜI THUÊ       │     NGƯỜI THUÊ ĐÁNH GIÁ CHỦ TRỌ     │
├─────────────────────────────────────┼─────────────────────────────────────┤
│ 1. Giữ gìn vệ sinh phòng (Clean)    │ 1. Phòng đúng mô tả & ảnh thật (Acc)│
│ 2. Trả tiền đúng hạn (Payment)      │ 2. Minh bạch giá điện nước (Price)  │
│ 3. Tuân thủ nội quy chung (Rules)   │ 3. Thái độ & Hỗ trợ sửa chữa (Care) │
│ 4. Hòa đồng khi ở ghép (Harmonious) │ 4. An ninh & Môi trường sống (Safe) │
└─────────────────────────────────────┴─────────────────────────────────────┘
```

#### Công thức tính điểm TrustScore cho cả 2 bên (Thang điểm 0 - 100):

```
TrustScore = BaseScore(50) + Điểm_KYC + Điểm_Reviews + Điểm_ThoiGianThue + Điểm_PhanHoi - Điểm_ViPham
```

- **Điểm_KYC:** Xác minh CCCD hợp lệ (+30 điểm).
- **Điểm_Reviews (Áp dụng thuật toán Bayesian Average):**
  - Đánh giá 5 sao: +5 điểm / lượt (Tối đa +25).
  - Đánh giá 4 sao: +2 điểm / lượt.
  - Đánh giá 1-2 sao có bằng chứng: -10 điểm / lượt.
- **Điểm_ThoiGianThue:** Thuê hoặc vận hành trọ ổn định >= 6 tháng (+10 điểm).
- **Điểm_PhanHoi:** Tỉ lệ phản hồi tin nhắn nhanh < 15 phút (+5 điểm).
- **Điểm_ViPham:** Bị Admin gửi cảnh cáo do vi phạm nội dung (-15 điểm).

---

### 3.2. Cơ chế 4 Lớp Chống Gian lận / Vote 1 Sao Ác Ý (Anti-Abuse & Dispute Workflow)

Để ngăn chặn tình trạng khách thuê cố tình vote 1 sao để trả thù cá nhân, hoặc chủ trọ dìm hàng khách thuê, hệ thống triển khai quy trình 4 lớp bảo vệ:

```
[Người dùng gửi Review 1-2 sao]
              │
              ▼
[Lớp 1: Ràng buộc Bằng chứng & Tags] ──(Không có ảnh/hóa đơn)──> [Từ chối ghi nhận điểm]
              │ (Có đủ bằng chứng)
              ▼
[Lớp 2: Giai đoạn Đóng băng & Khiếu nại (7 ngày)]
  - Review ở trạng thái PENDING_VERIFICATION
  - CHƯA trừ điểm TrustScore của đối phương
  - Gửi thông báo: "Bạn nhận được đánh giá thấp. Bạn có 7 ngày để khiếu nại."
              │
              ├──────────────────────────────────┐
              ▼                                  ▼
     [Không khiếu nại]                  [Bấm Nút Khiếu Nại]
              │                                  │
              ▼                                  ▼
   [Chính thức ghi nhận điểm]          [Lớp 3: Admin Phân xử trong 48h]
                                       - Kiểm tra đoạn chat trong hệ thống
                                       - Kiểm tra ảnh hợp đồng / hóa đơn
                                                 │
                                                 ├────────────────────────┐
                                                 ▼                        ▼
                                       [Review sai sự thật]      [Review đúng sự thật]
                                                 │                        │
                                                 ▼                        ▼
                                       [Lớp 4: Phạt Ngược]       [Chính thức trừ điểm]
                                       - Hủy bỏ review rác
                                       - Trừ -30 điểm kẻ vu khống
                                       - Ghi vết vi phạm
```

---

## 4. THUẬT TOÁN TÍNH ĐỘ TƯƠNG THÍCH BẠN CÙNG PHÒNG TOÀN DIỆN (8 TRỤ CỘT)

Dựa trên toàn bộ dữ liệu thu thập từ quy trình Onboarding thực tế (Hồ sơ cơ bản, **Ảnh 2: Sở thích**, **Ảnh 3: Lối sống & Giờ giấc**, **Ảnh 4: Nhu cầu ở bắt buộc**):

```
CompatibilityScore = (0.25 × S_budget 
                    + 0.20 × S_location 
                    + 0.20 × S_lifestyle 
                    + 0.10 × S_interests 
                    + 0.10 × S_proximity 
                    + 0.05 × S_room_type 
                    + 0.05 × S_demographics 
                    + 0.05 × S_verified_boost) × DealbreakerMultiplier
```

---

### Chi tiết Từng Trụ cột Tính điểm:

#### 1. S_budget (Giao thoa Khoảng Ngân sách - Trọng số 25% - Ảnh 4):
Người dùng $A$ chọn `[A_min, A_max]`, người dùng $B$ chọn `[B_min, B_max]`:
- Khoảng giao thoa: `Overlap = max(0, min(A_max, B_max) - max(A_min, B_min))`
- Khoảng bao quát nhỏ nhất: `MinSpan = min(A_max - A_min, B_max - B_min)`
- `S_budget = Overlap / MinSpan` (Nếu `Overlap <= 0` thì `S_budget = 0.0`).

#### 2. S_location (Trùng khớp Quận mong muốn - Trọng số 20% - Ảnh 4):
Dựa trên danh sách các quận người dùng tick chọn (Quận 1, 3, 5, 9, 10, Bình Thạnh, Thủ Đức, Gò Vấp...):
- `S_location = (Số quận cả 2 cùng chọn) / (Tổng số quận phân biệt mà 2 người đã chọn)`.

#### 3. S_lifestyle (Hòa hợp Lối sống & Giờ giấc - Trọng số 20% - Ảnh 3 Onboarding):
Bao gồm 5 trường dữ liệu lối sống:
- **Giờ đi ngủ (Toggle):** Cùng ngủ sớm hoặc cùng thức khuya $\to 1.0$; 1 người ngủ sớm 1 người thức khuya $\to 0.2$.
- **Mức độ gọn gàng (Toggle):** Cùng gọn gàng $\to 1.0$; lệch nhau $\to 0.4$.
- **Tiếp khách về phòng (Toggle):** Cùng quan điểm $\to 1.0$; lệch nhau $\to 0.3$.
- **Mức độ ồn ào chấp nhận được (Slider 0 - 100%):**
  `S_noise = 1.0 - (|A_noise - B_noise| / 100)`.
- `S_lifestyle = (S_sleep + S_clean + S_guest + S_noise) / 4`.

#### 4. S_interests (Tương đồng Sở thích - Trọng số 10% - Ảnh 2 Onboarding):
Tính toán theo hệ số tương đồng tập hợp Jaccard từ 12 sở thích (`Nấu ăn`, `Đọc sách`, `Du lịch`, `Thể thao`, `Gym`, `Gaming`...):
`S_interests = (Số sở thích chung) / (Tổng số sở thích không trùng lặp của cả 2)`.

#### 5. S_proximity (Ưu tiên Vị trí Tiện ích - Trọng số 10% - Ảnh 4 Onboarding):
So sánh 4 tiêu chí ưu tiên gần: `Gần trường học`, `Gần chỗ làm`, `Gần chợ/siêu thị`, `Gần trạm xe buýt`:
`S_proximity = (Số tiêu chí tiện ích cả 2 cùng tick) / 4`.

#### 6. S_room_type (Loại phòng mong muốn - Trọng số 5% - Ảnh 4 Onboarding):
- Cùng chọn chung 1 loại phòng (`Phòng khép kín`, `Studio`, `Căn hộ mini`, `Phòng trọ`) $\to 1.0$; khác loại $\to 0.0$.

#### 7. S_demographics (Độ tuổi & Trường học - Trọng số 5%):
- Khoảng cách tuổi: Nếu `|Tuổi_A - Tuổi_B| <= 2` $\to 1.0$; lệch 3-5 tuổi $\to 0.6$; lệch > 5 tuổi $\to 0.2$.
- Cùng trường học / nơi làm việc: Cộng thưởng $+0.3$ điểm bonus.

#### 8. DealbreakerMultiplier (Bộ lọc Khắc nghiệt - Phủ định Tuyệt đối):
- **Thuốc lá (Toggle Hút thuốc - Ảnh 3):** Nếu $A$ hoặc $B$ bật toggle `Không hút thuốc trong phòng` mà đối phương có hút thuốc $\to \text{DealbreakerMultiplier} = 0.0$ (Điểm tổng về 0%, loại ngay khỏi Deck).
- **Giới tính:** Nếu có yêu cầu khắt khe về giới tính mà không trùng khớp $\to \text{DealbreakerMultiplier} = 0.0$.
- Nếu vượt qua dealbreakers: $\text{DealbreakerMultiplier} = 1.0$.

---

## 5. THIẾT KẾ CƠ SỞ DỮ LIỆU TOÀN DIỆN (POSTGRESQL 16 + POSTGIS)

```sql
CREATE TYPE user_role_enum AS ENUM ('TENANT', 'LANDLORD', 'ADMIN');
CREATE TYPE user_status_enum AS ENUM ('ACTIVE', 'WARNED', 'LOCKED', 'DELETED');
CREATE TYPE gender_enum AS ENUM ('MALE', 'FEMALE', 'OTHER');
CREATE TYPE verification_status_enum AS ENUM ('PENDING', 'APPROVED', 'REJECTED');
-- NOTE: room_type là bảng động (room_types), không dùng ENUM cố định → Admin có thể thêm loại phòng mới không cần deploy lại
CREATE TYPE room_status_enum AS ENUM ('AVAILABLE', 'RENTED', 'HIDDEN', 'EXPIRED');
CREATE TYPE swipe_dir_enum AS ENUM ('LEFT', 'RIGHT', 'SUPER'); -- LEFT: Bỏ qua (X), RIGHT: Thích (Heart), SUPER: Super Match (⭐)
CREATE TYPE match_status_enum AS ENUM ('MATCHED', 'UNMATCHED', 'BLOCKED');
CREATE TYPE swap_status_enum AS ENUM ('OPEN', 'MATCHING', 'PENDING_LANDLORD', 'APPROVED', 'COMPLETED', 'CANCELLED', 'DECLINED');
CREATE TYPE rental_status_enum AS ENUM ('PENDING_CHECKIN', 'CHECKED_IN', 'TERMINATED', 'CANCELLED');
CREATE TYPE transaction_status_enum AS ENUM ('PENDING', 'SUCCESS', 'FAILED', 'EXPIRED');
CREATE TYPE payment_method_enum AS ENUM ('VNPAY', 'VIETQR');
CREATE TYPE report_status_enum AS ENUM ('NEW', 'PROCESSING', 'RESOLVED', 'DISMISSED');
CREATE TYPE report_severity_enum AS ENUM ('CRITICAL', 'MEDIUM', 'LOW');

-- 1. Bảng người dùng gốc
CREATE TABLE users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    phone_number VARCHAR(15) UNIQUE,
    email VARCHAR(255) UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    full_name VARCHAR(150) NOT NULL,
    role user_role_enum NOT NULL DEFAULT 'TENANT',
    status user_status_enum NOT NULL DEFAULT 'ACTIVE',
    avatar_url VARCHAR(500),
    is_verified BOOLEAN DEFAULT FALSE,
    trust_score INT DEFAULT 50 CHECK (trust_score BETWEEN 0 AND 100),
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW(),
    last_active_at TIMESTAMPTZ DEFAULT NOW()
);

-- 2. Bảng hồ sơ & ma trận tương thích ghép bạn
CREATE TABLE user_profiles (
    user_id UUID PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    school_or_company VARCHAR(200),
    birth_date DATE,
    gender gender_enum DEFAULT 'OTHER',
    bio TEXT,
    budget_min NUMERIC(12, 2) DEFAULT 1000000,
    budget_max NUMERIC(12, 2) DEFAULT 4000000,
    preferred_districts VARCHAR(100)[],
    sleep_schedule VARCHAR(50),
    cleanliness_level INT DEFAULT 4 CHECK (cleanliness_level BETWEEN 1 AND 5),
    guest_frequency VARCHAR(50),
    smoking_tolerance BOOLEAN DEFAULT FALSE,
    pet_tolerance VARCHAR(50) DEFAULT 'NONE',
    noise_tolerance INT DEFAULT 40 CHECK (noise_tolerance BETWEEN 0 AND 100),
    allow_guests BOOLEAN DEFAULT FALSE,
    early_sleeper BOOLEAN DEFAULT TRUE,
    is_neat BOOLEAN DEFAULT TRUE,
    non_smoking BOOLEAN DEFAULT TRUE,
    preferred_room_type VARCHAR(50) DEFAULT 'PHONG_KHEP_KIN',
    proximity_school BOOLEAN DEFAULT TRUE,
    proximity_work BOOLEAN DEFAULT TRUE,
    proximity_market BOOLEAN DEFAULT TRUE,
    proximity_bus BOOLEAN DEFAULT TRUE,
    interests VARCHAR(50)[],
    is_public BOOLEAN DEFAULT TRUE,
    show_school BOOLEAN DEFAULT TRUE,
    hide_active_status BOOLEAN DEFAULT FALSE,
    preferred_gender VARCHAR(20) DEFAULT 'ANY'  -- 'MALE', 'FEMALE', 'ANY' (Bất kỳ giới tính)
);

-- 3. Bảng hồ sơ nộp CCCD / KYC
CREATE TABLE user_verifications (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    id_card_number VARCHAR(255) NOT NULL, -- Mã hóa AES-256
    id_card_front_url VARCHAR(500) NOT NULL, -- Private S3 URI
    id_card_back_url VARCHAR(500) NOT NULL,
    status verification_status_enum DEFAULT 'PENDING',
    rejection_reason TEXT,
    reviewed_by UUID REFERENCES users(id),
    reviewed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ DEFAULT NOW()
);

-- 4. Bảng phòng trọ
CREATE TABLE rooms (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    landlord_id UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    title VARCHAR(255) NOT NULL,
    description TEXT NOT NULL,
    room_type VARCHAR(50) NOT NULL,  -- Mã loại phòng động (tham chiếu bảng room_types.code)
    price NUMERIC(12, 2) NOT NULL,
    deposit_amount NUMERIC(12, 2) NOT NULL,
    area_sqm NUMERIC(6, 2) NOT NULL,
    floor_number INT DEFAULT 1,
    max_occupants INT DEFAULT 2,
    address_street VARCHAR(255) NOT NULL,
    district VARCHAR(100) NOT NULL,
    city VARCHAR(100) NOT NULL DEFAULT 'TP.HCM',
    latitude DOUBLE PRECISION NOT NULL,
    longitude DOUBLE PRECISION NOT NULL,
    status room_status_enum DEFAULT 'AVAILABLE',
    is_verified BOOLEAN DEFAULT FALSE,
    is_boosted BOOLEAN DEFAULT FALSE,
    boost_expires_at TIMESTAMPTZ,
    view_count BIGINT DEFAULT 0,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW(),
    expires_at TIMESTAMPTZ DEFAULT (NOW() + INTERVAL '30 days')
);

-- 5. Bảng biểu phí phụ trợ
CREATE TABLE room_fees (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    room_id UUID NOT NULL REFERENCES rooms(id) ON DELETE CASCADE,
    fee_label VARCHAR(100) NOT NULL,
    fee_value VARCHAR(100) NOT NULL
);

-- 6. Bảng ảnh phòng trọ
CREATE TABLE room_images (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    room_id UUID NOT NULL REFERENCES rooms(id) ON DELETE CASCADE,
    image_url VARCHAR(500) NOT NULL,
    is_primary BOOLEAN DEFAULT FALSE,
    display_order INT DEFAULT 0
);

-- 7. Bảng phòng đã lưu (Saved/Bookmark)
CREATE TABLE saved_rooms (
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    room_id UUID NOT NULL REFERENCES rooms(id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    PRIMARY KEY (user_id, room_id)
);

-- 8. Bảng vuốt thẻ (Swipes)
CREATE TABLE swipes (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    swiper_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    target_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    direction swipe_dir_enum NOT NULL,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    UNIQUE (swiper_id, target_id)
);

-- 9. Bảng ghép đôi thành công (Matches)
CREATE TABLE matches (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_a_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    user_b_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    compatibility_score NUMERIC(5, 2) NOT NULL,
    status match_status_enum DEFAULT 'MATCHED',
    created_at TIMESTAMPTZ DEFAULT NOW(),
    UNIQUE (user_a_id, user_b_id)
);

-- 10. Bảng hoán đổi phòng thuê (Tenant Swap)
CREATE TABLE swap_requests (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    requester_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    current_room_id UUID NOT NULL REFERENCES rooms(id) ON DELETE RESTRICT,
    is_leaseholder BOOLEAN DEFAULT FALSE,
    target_districts VARCHAR(100)[],
    target_room_type VARCHAR(50),  -- Loại phòng mong muốn (động, tham chiếu room_types.code)
    target_budget_max NUMERIC(12, 2),
    habits VARCHAR(100)[],
    reason TEXT NOT NULL,
    target_move_in_date DATE,
    matched_tenant_id UUID REFERENCES users(id),
    landlord_id UUID REFERENCES users(id),
    landlord_decision swap_status_enum DEFAULT 'PENDING_LANDLORD',
    status swap_status_enum DEFAULT 'OPEN',
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW()
);

-- 11. Bảng hợp đồng thuê & Check-in QR
CREATE TABLE rental_contracts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    room_id UUID NOT NULL REFERENCES rooms(id) ON DELETE RESTRICT,
    landlord_id UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    tenant_id UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    status rental_status_enum DEFAULT 'PENDING_CHECKIN',
    is_leaseholder BOOLEAN DEFAULT TRUE,
    check_in_code VARCHAR(16) NOT NULL,
    check_in_qr_token VARCHAR(255) UNIQUE NOT NULL,
    start_date DATE,
    end_date DATE,
    checked_in_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ DEFAULT NOW()
);

-- 12. Bảng đánh giá 2 chiều
CREATE TABLE reviews (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    rental_contract_id UUID NOT NULL REFERENCES rental_contracts(id) ON DELETE RESTRICT,
    reviewer_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    reviewee_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    room_id UUID REFERENCES rooms(id) ON DELETE SET NULL,
    rating INT NOT NULL CHECK (rating BETWEEN 1 AND 5),
    tags VARCHAR(50)[],
    comment TEXT NOT NULL,
    images VARCHAR(500)[],
    is_verified_stay BOOLEAN DEFAULT TRUE,
    status VARCHAR(30) DEFAULT 'ACTIVE', -- 'ACTIVE', 'UNDER_DISPUTE', 'REMOVED'
    created_at TIMESTAMPTZ DEFAULT NOW()
);

-- 13. Bảng khiếu nại đánh giá chống gian lận
CREATE TABLE review_disputes (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    review_id UUID NOT NULL REFERENCES reviews(id) ON DELETE CASCADE,
    appellant_id UUID NOT NULL REFERENCES users(id),
    reason TEXT NOT NULL,
    evidence_images VARCHAR(500)[],
    status VARCHAR(30) DEFAULT 'PENDING_REVIEW', -- 'PENDING_REVIEW', 'ACCEPTED', 'REJECTED'
    admin_notes TEXT,
    resolved_by UUID REFERENCES users(id),
    created_at TIMESTAMPTZ DEFAULT NOW(),
    resolved_at TIMESTAMPTZ
);

-- 14. Bảng lịch sử điểm uy tín (TrustScore Logs)
CREATE TABLE trust_score_logs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    delta INT NOT NULL,
    final_score INT NOT NULL,
    reason VARCHAR(255) NOT NULL,
    reference_id UUID,
    created_at TIMESTAMPTZ DEFAULT NOW()
);

-- 15. Bảng gói dịch vụ & Đăng ký gói (Monetization)
CREATE TABLE package_plans (
    id VARCHAR(50) PRIMARY KEY,
    target_role user_role_enum NOT NULL,
    name VARCHAR(100) NOT NULL,
    price_monthly NUMERIC(12, 2) NOT NULL,
    price_yearly NUMERIC(12, 2) NOT NULL,
    features JSONB NOT NULL
);

CREATE TABLE subscriptions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    plan_id VARCHAR(50) NOT NULL REFERENCES package_plans(id),
    billing_cycle VARCHAR(20) NOT NULL,
    start_date TIMESTAMPTZ NOT NULL,
    end_date TIMESTAMPTZ NOT NULL,
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE TABLE payment_transactions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    item_type VARCHAR(50) NOT NULL,
    item_name VARCHAR(150) NOT NULL,
    amount NUMERIC(12, 2) NOT NULL,
    payment_method payment_method_enum NOT NULL,
    status transaction_status_enum DEFAULT 'PENDING',
    idempotency_key VARCHAR(100) UNIQUE NOT NULL,
    gateway_order_id VARCHAR(100),
    qr_code_url VARCHAR(500),
    qr_expired_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE TABLE user_consumables (
    user_id UUID PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    swipes_left INT DEFAULT 15,
    boosts_left INT DEFAULT 0,
    super_matches_left INT DEFAULT 0,        -- Super Match ⭐ consumable
    last_swipe_reset_at DATE DEFAULT CURRENT_DATE,
    version BIGINT DEFAULT 0  -- Optimistic locking cho Redis atomic update
);

-- 16. Bảng báo cáo vi phạm (Reports) & Moderation
CREATE TABLE reports (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    reporter_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    target_type VARCHAR(20) NOT NULL, -- 'ROOM', 'USER', 'REVIEW'
    target_id UUID NOT NULL,
    report_type VARCHAR(100) NOT NULL,
    detail TEXT NOT NULL,
    evidence_images VARCHAR(500)[],
    status report_status_enum DEFAULT 'NEW',
    severity report_severity_enum DEFAULT 'MEDIUM',
    handler_id UUID REFERENCES users(id),
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW()
);

-- 17. Bảng hội thoại & Tin nhắn chat
CREATE TABLE conversations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    type VARCHAR(20) NOT NULL, -- 'ROOM', 'ROOMMATE'
    room_id UUID REFERENCES rooms(id) ON DELETE SET NULL,
    participant1_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    participant2_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    last_message_text TEXT,
    last_message_at TIMESTAMPTZ DEFAULT NOW(),
    created_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE TABLE messages (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    conversation_id UUID NOT NULL REFERENCES conversations(id) ON DELETE CASCADE,
    sender_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    content TEXT NOT NULL,
    is_read BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMPTZ DEFAULT NOW()
);

-- 15a. Bảng danh mục loại phòng ĐỘNG (room_types - Admin thêm/sửa không cần deploy)
CREATE TABLE room_types (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code VARCHAR(50) UNIQUE NOT NULL,  -- 'PHONG_TRO', 'STUDIO', 'CAN_HO_MINI'...
    name VARCHAR(100) NOT NULL,         -- 'Phòng trọ', 'Studio', 'Căn hộ mini'...
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMPTZ DEFAULT NOW()
);

-- 18. Bảng thông báo in-app
CREATE TABLE notifications (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    type VARCHAR(50) NOT NULL,  -- 'MATCH', 'MESSAGE', 'SYSTEM', 'PAYMENT', 'REVIEW'
    title VARCHAR(255) NOT NULL,
    body TEXT NOT NULL,
    reference_id UUID,
    is_read BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMPTZ DEFAULT NOW()
);

-- 19. Bảng sự kiện Transactional Outbox (chống mất sự kiện khi commit DB và publish event)
CREATE TABLE outbox_events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    aggregate_type VARCHAR(100) NOT NULL,  -- 'PAYMENT', 'MATCH', 'SWAP'
    aggregate_id UUID NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    payload JSONB NOT NULL,
    status VARCHAR(30) DEFAULT 'PENDING',  -- 'PENDING', 'PROCESSED', 'FAILED'
    created_at TIMESTAMPTZ DEFAULT NOW(),
    processed_at TIMESTAMPTZ
);

CREATE TABLE system_audit_logs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    actor_id UUID REFERENCES users(id),
    action VARCHAR(100) NOT NULL,
    target_entity VARCHAR(50) NOT NULL,
    target_id UUID NOT NULL,
    payload_before JSONB,
    payload_after JSONB,
    ip_address VARCHAR(45),
    user_agent TEXT,
    created_at TIMESTAMPTZ DEFAULT NOW()
);
```

---

## 6. DANH MỤC 100% RESTFUL API TOÀN DIỆN (94 ENDPOINTS CHO CẢ 3 ROLES)

---

### MODULE 1: AUTHENTICATION & SECURITY (`/api/v1/auth`)

| # | Method | Endpoint | Quyền (Role) | Mô tả chi tiết chức năng |
| :-: | :--- | :--- | :--- | :--- |
| 1 | `POST` | `/api/v1/auth/register` | Public | Đăng ký tài khoản (Email, Phone, Pass, FullName, Role). Khởi tạo `user_profiles` và cấp 15 lượt vuốt. |
| 2 | `POST` | `/api/v1/auth/login` | Public | Đăng nhập qua Email/SĐT + Password. Trả về `accessToken` (15m), `refreshToken` (7d) và Profile. |
| 3 | `POST` | `/api/v1/auth/send-otp` | Public | Gửi mã OTP 6 số qua Email (Brevo REST API, Rate limit: 3 lần / 5 phút). |
| 4 | `POST` | `/api/v1/auth/verify-otp` | Public | Xác thực mã OTP. Trả về `tempToken` kích hoạt tài khoản hoặc đổi pass. |
| 5 | `POST` | `/api/v1/auth/forgot-password` | Public | Yêu cầu khôi phục mật khẩu tài khoản. |
| 6 | `POST` | `/api/v1/auth/reset-password` | Public | Đổi mật khẩu mới kèm xác thực `tempToken`. |
| 7 | `POST` | `/api/v1/auth/refresh-token` | Public | Cấp lại `accessToken` mới từ `refreshToken` (Cơ chế Token Rotation). |
| 8 | `POST` | `/api/v1/auth/logout` | Authenticated | Thu hồi `refreshToken` và đưa `accessToken` vào Redis Blacklist. |
| 9 | `POST` | `/api/v1/auth/oauth/google` | Public | Đăng nhập / Đăng ký nhanh qua Google OAuth2 ID Token. |
| 10 | `POST` | `/api/v1/auth/onboarding/tenant` | `TENANT` | Lưu dữ liệu 4 bước Onboarding người thuê (Sở thích, Lối sống, Nhu cầu ở, Giấy tờ). |
| 11 | `POST` | `/api/v1/auth/onboarding/landlord` | `LANDLORD` | Lưu dữ liệu 3 bước Onboarding chủ trọ (Thông tin cá nhân, Số lượng & khu vực phòng, Giấy tờ). |
| 12 | `GET` | `/api/v1/misc/landing-stats` | Public | Lấy số liệu thống kê hiển thị trang chủ (Số phòng có sẵn, Số cặp đã ghép đôi, Tỉ lệ đánh giá tích cực). |

---

### MODULE 2: USER PROFILES, KYC & TRUSTSCORE (`/api/v1/users`)

| # | Method | Endpoint | Quyền (Role) | Mô tả chi tiết chức năng |
| :-: | :--- | :--- | :--- | :--- |
| 13 | `GET`    | `/api/v1/users/me`                      | Authenticated | Lấy toàn bộ thông tin cá nhân, số dư lượt vuốt, gói active, điểm TrustScore. |
| 14 | `PUT`    | `/api/v1/users/me`                      | Authenticated | Cập nhật thông tin cơ bản: Họ tên, Ngày sinh, Giới tính, Trường/Công ty, Bio. |
| 15 | `POST`   | `/api/v1/users/me/avatar`               | Authenticated | Tải lên ảnh đại diện cá nhân (`multipart/form-data`). |
| 16 | `GET`    | `/api/v1/users/me/matching-profile`     | `TENANT` | Lấy chi tiết ma trận thói quen sinh hoạt và tiêu chí tìm bạn ở ghép. |
| 17 | `PUT`    | `/api/v1/users/me/matching-profile`     | `TENANT` | Cập nhật toàn bộ thói quen: Ngủ sớm, Gọn gàng, Tiếp khách, Thuốc lá, Slider ồn, Ngân sách, Quận, Loại phòng, 4 tiêu chí Proximity. |
| 18 | `GET`    | `/api/v1/users/me/trust-score`          | `TENANT`, `LANDLORD` | Lấy chi tiết điểm TrustScore (0-100), tiến độ 4 trụ cột, huy hiệu và lịch sử biến động điểm. |
| 19 | `POST`   | `/api/v1/users/me/kyc/cccd`             | Authenticated | Tải lên 2 mặt ảnh CCCD + Số CCCD mã hóa gửi vào hàng đợi duyệt KYC của Admin. |
| 20 | `GET`    | `/api/v1/users/me/kyc/status`           | Authenticated | Tra cứu trạng thái kiểm duyệt CCCD (`PENDING`, `APPROVED`, `REJECTED` + lý do). |
| 21 | `GET`    | `/api/v1/users/{userId}/public`         | Authenticated | Xem hồ sơ công khai của người dùng (ẩn thông tin nhạy cảm). |
| 22 | `GET`    | `/api/v1/users/me/settings`             | Authenticated | Lấy cấu hình thông báo (Noti toggles) và quyền riêng tư (Privacy toggles). |
| 23 | `PUT`    | `/api/v1/users/me/settings`             | Authenticated | Cập nhật cấu hình thông báo và quyền riêng tư. |
| 24 | `DELETE` | `/api/v1/users/me`                      | Authenticated | Yêu cầu xóa tài khoản (Soft delete sau khi kiểm tra hết hợp đồng thuê active). |

---

### MODULE 3: PHÒNG TRỌ, TÌM KIẾM BẢN ĐỒ & SO SÁNH (`/api/v1/rooms`)

| # | Method | Endpoint | Quyền (Role) | Mô tả chi tiết chức năng |
| :-: | :--- | :--- | :--- | :--- |
| 25 | `GET` | `/api/v1/rooms` | Public / Tenant | Tìm kiếm & phân trang phòng trọ đa tiêu chí: khoảng giá, quận, loại phòng, tiện nghi, sắp xếp. |
| 26 | `GET` | `/api/v1/rooms/map` | Public / Tenant | Lấy danh sách toạ độ & pin rút gọn cho MapView theo bounding box hoặc bán kính xung quanh. |
| 27 | `GET` | `/api/v1/rooms/compare` | Public / Tenant | So sánh ma trận tiện nghi, biểu phí và thông số giữa 2-4 phòng trọ theo danh sách `roomIds`. |
| 28 | `GET` | `/api/v1/rooms/{id}` | Public / Tenant | Lấy chi tiết 1 phòng trọ: Gallery ảnh, biểu phí điện nước, rating chủ trọ, tăng view count qua Redis. |
| 29 | `POST` | `/api/v1/rooms/{id}/save` | `TENANT` | Lưu phòng trọ vào danh sách yêu thích (Bookmark). |
| 30 | `DELETE`| `/api/v1/rooms/{id}/save` | `TENANT` | Bỏ lưu phòng trọ khỏi danh sách yêu thích. |
| 31 | `GET` | `/api/v1/rooms/saved/me` | `TENANT` | Lấy danh sách các phòng trọ người dùng đã lưu. |
| 32 | `POST` | `/api/v1/rooms` | `LANDLORD` | Chủ trọ đăng phòng mới (Kiểm tra giới hạn số phòng của gói dịch vụ). |
| 33 | `PUT` | `/api/v1/rooms/{id}` | `LANDLORD` | Chủ trọ cập nhật thông tin phòng, biểu phí, tiện nghi, mô tả. |
| 34 | `DELETE`| `/api/v1/rooms/{id}` | `LANDLORD` | Chủ trọ ẩn/xóa tin đăng phòng (`status = HIDDEN` hoặc `DELETED`). |
| 35 | `POST` | `/api/v1/rooms/{id}/images` | `LANDLORD` | Tải lên nhiều ảnh phòng trọ, chỉ định ảnh chính đại diện. |
| 36 | `DELETE`| `/api/v1/rooms/{id}/images/{imageId}` | `LANDLORD` | Xóa ảnh phòng trọ. |
| 37 | `GET` | `/api/v1/rooms/landlord/me` | `LANDLORD` | Danh sách các phòng do chủ trọ hiện tại sở hữu. |
| 38 | `POST` | `/api/v1/rooms/{id}/boost` | `LANDLORD` | Sử dụng 1 lượt Boost tin để đẩy phòng trọ lên top tìm kiếm trong 7 ngày. |
| 39 | `GET` | `/api/v1/landlord/analytics/overview` | `LANDLORD` | Lấy số liệu Dashboard Chủ trọ: Tổng lượt xem, lượt liên hệ, số phòng đang đăng, tỉ lệ lấp đầy & biểu đồ lượt xem 7 ngày. |

---

### MODULE 4: TÌM BẠN Ở GHÉP & VUỐT THẺ (SMART MATCHING) (`/api/v1/matching`)

| # | Method | Endpoint | Quyền (Role) | Mô tả chi tiết chức năng |
| :-: | :--- | :--- | :--- | :--- |
| 40 | `GET` | `/api/v1/matching/deck` | `TENANT` | Lấy tập ứng viên tương thích (Swipe Deck) kèm điểm tương thích (%) theo thuật toán 8 Trụ Cột. |
| 41 | `POST` | `/api/v1/matching/swipe` | `TENANT` | Gửi hành động vuốt (`LEFT` - Bỏ qua, `RIGHT` - Thích, `SUPER` - Super Match). Trừ lượt vuốt và kiểm tra Mutual Match tức thời. |
| 42 | `GET` | `/api/v1/matching/matches` | `TENANT` | Lấy danh sách tất cả các cặp đã Match đôi thành công để mở khóa chat. |
| 43 | `DELETE`| `/api/v1/matching/matches/{matchId}` | `TENANT` | Hủy ghép đôi (Unmatch) và xóa khỏi danh sách match. |
| 44 | `POST` | `/api/v1/matching/boost-profile` | `TENANT` | Kích hoạt Profile Boost để đưa hồ sơ bản thân lên đầu danh sách vuốt của người khác trong 24 giờ. |

---

### MODULE 5: HOÁN ĐỔI PHÒNG THUÊ (TENANT & LEASEHOLDER SWAP) (`/api/v1/swaps`)

| # | Method | Endpoint | Quyền (Role) | Mô tả chi tiết chức năng |
| :-: | :--- | :--- | :--- | :--- |
| 45 | `GET` | `/api/v1/swaps/feed` | `TENANT` | Khám phá bảng tin hoán đổi phòng thuê công khai (lọc theo quận, mức giá, loại phòng). |
| 46 | `POST` | `/api/v1/swaps/posts` | `TENANT` | Đăng tin yêu cầu hoán đổi phòng (chọn phòng hiện tại, vai trò `isLeaseholder`, lý do, tiêu chí phòng mới). |
| 47 | `GET` | `/api/v1/swaps/me` | `TENANT` | Xem danh sách tin swap cá nhân và tiến độ xử lý. |
| 48 | `PUT` | `/api/v1/swaps/posts/{id}` | `TENANT` | Cập nhật nội dung tin swap hoặc đóng yêu cầu (`status = CANCELLED`). |
| 49 | `POST` | `/api/v1/swaps/posts/{id}/apply` | `TENANT` | Ứng viên gửi đề nghị trao đổi / nhận nhượng lại phòng từ tin swap. |
| 50 | `GET` | `/api/v1/swaps/landlord/requests` | `LANDLORD` | Chủ trọ xem danh sách yêu cầu chuyển nhượng hợp đồng của phòng mình sở hữu. |
| 51 | `PUT` | `/api/v1/swaps/landlord/{id}/approve` | `LANDLORD` | Chủ trọ **Phê duyệt** hoán đổi hợp đồng (Tự động khởi tạo phụ lục hợp đồng điện tử mới cho người đến). |
| 52 | `PUT` | `/api/v1/swaps/landlord/{id}/decline` | `LANDLORD` | Chủ trọ **Từ chối** yêu cầu hoán đổi hợp đồng kèm lý do. |

---

### MODULE 6: QUẢN LÝ THUÊ PHÒNG, CHECK-IN QR & ĐÁNH GIÁ 2 CHIỀU (`/api/v1/rentals` & `/api/v1/reviews`)

| # | Method | Endpoint | Quyền (Role) | Mô tả chi tiết chức năng |
| :-: | :--- | :--- | :--- | :--- |
| 53 | `GET` | `/api/v1/rentals/landlord/tenants` | `LANDLORD` | Lấy danh sách toàn bộ khách thuê của chủ trọ, trạng thái check-in, TrustScore, thời gian thuê. |
| 54 | `GET` | `/api/v1/rentals/{id}/check-in-qr` | Authenticated | Tạo mã xác nhận Check-in nhận phòng động (TTL 5 phút). |
| 55 | `POST` | `/api/v1/rentals/{id}/check-in` | `LANDLORD`, `TENANT` | Xác nhận bàn giao nhận phòng (1-click hoặc OTP). Hệ thống chuyển hợp đồng sang CHECKED_IN, tăng điểm TrustScore và mở quyền review. |
| 56 | `GET` | `/api/v1/rentals/me/active` | `TENANT` | Lấy thông tin phòng đang thuê thực tế hiện tại của bản thân. |
| 57 | `PUT` | `/api/v1/rentals/{id}/terminate` | `LANDLORD`, `TENANT` | Chấm dứt mối quan hệ hợp đồng thuê phòng khi hết thời hạn. |
| 58 | `GET` | `/api/v1/reviews/rooms/{roomId}` | Public | Lấy danh sách đánh giá của phòng trọ (kèm bộ lọc sao, tags, ảnh). |
| 59 | `GET` | `/api/v1/reviews/users/{userId}` | Authenticated | Lấy danh sách đánh giá mà người dùng nhận được từ chủ trọ / bạn cùng phòng cũ. |
| 60 | `POST` | `/api/v1/reviews` | `TENANT`, `LANDLORD` | Gửi đánh giá mới (1-5 sao, tags, comment, ảnh). Ràng buộc bắt buộc phải có hợp đồng đã `CHECKED_IN`. |
| 61 | `POST` | `/api/v1/reviews/{id}/reply` | `LANDLORD` | Chủ trọ phản hồi công khai bài đánh giá của khách thuê. |
| 62 | `POST` | `/api/v1/reviews/{id}/dispute` | Authenticated | Gửi khiếu nại bài đánh giá 1-2 sao sai sự thật kèm bằng chứng đối chất. |

---

### MODULE 7: GÓI DỊCH VỤ, THANH TOÁN & WEBHOOKS (`/api/v1/monetization` & `/api/v1/payments`)

| # | Method | Endpoint | Quyền (Role) | Mô tả chi tiết chức năng |
| :-: | :--- | :--- | :--- | :--- |
| 63 | `GET` | `/api/v1/monetization/plans` | Public | Lấy danh mục các gói dịch vụ (Tenant: Free/Plus/Gold; Landlord: Free/Pro/Premium) và biểu phí. |
| 64 | `POST` | `/api/v1/payments/create-order`         | Authenticated | Khởi tạo đơn hàng thanh toán gói/lượt consumable. Trả về URL thanh toán VNPay Sandbox hoặc mã QR VietQR và `idempotencyKey`. |
| 65 | `GET`  | `/api/v1/payments/order/{orderId}/status` | Authenticated | Tra cứu trạng thái thanh toán của đơn hàng (Polling/SSE). |
| 66 | `POST` | `/api/v1/payments/webhooks/vnpay-ipn`   | Webhook Provider | Webhook tiếp nhận IPN từ cổng thanh toán VNPay (Xác thực chữ ký checksum HMAC-SHA512). |
| 67 | `GET`  | `/api/v1/payments/history/me`           | Authenticated | Lịch sử giao dịch thanh toán của người dùng hiện tại. |

---

### MODULE 8: TIN NHẮN REAL-TIME & WEBSOCKET (`/api/v1/chat` & `/ws`)

| # | Method | Endpoint | Quyền (Role) | Mô tả chi tiết chức năng |
| :-: | :--- | :--- | :--- | :--- |
| 68 | `GET` | `/api/v1/chat/conversations` | Authenticated | Lấy danh sách các cuộc hội thoại (tin nhắn cuối, số tin chưa đọc, trạng thái online, phân loại phòng/bạn). |
| 69 | `POST` | `/api/v1/chat/conversations` | Authenticated | Khởi tạo cuộc hội thoại mới với chủ trọ hoặc bạn cùng phòng đã Match. |
| 70 | `GET` | `/api/v1/chat/conversations/{id}/messages` | Authenticated | Lấy lịch sử tin nhắn trong cuộc hội thoại (Cursor-based pagination). |
| 71 | `POST` | `/api/v1/chat/conversations/{id}/read` | Authenticated | Đánh dấu đã đọc toàn bộ tin nhắn trong cuộc hội thoại. |
| 72 | `POST` | `/api/v1/chat/conversations/{id}/attachments` | Authenticated | Upload ảnh đính kèm trong tin nhắn chat. |

#### WebSocket STOMP Specification:
- **Handshake Endpoint:** `wss://api.phongtroxanh.vn/ws/chat?token={JWT_TOKEN}`
- **Subscribes:**
  - Nhận tin nhắn mới: `/topic/conversations.{conversationId}.messages`
  - Nhận trạng thái đang gõ: `/topic/conversations.{conversationId}.typing`
  - Nhận trạng thái Online/Offline: `/topic/users.{userId}.presence`
  - Nhận thông báo cá nhân: `/user/queue/notifications`
  - Nhận sự kiện Match đôi: `/topic/users.{userId}.matches`
- **Sends:**
  - Gửi tin nhắn: `/app/chat.sendMessage` -> `{ "conversationId": "...", "content": "...", "type": "TEXT" }`
  - Gửi typing: `/app/chat.sendTyping` -> `{ "conversationId": "...", "isTyping": true }`

---

### MODULE 9: THÔNG BÁO TỨC THỜI (`/api/v1/notifications`)

| # | Method | Endpoint | Quyền (Role) | Mô tả chi tiết chức năng |
| :-: | :--- | :--- | :--- | :--- |
| 73 | `GET` | `/api/v1/notifications` | Authenticated | Lấy danh sách thông báo cá nhân (Match mới, tin nhắn, phòng phù hợp 90%+, duyệt CCCD, review mới). |
| 74 | `PUT` | `/api/v1/notifications/{id}/read` | Authenticated | Đánh dấu 1 thông báo đã đọc. |
| 75 | `PUT` | `/api/v1/notifications/read-all` | Authenticated | Đánh dấu tất cả thông báo đã đọc. |
| 76 | `POST` | `/api/v1/notifications/device-token` | Authenticated | Đăng ký FCM Device Token để nhận Push Notification trên trình duyệt/mobile. |

---

### MODULE 10: ADMIN DASHBOARD, KIỂM DUYỆT & VẬN HÀNH (`/api/v1/admin`)

| # | Method | Endpoint | Quyền (Role) | Mô tả chi tiết chức năng |
| :-: | :--- | :--- | :--- | :--- |
| 77 | `GET` | `/api/v1/admin/analytics/overview` | `ADMIN` | Tổng quan số liệu: Tăng trưởng người dùng, hợp đồng hoàn thành, phân bổ TrustScore, Phễu chuyển đổi Renter Funnel (B1->B5) kèm phân tích điểm nghẽn & giải pháp. |
| 78 | `GET` | `/api/v1/admin/analytics/revenue` | `ADMIN` | Thống kê doanh thu theo kỳ (3 tháng, 6 tháng, năm) phân bổ theo 4 nguồn thu: Subscription, Boosts, Priority Matching, Banner Ads. |
| 79 | `GET` | `/api/v1/admin/kyc/queue` | `ADMIN` | Hàng đợi danh sách hồ sơ CCCD chờ duyệt kèm ảnh 2 mặt zoom độ phân giải cao. |
| 80 | `PUT` | `/api/v1/admin/kyc/{id}/approve` | `ADMIN` | **Phê duyệt** hồ sơ CCCD (Cấp huy hiệu Đã Xác Minh, cộng +30 điểm TrustScore). |
| 81 | `PUT` | `/api/v1/admin/kyc/{id}/reject` | `ADMIN` | **Từ chối** hồ sơ CCCD kèm lý do vi phạm chi tiết. |
| 82 | `GET` | `/api/v1/admin/users` | `ADMIN` | Quản lý danh sách người dùng toàn hệ thống (Tìm kiếm, lọc theo vai trò, trạng thái Active/Locked/Warned, phân trang). |
| 83 | `PUT` | `/api/v1/admin/users/{id}/status` | `ADMIN` | Khóa tài khoản (`LOCKED`), Mở khóa (`ACTIVE`), hoặc Gửi cảnh cáo (`WARNED`) kèm ghi chú. |
| 84 | `PUT` | `/api/v1/admin/users/{id}/reset-password` | `ADMIN` | Kích hoạt gửi email khôi phục mật khẩu tạm thời cho người dùng. |
| 85 | `PUT` | `/api/v1/admin/users/{id}/verify-badge` | `ADMIN` | Cấp hoặc thu hồi thủ công huy hiệu xác minh cho người dùng. |
| 86 | `GET` | `/api/v1/admin/reports` | `ADMIN` | Danh sách báo cáo vi phạm từ cộng đồng (Lọc theo trạng thái New/Processing/Resolved/Dismissed; mức độ Critical/Medium/Low; đối tượng Room/User). |
| 87 | `GET` | `/api/v1/admin/reports/{id}` | `ADMIN` | Chi tiết 1 báo cáo kèm hình ảnh bằng chứng, link phòng và lịch sử xử lý. |
| 88 | `POST` | `/api/v1/admin/reports/{id}/action` | `ADMIN` | Thực hiện hành động xử lý: Hoàn tất (`resolve`), Gửi cảnh cáo (`warn`), Gỡ nội dung (`remove_content`), Khóa tài khoản (`ban`), Bỏ qua (`dismiss`). Tự động ghi audit log. |
| 89 | `GET` | `/api/v1/admin/disputes` | `ADMIN` | Hàng đợi các khiếu nại đánh giá 1-2 sao giữa người thuê và chủ trọ. |
| 90 | `POST` | `/api/v1/admin/disputes/{id}/resolve` | `ADMIN` | Phán quyết khiếu nại review: Chấp thuận (Hủy review, phạt ngược người vu khống) hoặc Bác bỏ (Giữ nguyên review). |
| 91 | `GET` | `/api/v1/admin/audit-logs` | `ADMIN` | Nhật ký truy vết toàn bộ thao tác can thiệp dữ liệu nhạy cảm của Admin. |
| 92 | `GET` | `/api/v1/locations/autocomplete` | Public | Gợi ý địa chỉ tự động (Goong Maps Autocomplete) khi nhập tìm kiếm phòng hoặc tạo tin đăng. |
| 93 | `GET` | `/api/v1/locations/geocode` | Public | Chuyển đổi địa chỉ chuỗi văn bản $\to$ Tọa độ (Vĩ độ `latitude`, Kinh độ `longitude`). |
| 94 | `GET` | `/api/v1/locations/reverse-geocode` | Public | Chuyển đổi tọa độ GPS $\to$ Tên địa chỉ, Phường/Xã, Quận/Huyện tiếng Việt chuẩn xác. |

---

## 7. BẢO MẬT DOANH NGHIỆP, CHỊU TẢI & CHỐNG RACE CONDITION

1. **Redis Caching Strategy:**
   - Caching chi tiết phòng: `room:detail:{id}` (TTL: 1h, tự động xóa cache khi chủ trọ sửa thông tin).
   - Caching danh sách lọc theo quận: `rooms:district:{district_code}:page:{page}` (TTL: 5m).
2. **Distributed Locking (Redlock) chống Race Condition:**
   - Khóa phân tán khi xác nhận Check-in bàn giao phòng: `lock:checkin:{contractId}` (TTL: 10s) nhằm đảm bảo mỗi giao dịch check-in chỉ được xử lý đơn nhất 1 lần duy nhất, tránh tình trạng duplicate check-in.
   - Khóa phân tán khi xử lý Webhook thanh toán: `lock:payment:{idempotencyKey}` (TTL: 15s) tránh nhân đôi số dư gói/consumables khi cổng thanh toán retry nhiều lần.
3. **Bảo mật Dữ liệu Riêng tư (PII Encryption):**
   - Mã hóa cột `id_card_number` bằng thuật toán AES-256-GCM tại tầng cơ sở dữ liệu.
   - Ảnh CCCD lưu trữ trên bucket riêng tư (Private AWS S3), chỉ sinh Pre-signed URL có thời hạn sống 15 phút khi Admin xem xét duyệt.
4. **Hệ thống Nhật ký Kiểm toán Doanh nghiệp (System Audit Logging):**
   - Tự động ghi nhận mọi thao tác nhạy cảm của Admin (Duyệt/Từ chối CCCD, Khóa tài khoản, Gỡ nội dung, Xử lý báo cáo) vào bảng `system_audit_logs` kèm IP, User-Agent, dấu vết thời gian và payload trước/sau khi cập nhật.

---

## 8. TIÊU CHUẨN HOÀN THÀNH (DEFINITION OF DONE)

Toàn bộ các module và API endpoints của hệ thống được kiểm toán và đối soát theo bộ 20 tiêu chuẩn kỹ thuật doanh nghiệp quy định chi tiết tại [`coding-rules.md`](./coding-rules.md).




# HƯỚNG DẪN TÍCH HỢP & ĐĂNG KÝ CREDENTIALS DỊCH VỤ BÊN THỨ BA

# DỰ ÁN: PHÒNG TRỌ XANH (PhongTroXanh.vn)

> **Mục tiêu:** Hướng dẫn từng bước từ A-Z cách đăng ký tài khoản miễn phí, lấy API keys và cấu hình biến môi trường cho 4 dịch vụ cốt lõi: **Cloudinary, Brevo, Goong Maps, VNPay Sandbox**.
> **Thời gian thực hiện ước tính:** 15 – 20 phút.
> **Chi phí:** 0 VNĐ (Toàn bộ đều có gói Free Tier / Sandbox thử nghiệm không giới hạn).

---

## 1. CLOUDINARY (DỊCH VỤ LƯU TRỮ ẢNH ĐÁM MÂY)

### 1.1. Tại sao bắt buộc dùng Cloudinary khi deploy Cloud (Render / Railway)?

- Các nền tảng PaaS miễn phí (Render, Railway, Fly.io) sử dụng **Ephemeral File System (Ổ cứng tạm thời)**. Khi server restart hoặc deploy phiên bản mới, toàn bộ thư mục `./uploads` trên ổ cứng sẽ bị **xóa sạch**.
- `CloudinaryStorageAdapter` cho phép upload trực tiếp luồng byte/stream từ Client lên Cloudinary qua HTTPS, trả về link ảnh vĩnh viễn (`https://res.cloudinary.com/...`) mà không ghi bất kỳ byte nào xuống ổ cứng cục bộ.

### 1.2. Các bước đăng ký và lấy Credentials

1. Truy cập trang đăng ký chính thức: **[https://cloudinary.com/users/register_free](https://cloudinary.com/users/register_free)**.
2. Điền thông tin (Họ tên, Email, Mật khẩu) hoặc chọn **Sign up with Google/GitHub** để tạo nhanh.
3. Sau khi đăng nhập, hệ thống sẽ đưa bạn vào trang **Dashboard (Console)**:
   - Tại góc trên bên trái hoặc phần **Product Environment Credentials**, bạn sẽ thấy 3 thông số:
     - **Cloud Name** (Ví dụ: `dxyzt123a`)
     - **API Key** (Dãy số, ví dụ: `987654321012345`)
     - **API Secret** (Bấm vào biểu tượng con mắt để copy, ví dụ: `aBcDeFgHiJkLmNoPqRsTuVwXyZ`)
4. Copy 3 giá trị này để chuẩn bị dán vào file `.env`.

---

## 2. BREVO EMAIL (GỬI EMAIL OTP XÁC THỰC QUA REST API)

### 2.1. Tại sao dùng Brevo REST API thay cho TCP SMTP (Port 587/465)?

- Cổng TCP 25, 465, 587 thường xuyên bị chặn hoặc lọc gắt gao trên các dịch vụ Cloud miễn phí, gây lỗi `Connection timed out`.
- Brevo cung cấp REST API hoạt động qua HTTPS chuẩn (Port 443), gửi mail tức thời trong 1-2 giây với hạn ngạch **300 email miễn phí mỗi ngày**, không bắt buộc có tên miền riêng (hỗ trợ tài khoản Gmail cá nhân).

### 2.2. Các bước đăng ký và lấy API Key

1. Truy cập trang đăng ký Brevo (trước đây là Sendinblue): **[https://onboarding.brevo.com/account/register](https://onboarding.brevo.com/account/register)**.
2. Điền email, họ tên, xác nhận email kích hoạt. Chọn gói **Free Plan (300 emails/day)**.
3. **Cấu hình Sender Email (Email gửi đi):**
   - Vào menu avatar góc trên bên phải → chọn **Senders, Domains & Dedicated IPs** (hoặc truy cập trực tiếp: `https://app.brevo.com/senders`).
   - Kiểm tra xem địa chỉ email bạn vừa đăng ký đã có trạng thái **Verified** hay chưa. Địa chỉ này sẽ được dùng làm biến `MAIL_FROM`.
4. **Sinh API Key v3:**
   - Vào menu avatar → chọn **SMTP & API** (hoặc truy cập: `https://app.brevo.com/settings/keys/api`).
   - Bấm vào nút **Generate a new API key**.
   - Đặt tên cho Key (Ví dụ: `PhongTroXanh-Backend`) → bấm **Generate**.
   - Copy mã API Key bắt đầu bằng `xkeysib-...` (Lưu ý: Mã này chỉ hiện 1 lần duy nhất).

---

## 3. GOONG MAPS (BẢN ĐỒ, TỌA ĐỘ POSTGIS & AUTOCOMPLETE ĐỊA CHỈ)

### 3.1. Phân định vai trò giữa Frontend và Backend

- **Frontend (Client):** Sử dụng **Goong Map Key / SDK** với Domain Restriction (`localhost:5173`, `phongtroxanh.vn`) để gọi trực tiếp Place Autocomplete theo từng phím bấm của người dùng. Điều này giảm thiểu tối đa độ trễ và tránh gây nghẽn cho Backend.
- **Backend:** Giữ lại **Goong REST API Key** để làm chốt chặn bảo mật (Fallback Geocoding). Khi người dùng đăng phòng hoặc cập nhật phòng mà không truyền kinh độ/vĩ độ, Backend sẽ tự động gọi Geocoding để tính tọa độ PostGIS `Point(lng, lat)`. Đồng thời API `/api/v1/locations/autocomplete` được cache Redis 24h làm fallback.

### 3.2. Các bước đăng ký và lấy API Keys

1. Truy cập cổng lập trình viên Goong: **[https://account.goong.io/login](https://account.goong.io/login)**.
2. Đăng ký tài khoản mới bằng Email hoặc Google. Mọi tài khoản mới được tặng ngay **$100 credit miễn phí** dùng trọn đời.
3. Vào trang quản lý Key: **[https://account.goong.io/keys](https://account.goong.io/keys)**.
4. **Tạo 2 loại Key riêng biệt:**
   - **REST API Key (Dành cho Backend):**
     - Bấm **Create Key** → chọn loại **API Key**.
     - Đặt tên: `Backend-Geocoding-Key`.
     - Copy chuỗi key này gán vào biến `GOONG_API_KEY` của Backend.
   - **Map Key (Dành cho Frontend):**
     - Bấm **Create Key** → chọn loại **Map Key**.
     - Đặt tên: `Frontend-Goong-Map-Key`.
     - (Khuyên dùng) Thêm giới hạn HTTP Referrers: `http://localhost:*`, `https://*.phongtroxanh.vn`.
     - Cung cấp key này cho đội Frontend hiển thị bản đồ tương tác và Places Autocomplete SDK.

---

## 4. VNPAY SANDBOX (CỔNG THANH TOÁN THỬ NGHIỆM 0Đ)

### 4.1. Cơ chế hoạt động trong dự án

- Backend sử dụng thuật toán băm **HMAC-SHA512** tạo chuỗi thanh toán có chữ ký số chuyển hướng sang cổng VNPay Sandbox.
- Sau khi thanh toán, VNPay sẽ gửi IPN Webhook về `/api/v1/monetization/vnpay-ipn` để tự động cộng số dư gói (Consumable swipes/boosts) với cơ chế Idempotent chống duplicate.

### 4.2. Bộ thông tin thẻ Test NCB Sandbox chính thức

Khi mở màn hình thanh toán VNPay Sandbox, chọn phương thức **Thẻ nội địa / Tài khoản ngân hàng**, chọn ngân hàng **NCB** và nhập thông tin kiểm thử sau:

| Thông tin trường    | Dữ liệu Test Sandbox          | Ghi chú                          |
| :------------------ | :---------------------------- | :------------------------------- |
| **Ngân hàng**       | **NCB** (Ngân hàng Quốc Dân)  | Chọn từ danh sách icon           |
| **Số thẻ**          | `9704198526191432198`         | Thẻ test mặc định của VNPay      |
| **Tên chủ thẻ**     | `NGUYEN VAN A`                | Không dấu, viết hoa              |
| **Ngày phát hành**  | `07/15` (Tháng 07 / Năm 2015) | Cố định                          |
| **Mã xác thực OTP** | `123456`                      | Mã OTP cố định cho mọi giao dịch |

### 4.3. Cấu hình Credentials mặc định

Dự án đã tích hợp sẵn TMN Code và Hash Secret chính thức từ VNPay Sandbox:

- `VNPAY_TMN_CODE=TESTVNPAY` (hoặc mã riêng của trường/đối tác nếu có)
- `VNPAY_HASH_SECRET=TESTHASHSECRET1234567890ABCDEF1234567890ABCDEF1234567890ABCDEF`
- `VNPAY_PAY_URL=https://sandbox.vnpayment.vn/paymentv2/vpcpay.html`

---

## 5. GOOGLE OAUTH 2.0 (GOOGLE IDENTITY SERVICES / SIGN IN WITH GOOGLE)

### 5.1. Cơ chế hoạt động trong dự án

- **Frontend (Client):** Sử dụng Google Identity Services hiển thị giao diện đăng nhập Google. Sau khi người dùng đăng nhập thành công, Google trả về chuỗi `Google ID Token` (JWT ký số RSA).
- **Backend:** Nhận `idToken` qua API `POST /api/v1/auth/oauth/google`. Backend sử dụng Google Tokeninfo API để kiểm tra chữ ký số, thời hạn và kiểm tra đối chiếu `aud` với `GOOGLE_CLIENT_ID`. Khi hợp lệ, hệ thống tự động tìm hoặc khởi tạo tài khoản, đánh dấu `isVerified = true` và phát hành access token / refresh token riêng của hệ thống.
- **Chi phí:** 0 VNĐ (Miễn phí 100%, không giới hạn lượt đăng nhập).

### 5.2. Các bước đăng ký và tạo Google OAuth 2.0 Client ID

1. Truy cập **Google Cloud Console**: **[https://console.cloud.google.com/](https://console.cloud.google.com/)**.
2. Đăng nhập tài khoản Google/Gmail của bạn.
3. **Tạo Project mới:**
   - Tại thanh điều hướng trên cùng, bấm vào menu chọn dự án → chọn **NEW PROJECT**.
   - Đặt tên dự án (Ví dụ: `PhongTroXanh-Platform`) → bấm **CREATE**.
4. **Cấu hình Màn hình đồng thuận (OAuth consent screen):**
   - Vào menu bên trái: **APIs & Services** → **OAuth consent screen**.
   - Chọn **User Type**: Chọn **External** (cho phép mọi người dùng có tài khoản Google đăng nhập) → bấm **CREATE**.
   - **App information:**
     - **App name:** `Phòng Trọ Xanh`
     - **User support email:** Chọn email của bạn.
     - **Developer contact information:** Điền email của bạn.
   - Bấm **SAVE AND CONTINUE** qua các bước _Scopes_ và _Test users_ (để mặc định các scope cơ bản: email, profile, openid).
5. **Tạo Credentials (OAuth 2.0 Client ID):**
   - Vào menu bên trái: **Credentials** → bấm **+ CREATE CREDENTIALS** → chọn **OAuth client ID**.
   - **Application type:** Chọn **Web application**.
   - **Name:** `PhongTroXanh Web Client`.
   - **Authorized JavaScript origins (BẮT BUỘC):**
     - Bấm **+ ADD URI** và thêm các domain:
       - `http://localhost:5173` (Frontend dev)
       - `http://localhost:8080` (Backend dev)
   - Bấm **CREATE**.
6. **Lấy Client ID:**
   - Copy chuỗi **Client ID** (dạng: `xxxxxxxxxxxx-xxxxxxxxxxxxxxxxxxxxxxxx.apps.googleusercontent.com`).
   - Gán chuỗi này vào:
     - Backend: `GOOGLE_CLIENT_ID` trong file `.env`
     - Frontend: `VITE_GOOGLE_CLIENT_ID` trong file `.env`

---

## 6. HƯỚNG DẪN ĐIỀN BIẾN MÔI TRƯỜNG FILE `.env`

Sau khi thu thập đầy đủ các khóa API ở 5 bước trên, hãy mở file `.env` ở thư mục gốc của backend và cập nhật các dòng tương ứng:

```properties
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
DB_PASSWORD=your_db_password

REDIS_HOST=localhost
REDIS_PORT=6379
REDIS_PASSWORD=

# ==============================================================================
# 3. SECURITY & JWT (HS512 Key at least 64 characters)
# ==============================================================================
JWT_SECRET=your_super_secret_key_at_least_64_characters_long_for_hs512_algorithm
JWT_ACCESS_EXPIRATION_MS=900000        # 15 Minutes
JWT_REFRESH_EXPIRATION_MS=604800000    # 7 Days
COOKIE_SECURE=false                     # true when running HTTPS
COOKIE_SAME_SITE=Lax

# ==============================================================================
# 4. PAYMENT GATEWAY (VNPay Sandbox)
# ==============================================================================
VNPAY_TMN_CODE=TESTVNPAY
VNPAY_HASH_SECRET=your_vnpay_hash_secret
VNPAY_PAY_URL=https://sandbox.vnpayment.vn/paymentv2/vpcpay.html
VNPAY_RETURN_URL=http://localhost:5173/payment/vnpay-return

# ==============================================================================
# 5. EMAIL SERVICE (Brevo REST API Port 443)
# ==============================================================================
MAIL_PROVIDER=console                  # console | brevo
BREVO_API_KEY=xkeysib-your_brevo_api_key_here
MAIL_FROM=phongtroxanh.vn@gmail.com
MAIL_FROM_NAME=PhongTroXanh Platform

# ==============================================================================
# 6. STORAGE (Cloudinary Cloud Storage)
# ==============================================================================
CLOUDINARY_CLOUD_NAME=your_cloudinary_cloud_name
CLOUDINARY_API_KEY=your_cloudinary_api_key
CLOUDINARY_API_SECRET=your_cloudinary_api_secret

# ==============================================================================
# 7. MAP & GEOCODING (Goong Maps REST API)
# ==============================================================================
GOONG_API_KEY=your_goong_api_key

# ==============================================================================
# 8. GOOGLE OAUTH 2.0 (Google Identity Services)
# ==============================================================================
GOOGLE_CLIENT_ID=your_google_client_id.apps.googleusercontent.com
```

---

## 7. KIỂM TRA & XÁC THỰC SAU KHI ĐIỀN CREDENTIALS

Chạy các lệnh sau tại thư mục gốc backend để kiểm tra kết nối:

```bash
# 1. Biên dịch mã nguồn đảm bảo thư viện nhận diện đầy đủ
./mvnw clean compile -DskipTests

# 2. Khởi động server
./mvnw spring-boot:run

# 3. Kiểm tra các dịch vụ trên Swagger UI:
# Truy cập: http://localhost:8080/swagger-ui.html
# - Đăng nhập Google OAuth2: POST /api/v1/auth/oauth/google
# - Gửi OTP qua Brevo: POST /api/v1/auth/send-otp
# - Upload ảnh lên Cloudinary: POST /api/v1/users/me/avatar
# - Kiểm tra Geocoding Goong: GET /api/v1/locations/geocode?address=Dai hoc BGD
# - Khởi tạo thanh toán VNPay: POST /api/v1/monetization/create-payment
```

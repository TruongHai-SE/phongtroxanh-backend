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
   - Vào menu avatar góc trên bên phải $\to$ chọn **Senders, Domains & Dedicated IPs** (hoặc truy cập trực tiếp: `https://app.brevo.com/senders`).
   - Kiểm tra xem địa chỉ email bạn vừa đăng ký đã có trạng thái **Verified** hay chưa. Địa chỉ này sẽ được dùng làm biến `MAIL_FROM`.
4. **Sinh API Key v3:**
   - Vào menu avatar $\to$ chọn **SMTP & API** (hoặc truy cập: `https://app.brevo.com/settings/keys/api`).
   - Bấm vào nút **Generate a new API key**.
   - Đặt tên cho Key (Ví dụ: `PhongTroXanh-Backend`) $\to$ bấm **Generate**.
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
     - Bấm **Create Key** $\to$ chọn loại **API Key**.
     - Đặt tên: `Backend-Geocoding-Key`.
     - Copy chuỗi key này gán vào biến `GOONG_API_KEY` của Backend.
   - **Map Key (Dành cho Frontend):**
     - Bấm **Create Key** $\to$ chọn loại **Map Key**.
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

## 5. HƯỚNG DẪN ĐIỀN BIẾN MÔI TRƯỜNG FILE `.env`

Sau khi thu thập đầy đủ các khóa API ở 4 bước trên, hãy mở file `.env` ở thư mục gốc của backend và cập nhật các dòng tương ứng:

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
DB_PASSWORD=12345

REDIS_HOST=localhost
REDIS_PORT=6379
REDIS_PASSWORD=

# ==============================================================================
# 3. SECURITY & JWT (HS512 Key at least 64 characters)
# ==============================================================================
JWT_SECRET=
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
MAIL_PROVIDER=brevo                  # console | brevo
BREVO_API_KEY=
MAIL_FROM=
MAIL_FROM_NAME=PhongTroXanh Platform

# ==============================================================================
# 6. STORAGE (Cloudinary Cloud Storage)
# ==============================================================================
CLOUDINARY_CLOUD_NAME=
CLOUDINARY_API_KEY=
CLOUDINARY_API_SECRET=

# ==============================================================================
# 7. MAP & GEOCODING (Goong Maps REST API)
# ==============================================================================
GOONG_API_KEY=your_goong_api_key
```

---

## 6. KIỂM TRA & XÁC THỰC SAU KHI ĐIỀN CREDENTIALS

Chạy các lệnh sau tại thư mục gốc backend để kiểm tra kết nối:

```bash
# 1. Biên dịch mã nguồn đảm bảo thư viện nhận diện đầy đủ
./mvnw clean compile -DskipTests

# 2. Khởi động server
./mvnw spring-boot:run

# 3. Kiểm tra các dịch vụ trên Swagger UI:
# Truy cập: http://localhost:8080/swagger-ui.html
# - Gửi OTP qua Brevo: POST /api/v1/auth/send-otp
# - Upload ảnh lên Cloudinary: POST /api/v1/users/me/avatar
# - Kiểm tra Geocoding Goong: GET /api/v1/locations/geocode?address=Dai hoc BGD
# - Khởi tạo thanh toán VNPay/VietQR: POST /api/v1/monetization/create-payment
```

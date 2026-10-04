# Thiết lập payOS cho MVP

Backend dùng SDK Java chính thức `vn.payos:payos-java:2.0.1`. Các endpoint tạo thanh toán, lịch sử và số dư giữ nguyên.

## Cấu hình

Ứng dụng đọc `.env` ở thư mục chạy. Gắn các giá trị sau; không commit khóa:

```properties
PAYOS_CLIENT_ID=
PAYOS_API_KEY=
PAYOS_CHECKSUM_KEY=
FRONTEND_URL=http://localhost:5173
PAYOS_RETURN_URL=http://localhost:5173/payment/payos-return
PAYOS_CANCEL_URL=http://localhost:5173/payment/payos-cancel
```

Return URL phải cùng origin với `FRONTEND_URL`. `.env` được đọc theo cú pháp Java properties: đặt chú thích trên dòng riêng, không thêm `# chú thích` vào cuối giá trị, không bọc giá trị bằng dấu nháy.

Đăng ký webhook **HTTPS công khai** trên dashboard payOS:

```text
https://<backend-domain>/api/v1/monetization/payos-webhook
```

Nếu demo local, trỏ HTTPS tunnel tới cổng backend. Webhook không cần JWT; backend xác minh chữ ký bằng checksum key. Thông báo test ký hợp lệ khi đăng ký được xác nhận mà không cộng quyền lợi.

## Frontend cần thay đổi

Tiếp tục `POST /api/v1/monetization/create-payment` với Bearer access token:

```json
{"packageId":"PRO_TENANT","paymentMethod":"PAYOS"}
```

Có thể bỏ `paymentMethod` để dùng mặc định PAYOS. Gửi `Idempotency-Key` cố định cho mỗi thao tác mua (1–60 ký tự chữ/số/`-`/`_`) khi retry. Khi tạo lần mua mới, dùng key mới. Response giữ `transactionCode`, `paymentMethod`, `amount`, `paymentUrl`; bổ sung `paymentLinkId` và `qrCode` khi có. Chuyển người dùng đến `paymentUrl`.

Sau khi quay lại frontend, đọc `GET /api/v1/monetization/payos-return?orderCode=...` với JWT hoặc tải lại `/transactions/me`. Chỉ trạng thái database do webhook xác nhận mới được coi là thanh toán thành công; tham số `status`, `code`, `cancel` từ trình duyệt không cấp quyền lợi.

`GET /vnpay-return` giữ làm alias đọc trạng thái. VNPay không tạo giao dịch mới; endpoint IPN cũ không xử lý thanh toán. Lịch sử VNPay cũ vẫn đọc được.

Gói hiện mua theo tháng, thời hạn 30 ngày, nối thêm kỳ hạn khi mua lại cùng gói. Giá năm trong danh mục chưa có luồng checkout theo năm. Đây là thanh toán gói hội viên/boost/swipe, chưa thu tiền thuê hoặc tiền cọc của hợp đồng.

## Database và kiểm tra

Volume mới được Docker chạy schema → migration → seed. Volume có sẵn cần migration một lần (có thể chạy lại):

```powershell
Get-Content db/migrations/V001__payments_and_rental_integrity.sql | docker exec -i phongtroxanh-postgres psql -U postgres -d phongtroxanh_db -v ON_ERROR_STOP=1
Get-Content db/seeds/subscription_plans.sql | docker exec -i phongtroxanh-postgres psql -U postgres -d phongtroxanh_db -v ON_ERROR_STOP=1
```

Không bọc toàn bộ file migration trong một transaction ngoài: PostgreSQL phải commit enum mới trước khi sử dụng. Các unique index sẽ báo lỗi nếu dữ liệu cũ có hợp đồng đang hoạt động trùng phòng hoặc review trùng; cần xử lý dữ liệu cụ thể, không xóa volume.

Sau khi gắn khóa: tạo đơn thật, thanh toán số tiền đúng, kiểm tra `SUCCESS`, subscription và số dư; gửi lại thông báo để xác nhận không cộng hai lần. Mất kết nối tạo đơn thì retry cùng key. Đơn hết hạn thì dùng key mới. Không có khóa trả `503 PAYOS_NOT_CONFIGURED`, không giả lập thành công.

Tài liệu nhà cung cấp: [Java SDK chính thức](https://github.com/payOSHQ/payos-lib-java), [API payOS](https://payos.vn/docs/api/).

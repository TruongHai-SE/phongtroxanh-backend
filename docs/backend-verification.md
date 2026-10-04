# Kết quả sửa và kiểm chứng MVP backend

Phạm vi: backend Phòng Trọ Xanh. Kiểm chứng ngày 01/10/2026; kết quả dưới đây là bằng chứng tại thời điểm đó. Chưa kiểm tra frontend.

## Những lỗi ảnh hưởng nghiêm trọng đến MVP

Các lỗi cấp ADMIN công khai, Google token giả, nghe hội thoại không có quyền, QR hết hạn vẫn dùng được, cộng quyền lợi thanh toán lặp và swap chỉ đổi trạng thái có thể phá cả bảo mật lẫn demo. Những đường xử lý này đã được sửa; không cần đổi kiến trúc hoặc viết lại controller.

| Luồng | Thay đổi logic và phạm vi kiểm chứng |
|---|---|
| Tài khoản | Chặn đăng ký/Google tự cấp ADMIN; xác minh token Google với nhà cung cấp; token phải đúng loại; kiểm tra trạng thái/quyền trong DB; OTP theo mục đích, giới hạn thử, proof dùng một lần; logout/reset thu hồi phiên; refresh chỉ xoay một lần; login/reset/refresh khóa cùng user trước kiểm tra và cấp token; update chỉ ghi các trường thay đổi để không khôi phục mật khẩu cũ. |
| Hồ sơ | Tôn trọng hồ sơ riêng tư; kiểm tra khoảng ngân sách khi cập nhật một phần; chặn xóa tài khoản còn thuê; chặn nộp KYC trùng, kể cả qua onboarding; duyệt KYC không cộng điểm lặp. |
| Tìm và đăng phòng | Giữ phí phòng, kiểm tra tọa độ/ngân sách; không tạo tọa độ giả nếu geocoding lỗi; loại tin hết hạn/chủ bị khóa; boost có hạn dùng và trừ số dư. |
| Ghép đôi | Reset quota theo ngày Việt Nam, tách lượt miễn phí để giữ lượt mua; boost hồ sơ có hiệu lực; mutual match tạo hội thoại và thông báo cho hai người; khóa cặp user theo cùng thứ tự để không mất match khi thích đồng thời. |
| Chat | Kiểm tra JWT và thành viên ở STOMP CONNECT/SUBSCRIBE/SEND, kiểm tra lại khi giao tin; lưu attachment; in-app notification; kiểm thử WebSocket native thật. |
| Thuê phòng | Một phòng chỉ có một hồ sơ thuê đang hoạt động; khóa giao dịch; giá/cọc lưu cố định khi tạo; QR chỉ lấy Redis TTL 5 phút, không fallback DB; check-in lặp bị chặn; hủy/kết thúc đúng trạng thái. |
| Pass phòng | Người đứng tên thuê thật đăng bài → đề xuất → người đăng chấp nhận → chủ trọ duyệt → kết thúc hồ sơ cũ và tạo hồ sơ mới chờ QR; giữ giá/cọc đã chốt. |
| Review | Chỉ hồ sơ đã check-in được đánh giá; chặn review trùng; reply/dispute/duyệt dispute chạy đúng phân quyền và không xử lý lặp. |
| Thanh toán | payOS SDK chính thức; lưu đơn trước network; retry cùng key không tạo đơn mới; verify chữ ký/số tiền/order/link; khóa đơn và số dư; cấp subscription/boost/quota một lần cùng transaction; hai đơn đồng thời không cộng sai quota. |
| Khởi tạo | Docker PostgreSQL 18/PostGIS khởi tạo được từ rỗng, schema → migration → 3 gói seed; Redis AOF dùng volume `/data`; lỗi input trả 400/409 thay vì che thành 500. |

## Giữ API như yêu cầu

Route và body của auth, user, room, matching, chat, rental, swap, review được giữ. Input sai hoặc hành vi không đủ quyền nay trả lỗi đúng thay vì thành công giả. Thanh toán là phần cần cập nhật frontend:

- `POST /api/v1/monetization/create-payment`: gửi `paymentMethod=PAYOS` hoặc bỏ để dùng mặc định, vẫn nhận `paymentUrl`/`transactionCode`/`amount`.
- Bổ sung `POST /api/v1/monetization/payos-webhook` cho nhà cung cấp. Có thể gửi header `Idempotency-Key` để retry an toàn.
- `/payos-return` đọc trạng thái DB với JWT; `/vnpay-return` giữ alias. Tham số callback không kích hoạt gói. VNPay ngừng tạo đơn/IPN, lịch sử cũ vẫn giữ.

Xem [hướng dẫn env, webhook và frontend payOS](payos-setup.md). Khóa thật do chủ dự án gắn; `.env.example` không chứa khóa payOS.

## Hợp đồng hiện tại

Entity `Rental`, bảng `rental_contracts`, API `/api/v1/rentals` lưu phòng, chủ trọ, người thuê, ngày bắt đầu/kết thúc, giá tháng, cọc, trạng thái và thời điểm check-in. Giá/cọc mới được snapshot nên đổi tin đăng không sửa số tiền trên hồ sơ đã tạo. Dữ liệu cũ chỉ backfill theo giá phòng tại thời điểm migration; không thể khôi phục giá đã thỏa thuận trong quá khứ nếu chưa từng lưu.

Đây là quản lý hồ sơ thuê online và bàn giao bằng QR. Hiện **không có nhà cung cấp ký điện tử**, không sinh PDF, không luồng ký hai bên/chứng thư/dấu thời gian/phụ lục. QR check-in không được mô tả là chữ ký điện tử. Demo có thể chạy quản lý thuê; nếu yêu cầu MVP là ký hợp đồng điện tử thì phần đó chưa hoàn thành. Chưa tự chọn hoặc tích hợp vendor khi chưa có yêu cầu cụ thể.

payOS chỉ thanh toán gói hội viên và quyền lợi, **chưa thu tiền thuê/cọc**. Swap hiện hỗ trợ **pass một phòng**; `offeredRoomId` được lưu như đề xuất, chưa thực hiện trao đổi hai hợp đồng/hai phòng với cả hai chủ trọ phê duyệt. Chưa coi việc đổi hai phòng là đã hoàn thành.

## Bằng chứng kiểm thử

**`mvn clean test`: 108/108 test đạt, 0 failures, 0 errors, 0 skipped**, kết thúc 12:58:10 ngày 01/10/2026. Bao gồm contextLoads, SQL quota, thanh toán retry/replay/sai số tiền/hai đơn đồng thời, ghép đôi đồng thời và profile không ghi đè mật khẩu sau reset. Dùng JDK 21 vì JDK 24 mặc định của máy không tương thích Lombok hiện tại. Log: `target/mvp-final-test.log`.

**HTTP/WebSocket: 73/73 kiểm tra đạt**, script exit 0 trên backend build mới nhất, DB test `ptx_mvp_check`. Bao gồm onboarding, riêng tư/UUID/input lỗi, matching/MATCH notification, WebSocket auth/membership/giao tin, attachment, phòng/tìm/save/boost, giá/cọc snapshot, reserve collision, QR/replay, review/dispute, pass phòng/check-in/kết thúc, KYC trùng, khóa/logout JWT. Kết quả: `target/mvp-http-results.json`; log: `target/mvp-http-check.log`.

Docker mới đã khởi tạo từ dữ liệu RAM rỗng và xác nhận 3 gói cùng các cột migration. Redis AOF giữ key thử qua restart; key thử đã xóa. `.env` local được căn DB credentials với Compose và tách chú thích cuối dòng numeric/bool/provider; các khóa nhà cung cấp giữ nguyên.

SQL integration chạy riêng `ptx_mvp_check`; HTTP script khóa cứng localhost:18080 và tên DB test. Tạo dữ liệu ngẫu nhiên, cấp admin/credits bằng fixture SQL trong DB test. Các câu trả lời HTTP được kiểm tra đúng status, body và trạng thái sau mỗi bước; bất kỳ lỗi nào đều exit khác 0.

```powershell
$env:JAVA_HOME='C:\Program Files\Java\jdk-21'
$env:PTX_TEST_DB_URL='jdbc:postgresql://localhost:5433/ptx_mvp_check?stringtype=unspecified'
mvn clean test
```

Test SQL payOS mock tại ranh giới SDK/network, dùng transaction và DB thật. Test chữ ký riêng dùng SDK thật với checksum giả dùng cho test. Điều này chưa chứng minh checkout/webhook với tài khoản payOS thật. HTTP demo thiếu khóa nhận 503, không giả lập giao dịch thành công.

## Môi trường và giới hạn

Migration [V001__payments_and_rental_integrity.sql](../db/migrations/V001__payments_and_rental_integrity.sql) đã áp dụng trên DB local hiện tại và DB test, không xóa PostgreSQL volume. Container PostgreSQL cũ mang tag 16 nhưng thực tế chạy 18; Docker mới giữ major 18 để đọc volume an toàn. Chuyển Redis AOF đã mất cache phiên local trong lần cấu hình này; đăng nhập lại, dữ liệu nghiệp vụ PostgreSQL vẫn giữ. Không xóa Redis volume vì credential-version/blacklist là trạng thái thu hồi token.

Chưa kiểm chứng provider thật: payOS, Google OAuth, Cloudinary, email Brevo, Goong. Cần khóa/cấu hình tương ứng; upload thiếu cấu hình không trả URL ảnh giả. Đăng phòng có tọa độ sẵn không phụ thuộc geocoding. In-app notifications đã có; chưa chứng minh push FCM hoặc email ngoài console. Gói năm chưa có checkout; không có refund/đối soát tự động khi nhà cung cấp mất webhook.

Các kết quả này chứng minh các luồng backend đã ghi rõ, không chứng minh frontend hay sẵn sàng vận hành production.

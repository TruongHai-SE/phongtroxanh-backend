package vn.phongtroxanh.backend.common.mail;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class BrevoEmailAdapter implements EmailNotificationPort {

    private final String provider;
    private final String apiKey;
    private final String fromEmail;
    private final String fromName;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public BrevoEmailAdapter(
            @Value("${app.mail.provider:console}") String provider,
            @Value("${app.mail.brevo-api-key:}") String apiKey,
            @Value("${app.mail.from:phongtroxanh.vn@gmail.com}") String fromEmail,
            @Value("${app.mail.from-name:PhongTroXanh Platform}") String fromName,
            ObjectMapper objectMapper) {
        this.provider = provider;
        this.apiKey = apiKey;
        this.fromEmail = fromEmail;
        this.fromName = fromName;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(3000))
                .build();
    }

    @Override
    public void sendOtpEmail(String toEmail, String otp) {
        String subject = "[PhongTroXanh] Mã xác thực OTP của bạn: " + otp;
        String contentHtml =
                "<div style='text-align:center;'>" +
                    "<h2 style='margin:0 0 12px 0;font-size:18px;font-weight:600;color:#0f172a;'>Mã xác thực tài khoản</h2>" +
                    "<p style='margin:0 0 20px 0;font-size:14px;line-height:1.6;color:#475569;'>" +
                        "Chào bạn,<br/>" +
                        "Bạn vừa yêu cầu mã OTP để xác thực tài khoản tại <strong style='color:#065f46;'>Phòng Trọ Xanh</strong>. " +
                        "Vui lòng nhập mã bên dưới để hoàn tất:" +
                    "</p>" +
                    "<div style='margin:24px 0;'>" +
                        "<div style='display:inline-block;padding:16px 36px;background:#f0fdf4;border:1.5px solid #bbf7d0;border-radius:14px;box-shadow:0 2px 6px rgba(5,150,105,0.06);'>" +
                            "<span style='font-family:ui-monospace,SFMono-Regular,Menlo,Monaco,Consolas,monospace;font-size:34px;font-weight:800;letter-spacing:8px;color:#047857;display:inline-block;'>" + otp + "</span>" +
                        "</div>" +
                    "</div>" +
                    "<p style='margin:18px 0 0 0;font-size:13px;line-height:1.6;color:#64748b;'>" +
                        "⏱️ Mã xác thực có hiệu lực trong vòng <strong style='color:#065f46;'>5 phút</strong>.<br/>" +
                        "Vì lý do an toàn bảo mật, tuyệt đối không chia sẻ mã này cho bất kỳ ai." +
                    "</p>" +
                "</div>";

        String htmlContent = buildStyledTemplate(contentHtml);
        sendEmailInternal(toEmail, subject, htmlContent);
    }

    @Override
    public void sendPasswordResetEmail(String toEmail, String tempToken) {
        String subject = "[PhongTroXanh] Yêu cầu đặt lại mật khẩu";
        String contentHtml =
                "<div style='text-align:center;'>" +
                    "<h2 style='margin:0 0 12px 0;font-size:18px;font-weight:600;color:#0f172a;'>Yêu cầu đặt lại mật khẩu</h2>" +
                    "<p style='margin:0 0 20px 0;font-size:14px;line-height:1.6;color:#475569;'>" +
                        "Chào bạn,<br/>" +
                        "Hệ thống nhận được yêu cầu đặt lại mật khẩu cho tài khoản liên kết với email này. " +
                        "Mã xác nhận bảo mật của bạn là:" +
                    "</p>" +
                    "<div style='margin:24px 0;'>" +
                        "<div style='display:inline-block;padding:14px 30px;background:#f0fdf4;border:1.5px solid #bbf7d0;border-radius:14px;'>" +
                            "<span style='font-family:ui-monospace,SFMono-Regular,Menlo,Monaco,Consolas,monospace;font-size:24px;font-weight:800;letter-spacing:4px;color:#047857;'>" + tempToken + "</span>" +
                        "</div>" +
                    "</div>" +
                    "<p style='margin:18px 0 0 0;font-size:13px;line-height:1.6;color:#64748b;'>" +
                        "⏱️ Mã xác nhận có hiệu lực trong vòng <strong style='color:#065f46;'>15 phút</strong>.<br/>" +
                        "Nếu bạn không gửi yêu cầu này, vui lòng bỏ qua thư hoặc đổi mật khẩu để bảo vệ tài khoản." +
                    "</p>" +
                "</div>";

        String htmlContent = buildStyledTemplate(contentHtml);
        sendEmailInternal(toEmail, subject, htmlContent);
    }

    @Override
    public void sendAdminResetPasswordEmail(String toEmail, String tempPassword) {
        String subject = "[Phòng Trọ Xanh] Mật khẩu tài khoản của bạn đã được đặt lại";
        String contentHtml =
                "<div style='text-align:left;'>" +
                    "<h2 style='margin:0 0 12px 0;font-size:18px;font-weight:600;color:#0f172a;'>Mật khẩu tài khoản đã được đặt lại</h2>" +
                    "<p style='margin:0 0 16px 0;font-size:14px;line-height:1.6;color:#475569;'>" +
                        "Chào bạn,<br/>" +
                        "Ban Quản Trị hệ thống <strong style='color:#065f46;'>Phòng Trọ Xanh</strong> vừa thực hiện cấp lại mật khẩu truy cập cho tài khoản liên kết với địa chỉ email này." +
                    "</p>" +
                    "<p style='margin:0 0 12px 0;font-size:14px;color:#334155;font-weight:500;'>" +
                        "Mật khẩu tạm thời mới của bạn là:" +
                    "</p>" +
                    "<div style='margin:20px 0;text-align:center;'>" +
                        "<div style='display:inline-block;padding:16px 36px;background:#f0fdf4;border:1.5px solid #86efac;border-radius:14px;box-shadow:0 2px 6px rgba(5,150,105,0.08);'>" +
                            "<span style='font-family:ui-monospace,SFMono-Regular,Menlo,Monaco,Consolas,monospace;font-size:26px;font-weight:800;letter-spacing:3px;color:#047857;'>" + tempPassword + "</span>" +
                        "</div>" +
                    "</div>" +
                    "<div style='padding:14px 16px;background:#fffbeb;border:1px solid #fde68a;border-radius:12px;margin:20px 0;font-size:13px;line-height:1.6;color:#92400e;'>" +
                        "⚠️ <strong>Lưu ý an toàn quan trọng:</strong><br/>" +
                        "• Mật khẩu này được sinh tự động an toàn và chỉ gửi duy nhất tới hòm thư cá nhân của bạn.<br/>" +
                        "• Vui lòng đăng nhập và <strong>tiến hành đổi mật khẩu mới ngay lập tức</strong> trong phần <em>Cài đặt tài khoản &gt; Đổi mật khẩu</em>.<br/>" +
                        "• Tuyệt đối không chia sẻ mật khẩu này cho bất kỳ ai khác." +
                    "</div>" +
                    "<p style='margin:16px 0 0 0;font-size:13px;line-height:1.6;color:#64748b;'>" +
                        "Nếu bạn không yêu cầu hành động này, vui lòng phản hồi ngay thư này hoặc liên hệ hỗ trợ của Phòng Trọ Xanh." +
                    "</p>" +
                "</div>";

        String htmlContent = buildStyledTemplate(contentHtml);
        sendEmailInternal(toEmail, subject, htmlContent);
    }

    @Override
    public void sendNotificationEmail(String toEmail, String subject, String content) {
        String contentHtml =
                "<div style='text-align:left;'>" +
                    "<p style='margin:0 0 16px 0;font-size:14px;line-height:1.6;color:#334155;'>" +
                        content +
                    "</p>" +
                "</div>";
        String htmlContent = buildStyledTemplate(contentHtml);
        sendEmailInternal(toEmail, subject, htmlContent);
    }

    private String buildStyledTemplate(String bodyContent) {
        return "<!DOCTYPE html>" +
                "<html lang='vi'>" +
                "<head>" +
                "<meta charset='UTF-8'>" +
                "<meta name='viewport' content='width=device-width, initial-scale=1.0'>" +
                "<meta name='color-scheme' content='light'>" +
                "<title>Phòng Trọ Xanh</title>" +
                "</head>" +
                "<body style='margin:0;padding:0;background-color:#f4f7f5;font-family:-apple-system,BlinkMacSystemFont,\"Segoe UI\",Roboto,Helvetica,Arial,sans-serif;color:#334155;'>" +
                "<table width='100%' border='0' cellspacing='0' cellpadding='0' style='background-color:#f4f7f5;padding:36px 12px;'>" +
                "<tr><td align='center'>" +
                "<table width='100%' border='0' cellspacing='0' cellpadding='0' style='max-width:480px;background-color:#ffffff;border-radius:18px;overflow:hidden;box-shadow:0 4px 20px rgba(6,78,59,0.06);border:1px solid #e2ebe4;'>" +
                "<!-- Top accent brand line -->" +
                "<tr>" +
                "<td style='height:5px;background:#059669;line-height:5px;font-size:5px;'>&nbsp;</td>" +
                "</tr>" +
                "<!-- Header -->" +
                "<tr>" +
                "<td style='padding:32px 28px 20px 28px;text-align:center;background:#ffffff;'>" +
                "<div style='display:inline-block;width:44px;height:44px;line-height:44px;border-radius:14px;background:#ecfdf5;border:1px solid #a7f3d0;font-size:22px;margin-bottom:10px;'>🏠</div>" +
                "<div style='font-family:\"Fraunces\",Georgia,serif;font-size:23px;font-weight:700;color:#064e3b;letter-spacing:-0.4px;'>Phòng Trọ Xanh</div>" +
                "<div style='font-size:12px;color:#059669;font-weight:600;margin-top:2px;letter-spacing:0.2px;'>Tìm trọ đúng — Ở đúng người</div>" +
                "<div style='margin-top:22px;border-top:1px solid #edf4ee;'></div>" +
                "</td>" +
                "</tr>" +
                "<!-- Content -->" +
                "<tr>" +
                "<td style='padding:6px 32px 32px 32px;background-color:#ffffff;'>" +
                bodyContent +
                "</td>" +
                "</tr>" +
                "<!-- Footer -->" +
                "<tr>" +
                "<td style='background-color:#f9fbf9;padding:20px 24px;border-top:1px solid #edf4ee;text-align:center;'>" +
                "<p style='margin:0 0 4px 0;font-size:12px;font-weight:500;color:#52796f;'>© 2026 Phòng Trọ Xanh. Tìm trọ đúng — Ở đúng người.</p>" +
                "<p style='margin:0;font-size:11px;color:#8fa39a;'>Đây là email tự động gửi từ hệ thống. Vui lòng không trả lời thư này.</p>" +
                "</td>" +
                "</tr>" +
                "</table>" +
                "</td></tr>" +
                "</table>" +
                "</body>" +
                "</html>";
    }

    private void sendEmailInternal(String toEmail, String subject, String htmlContent) {
        if ("console".equalsIgnoreCase(provider) || apiKey == null || apiKey.isBlank() || apiKey.startsWith("xkeysib-mock")) {
            log.info("[CONSOLE_EMAIL] To: {}, Subject: {}, Content Preview: {}", toEmail, subject, htmlContent.replaceAll("<[^>]*>", ""));
            return;
        }

        try {
            Map<String, Object> payload = Map.of(
                    "sender", Map.of("name", fromName, "email", fromEmail),
                    "to", List.of(Map.of("email", toEmail)),
                    "subject", subject,
                    "htmlContent", htmlContent
            );

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.brevo.com/v3/smtp/email"))
                    .timeout(Duration.ofMillis(5000))
                    .header("api-key", apiKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload)))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                log.info("Email sent successfully via Brevo to {}", toEmail);
            } else {
                log.warn("Failed to send email via Brevo to {}. Status: {}, Body: {}", toEmail, response.statusCode(), response.body());
            }
        } catch (Exception e) {
            log.error("Exception occurred while sending email via Brevo to {}: {}", toEmail, e.getMessage());
        }
    }
}

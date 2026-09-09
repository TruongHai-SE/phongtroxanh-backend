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
        String subject = "[PhongTroXanh] Mã xác thực OTP của bạn";
        String htmlContent = "<div style='font-family:Arial,sans-serif;padding:20px;'>" +
                "<h2>PhongTroXanh.vn - Xác thực tài khoản</h2>" +
                "<p>Mã OTP của bạn là: <strong style='font-size:24px;color:#2E7D32;'>" + otp + "</strong></p>" +
                "<p>Mã có hiệu lực trong 5 phút. Vui lòng không chia sẻ mã này cho bất kỳ ai.</p>" +
                "</div>";

        sendEmailInternal(toEmail, subject, htmlContent);
    }

    @Override
    public void sendPasswordResetEmail(String toEmail, String tempToken) {
        String subject = "[PhongTroXanh] Yêu cầu đặt lại mật khẩu";
        String htmlContent = "<div style='font-family:Arial,sans-serif;padding:20px;'>" +
                "<h2>PhongTroXanh.vn - Đặt lại mật khẩu</h2>" +
                "<p>Bạn vừa yêu cầu đặt lại mật khẩu. Mã xác nhận của bạn là:</p>" +
                "<p><strong style='font-size:18px;color:#1565C0;'>" + tempToken + "</strong></p>" +
                "<p>Mã có hiệu lực trong 15 phút.</p>" +
                "</div>";

        sendEmailInternal(toEmail, subject, htmlContent);
    }

    @Override
    public void sendNotificationEmail(String toEmail, String subject, String content) {
        String htmlContent = "<div style='font-family:Arial,sans-serif;padding:20px;'>" +
                "<h2>PhongTroXanh.vn Thông báo</h2>" +
                "<p>" + content + "</p>" +
                "</div>";
        sendEmailInternal(toEmail, subject, htmlContent);
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

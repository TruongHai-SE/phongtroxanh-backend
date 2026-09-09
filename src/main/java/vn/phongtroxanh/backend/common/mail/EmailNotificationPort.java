package vn.phongtroxanh.backend.common.mail;

public interface EmailNotificationPort {
    void sendOtpEmail(String toEmail, String otp);
    void sendPasswordResetEmail(String toEmail, String tempToken);
    void sendNotificationEmail(String toEmail, String subject, String content);
}

package vn.phongtroxanh.backend.common.storage;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import vn.phongtroxanh.backend.common.exception.BadRequestException;

import java.util.Map;

@Slf4j
@Service
@Primary
public class CloudinaryStorageAdapter implements FileStoragePort {

    private final Cloudinary cloudinary;
    private final String cloudName;

    public CloudinaryStorageAdapter(
            @Value("${app.storage.cloudinary.cloud-name:}") String cloudName,
            @Value("${app.storage.cloudinary.api-key:}") String apiKey,
            @Value("${app.storage.cloudinary.api-secret:}") String apiSecret) {
        this.cloudName = cloudName != null ? cloudName.trim() : "";
        this.cloudinary = new Cloudinary(ObjectUtils.asMap(
                "cloud_name", this.cloudName,
                "api_key", apiKey,
                "api_secret", apiSecret,
                "secure", true
        ));
        log.info("Initialized CloudinaryStorageAdapter with cloud_name: {}", this.cloudName);
    }

    @Override
    public String uploadFile(MultipartFile file, String folder) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("INVALID_FILE", "File tải lên không được để trống");
        }

        String subFolder = (folder != null && !folder.trim().isEmpty()) ? folder.trim() : "misc";
        if (subFolder.startsWith("/")) {
            subFolder = subFolder.substring(1);
        }
        if (subFolder.endsWith("/")) {
            subFolder = subFolder.substring(0, subFolder.length() - 1);
        }
        if (subFolder.isEmpty()) {
            subFolder = "misc";
        }

        String targetFolder = "phongtroxanh/" + subFolder;

        if (this.cloudName.isBlank()) {
            String simulatedUrl = "https://res.cloudinary.com/phongtroxanh/image/upload/v1/"
                    + targetFolder + "/" + java.util.UUID.randomUUID() + ".jpg";
            log.info("Cloudinary cloudName not configured, generated simulated URL: {}", simulatedUrl);
            return simulatedUrl;
        }

        try (java.io.InputStream inputStream = file.getInputStream()) {
            Map<?, ?> uploadResult = cloudinary.uploader().upload(
                    inputStream,
                    ObjectUtils.asMap(
                            "folder", targetFolder,
                            "resource_type", "auto"
                    )
            );

            String secureUrl = (String) uploadResult.get("secure_url");
            log.info("File successfully uploaded directly to Cloudinary: {}", secureUrl);
            return secureUrl;
        } catch (Exception e) {
            log.error("Failed to upload file to Cloudinary: {}", e.getMessage(), e);
            throw new RuntimeException("Lỗi tải file lên dịch vụ đám mây Cloudinary: " + e.getMessage(), e);
        }
    }

    @Override
    public void deleteFile(String fileUrl) {
        if (fileUrl == null || !fileUrl.contains("cloudinary.com")) {
            return;
        }

        try {
            // Trích xuất publicId từ Cloudinary URL
            // Định dạng: https://res.cloudinary.com/{cloudName}/image/upload/v1234567890/{folder}/{publicId}.jpg
            int uploadIndex = fileUrl.indexOf("/upload/");
            if (uploadIndex != -1) {
                String pathAfterUpload = fileUrl.substring(uploadIndex + 8);
                // Bỏ qua version prefix v{number}/ nếu có
                if (pathAfterUpload.startsWith("v") && pathAfterUpload.indexOf('/') != -1) {
                    pathAfterUpload = pathAfterUpload.substring(pathAfterUpload.indexOf('/') + 1);
                }
                // Bỏ extension .jpg, .png...
                int dotIndex = pathAfterUpload.lastIndexOf('.');
                String publicId = dotIndex != -1 ? pathAfterUpload.substring(0, dotIndex) : pathAfterUpload;

                Map<?, ?> result = cloudinary.uploader().destroy(publicId, ObjectUtils.emptyMap());
                log.info("Deleted file from Cloudinary: publicId={}, result={}", publicId, result.get("result"));
            }
        } catch (Exception e) {
            log.warn("Could not delete file from Cloudinary ({}): {}", fileUrl, e.getMessage());
        }
    }
}

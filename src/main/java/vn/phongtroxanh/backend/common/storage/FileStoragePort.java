package vn.phongtroxanh.backend.common.storage;

import org.springframework.web.multipart.MultipartFile;

public interface FileStoragePort {
    String uploadFile(MultipartFile file, String folder);
    void deleteFile(String fileUrl);
}

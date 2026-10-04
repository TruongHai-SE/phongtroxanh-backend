package vn.phongtroxanh.backend.common.storage;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import static org.junit.jupiter.api.Assertions.assertThrows;
class StorageIntegrityTest {
    @Test void missingCloudinaryKeysCannotPretendUploadSucceeded() {
        var storage = new CloudinaryStorageAdapter("", "", "");
        var file = new MockMultipartFile("file", "photo.png", "image/png", new byte[]{(byte)137,80,78,71,13,10,26,10});
        assertThrows(RuntimeException.class, () -> storage.uploadFile(file,"rooms"));
    }
}

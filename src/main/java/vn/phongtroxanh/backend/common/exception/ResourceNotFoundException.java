package vn.phongtroxanh.backend.common.exception;

import org.springframework.http.HttpStatus;

public class ResourceNotFoundException extends AppException {
    public ResourceNotFoundException(String code, String detail) {
        super(HttpStatus.NOT_FOUND, code, detail);
    }

    public ResourceNotFoundException(String detail) {
        super(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", detail);
    }
}

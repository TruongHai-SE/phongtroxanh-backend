package vn.phongtroxanh.backend.common.exception;

import org.springframework.http.HttpStatus;

public class ConflictException extends AppException {
    public ConflictException(String code, String detail) {
        super(HttpStatus.CONFLICT, code, detail);
    }

    public ConflictException(String detail) {
        super(HttpStatus.CONFLICT, "CONFLICT", detail);
    }
}

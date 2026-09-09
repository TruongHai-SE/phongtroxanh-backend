package vn.phongtroxanh.backend.common.exception;

import org.springframework.http.HttpStatus;

public class BadRequestException extends AppException {
    public BadRequestException(String code, String detail) {
        super(HttpStatus.BAD_REQUEST, code, detail);
    }

    public BadRequestException(String detail) {
        super(HttpStatus.BAD_REQUEST, "BAD_REQUEST", detail);
    }
}

package vn.phongtroxanh.backend.common.exception;

import org.springframework.http.HttpStatus;

public class ForbiddenException extends AppException {
    public ForbiddenException(String code, String detail) {
        super(HttpStatus.FORBIDDEN, code, detail);
    }

    public ForbiddenException(String detail) {
        super(HttpStatus.FORBIDDEN, "FORBIDDEN", detail);
    }
}

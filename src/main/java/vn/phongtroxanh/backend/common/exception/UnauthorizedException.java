package vn.phongtroxanh.backend.common.exception;

import org.springframework.http.HttpStatus;

public class UnauthorizedException extends AppException {
    public UnauthorizedException(String code, String detail) {
        super(HttpStatus.UNAUTHORIZED, code, detail);
    }

    public UnauthorizedException(String detail) {
        super(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", detail);
    }
}

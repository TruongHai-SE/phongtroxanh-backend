package vn.phongtroxanh.backend.common.exception;

import org.springframework.http.HttpStatus;

public class RateLimitException extends AppException {
    public RateLimitException(String code, String detail) {
        super(HttpStatus.TOO_MANY_REQUESTS, code, detail);
    }

    public RateLimitException(String detail) {
        super(HttpStatus.TOO_MANY_REQUESTS, "RATE_LIMIT_EXCEEDED", detail);
    }
}

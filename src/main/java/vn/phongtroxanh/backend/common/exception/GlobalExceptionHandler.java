package vn.phongtroxanh.backend.common.exception;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.persistence.OptimisticLockException;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.beans.TypeMismatchException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import vn.phongtroxanh.backend.common.dto.InvalidParam;
import vn.phongtroxanh.backend.common.dto.ProblemDetailResponse;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final String BASE_ERROR_URI = "https://api.phongtroxanh.vn/errors/";

    @ExceptionHandler({HttpMessageNotReadableException.class, TypeMismatchException.class,
            MissingServletRequestParameterException.class, MissingServletRequestPartException.class,
            ConstraintViolationException.class, BindException.class})
    public ResponseEntity<ProblemDetailResponse> handleInvalidInput(Exception ex, HttpServletRequest request) {
        return handleAppException(new BadRequestException("INVALID_INPUT_DATA", "Dữ liệu gửi lên không hợp lệ"), request);
    }

    @ExceptionHandler({DataIntegrityViolationException.class, OptimisticLockingFailureException.class, OptimisticLockException.class})
    public ResponseEntity<ProblemDetailResponse> handleDataConflict(Exception ex, HttpServletRequest request) {
        return handleAppException(new ConflictException("DATA_CONFLICT", "Dữ liệu bị trùng hoặc đã được cập nhật. Vui lòng tải lại và thử lại"), request);
    }

    @ExceptionHandler(AppException.class)
    public ResponseEntity<ProblemDetailResponse> handleAppException(AppException ex, HttpServletRequest request) {
        String reqId = getRequestId(request);
        log.warn("[{}] AppException [{}]: {}", reqId, ex.getCode(), ex.getDetail());

        ProblemDetailResponse problem = ProblemDetailResponse.builder()
                .type(BASE_ERROR_URI + ex.getCode().toLowerCase().replace('_', '-'))
                .title(formatTitle(ex.getCode()))
                .status(ex.getStatus().value())
                .detail(ex.getDetail())
                .instance(request.getRequestURI())
                .code(ex.getCode())
                .timestamp(Instant.now())
                .requestId(reqId)
                .build();

        return ResponseEntity.status(ex.getStatus()).body(problem);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ProblemDetailResponse> handleValidationException(MethodArgumentNotValidException ex, HttpServletRequest request) {
        String reqId = getRequestId(request);
        List<InvalidParam> invalidParams = new ArrayList<>();

        for (FieldError fieldError : ex.getBindingResult().getFieldErrors()) {
            invalidParams.add(InvalidParam.builder()
                    .name(fieldError.getField())
                    .reason(fieldError.getDefaultMessage())
                    .build());
        }

        log.warn("[{}] Validation failed on URI: {}, invalid fields count: {}", reqId, request.getRequestURI(), invalidParams.size());

        ProblemDetailResponse problem = ProblemDetailResponse.builder()
                .type(BASE_ERROR_URI + "invalid-input")
                .title("Invalid Input Data")
                .status(HttpStatus.BAD_REQUEST.value())
                .detail("Dữ liệu gửi lên không vượt qua validation")
                .instance(request.getRequestURI())
                .code("INVALID_INPUT_DATA")
                .invalidParams(invalidParams)
                .timestamp(Instant.now())
                .requestId(reqId)
                .build();

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(problem);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ProblemDetailResponse> handleAccessDenied(AccessDeniedException ex, HttpServletRequest request) {
        String reqId = getRequestId(request);
        log.warn("[{}] AccessDenied on {}: {}", reqId, request.getRequestURI(), ex.getMessage());

        ProblemDetailResponse problem = ProblemDetailResponse.builder()
                .type(BASE_ERROR_URI + "forbidden")
                .title("Forbidden")
                .status(HttpStatus.FORBIDDEN.value())
                .detail("Bạn không có quyền thực hiện thao tác này")
                .instance(request.getRequestURI())
                .code("FORBIDDEN")
                .timestamp(Instant.now())
                .requestId(reqId)
                .build();

        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(problem);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ProblemDetailResponse> handleAuthenticationException(AuthenticationException ex, HttpServletRequest request) {
        String reqId = getRequestId(request);
        log.warn("[{}] AuthenticationException on {}: {}", reqId, request.getRequestURI(), ex.getMessage());

        ProblemDetailResponse problem = ProblemDetailResponse.builder()
                .type(BASE_ERROR_URI + "unauthorized")
                .title("Unauthorized")
                .status(HttpStatus.UNAUTHORIZED.value())
                .detail("Chưa đăng nhập hoặc phiên đăng nhập đã hết hạn")
                .instance(request.getRequestURI())
                .code("UNAUTHORIZED")
                .timestamp(Instant.now())
                .requestId(reqId)
                .build();

        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(problem);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetailResponse> handleGenericException(Exception ex, HttpServletRequest request) {
        String reqId = getRequestId(request);
        log.error("[{}] Unhandled Internal Server Error on {}: ", reqId, request.getRequestURI(), ex);

        ProblemDetailResponse problem = ProblemDetailResponse.builder()
                .type(BASE_ERROR_URI + "internal-server-error")
                .title("Internal Server Error")
                .status(HttpStatus.INTERNAL_SERVER_ERROR.value())
                .detail("Đã xảy ra lỗi hệ thống. Vui lòng thử lại sau")
                .instance(request.getRequestURI())
                .code("INTERNAL_SERVER_ERROR")
                .timestamp(Instant.now())
                .requestId(reqId)
                .build();

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(problem);
    }

    private String getRequestId(HttpServletRequest request) {
        String reqId = request.getHeader("X-Request-ID");
        if (reqId == null || reqId.isBlank()) {
            reqId = "req_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        }
        return reqId;
    }

    private String formatTitle(String code) {
        if (code == null) return "Error";
        String[] words = code.toLowerCase().split("_");
        StringBuilder sb = new StringBuilder();
        for (String w : words) {
            if (!w.isEmpty()) {
                sb.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1)).append(" ");
            }
        }
        return sb.toString().trim();
    }
}

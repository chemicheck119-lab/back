package com.c2guard.bff.common;

import com.c2guard.integration.model.ModelApiErrorKind;
import com.c2guard.integration.model.ModelApiException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(basePackages = "com.c2guard.bff")
public class BffExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(BffExceptionHandler.class);

    @ExceptionHandler({MethodArgumentNotValidException.class,
            HttpMessageNotReadableException.class,
            ConstraintViolationException.class,
            IllegalArgumentException.class})
    ResponseEntity<DashboardErrorResponse> invalidRequest(Exception error,
                                                          HttpServletRequest request) {
        return response(HttpStatus.BAD_REQUEST, request, "INVALID_REQUEST",
                "요청 형식 또는 입력값을 확인해주세요.", false);
    }

    @ExceptionHandler(BffContractException.class)
    ResponseEntity<DashboardErrorResponse> contractFailure(BffContractException error,
                                                            HttpServletRequest request) {
        log.warn("bff_call requestId={} status=failed code={}",
                BffRequestIdFilter.current(request), error.getCode());
        return response(HttpStatus.valueOf(error.getStatus()), request, error.getCode(),
                error.getMessage(), error.isRetryable());
    }

    @ExceptionHandler(ModelApiException.class)
    ResponseEntity<DashboardErrorResponse> modelFailure(ModelApiException error,
                                                         HttpServletRequest request) {
        if (error.getKind() == ModelApiErrorKind.TIMEOUT) {
            return response(HttpStatus.GATEWAY_TIMEOUT, request, "MODEL_TIMEOUT",
                    "모델 서비스가 제한 시간 안에 응답하지 않았습니다. 현재 화면을 유지하고 다시 시도하세요.",
                    true);
        }
        if (error.getKind() == ModelApiErrorKind.CONTRACT) {
            return response(HttpStatus.UNPROCESSABLE_ENTITY, request,
                    "MODEL_CONTRACT_VIOLATION",
                    "모델 서비스 계약이 현재 BE 계약과 일치하지 않습니다.", false);
        }
        boolean retryable = error.getKind() == ModelApiErrorKind.NETWORK
                || (error.getUpstreamStatus() != null
                && error.getUpstreamStatus() == HttpStatus.SERVICE_UNAVAILABLE.value()
                && error.isRetryable());
        return response(HttpStatus.SERVICE_UNAVAILABLE, request,
                "MODEL_SERVICE_UNAVAILABLE",
                "모델 서비스가 준비되지 않았습니다. 저장된 현장 정보는 유지됩니다.",
                retryable);
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<DashboardErrorResponse> internalFailure(Exception error,
                                                            HttpServletRequest request) {
        log.error("bff_call requestId={} status=failed code=INTERNAL_ERROR type={}",
                BffRequestIdFilter.current(request), error.getClass().getSimpleName());
        return response(HttpStatus.INTERNAL_SERVER_ERROR, request, "INTERNAL_ERROR",
                "요청을 처리하지 못했습니다. 현재 화면을 유지해주세요.", false);
    }

    private ResponseEntity<DashboardErrorResponse> response(HttpStatus status,
                                                            HttpServletRequest request,
                                                            String code, String message,
                                                            boolean retryable) {
        String requestId = BffRequestIdFilter.current(request);
        return ResponseEntity.status(status)
                .body(new DashboardErrorResponse(requestId, code, message, retryable));
    }
}

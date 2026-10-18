package com.smartticket.backend.exception;

import com.smartticket.backend.dto.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice
public class GlobalExceptionHandler {

  @ExceptionHandler(LoginException.class)
  @ResponseStatus(HttpStatus.UNAUTHORIZED)
  public ApiResponse<Void> handleLoginException(LoginException e) {
    return ApiResponse.error(401, e.getMessage());
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  @ResponseStatus(HttpStatus.BAD_REQUEST)
  public ApiResponse<Void> handleValidationException(MethodArgumentNotValidException e) {
    String message =
        e.getBindingResult().getFieldErrors().stream()
            .findFirst()
            .map(error -> error.getDefaultMessage())
            .orElse("参数校验失败");

    return ApiResponse.error(400, message);
  }

  @ExceptionHandler(ResponseStatusException.class)
  public ResponseEntity<ApiResponse<Void>> handleResponseStatusException(
      ResponseStatusException e) {
    int code = e.getStatusCode().value();

    return ResponseEntity.status(code)
        .body(ApiResponse.error(code, e.getReason() == null ? "请求失败" : e.getReason()));
  }

  @ExceptionHandler({
    org.springframework.http.converter.HttpMessageNotReadableException.class,
    org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class,
    org.springframework.web.bind.MissingServletRequestParameterException.class,
    jakarta.validation.ConstraintViolationException.class
  })
  @ResponseStatus(HttpStatus.BAD_REQUEST)
  public ApiResponse<Void> invalidParameter(Exception e) {
    return ApiResponse.error(400, "参数格式或状态值不正确");
  }

  @ExceptionHandler(org.springframework.dao.DataIntegrityViolationException.class)
  @ResponseStatus(HttpStatus.CONFLICT)
  public ApiResponse<Void> conflict(Exception e) {
    return ApiResponse.error(409, "数据重复或不符合约束");
  }

  @ExceptionHandler(org.springframework.dao.DataAccessException.class)
  @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
  public ApiResponse<Void> database(Exception e) {
    org.slf4j.LoggerFactory.getLogger(getClass()).error("数据库操作失败", e);
    return ApiResponse.error(503, "数据库暂时不可用，请稍后重试");
  }

  @ExceptionHandler(Exception.class)
  @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
  public ApiResponse<Void> handleException(Exception e) {

    org.slf4j.LoggerFactory.getLogger(getClass()).error("请求处理失败", e);

    return ApiResponse.error(500, "服务器内部错误");
  }
}

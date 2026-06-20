package com.omnimerchant.common.dto;

import com.omnimerchant.common.exception.ErrorCode;
import lombok.Data;
import org.slf4j.MDC;

/**
 * 统一 API 响应体，所有 Controller 返回此类型。
 */
@Data
public class R<T> {

    private String code;
    private String message;
    private T data;
    private String traceId;
    private long timestamp;

    private R(String code, String message, T data) {
        this.code = code;
        this.message = message;
        this.data = data;
        this.traceId = MDC.get("traceId");
        this.timestamp = System.currentTimeMillis();
    }

    public static <T> R<T> ok(T data) {
        return new R<>(ErrorCode.SUCCESS.getCode(), ErrorCode.SUCCESS.getMessage(), data);
    }

    public static <T> R<T> ok() {
        return ok(null);
    }

    public static <T> R<T> fail(ErrorCode errorCode) {
        return new R<>(errorCode.getCode(), errorCode.getMessage(), null);
    }

    public static <T> R<T> fail(ErrorCode errorCode, String message) {
        return new R<>(errorCode.getCode(), message, null);
    }

    public static <T> R<T> fail(String code, String message) {
        return new R<>(code, message, null);
    }
}

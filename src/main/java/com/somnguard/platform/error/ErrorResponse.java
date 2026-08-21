package com.somnguard.platform.error;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ErrorResponse {

    private String code;
    private String message;
    private List<String> details;
    private String traceId;
    private Instant timestamp;
    private Integer status;
    private String path;
    private Map<String, Object> metadata;

    public static ErrorResponse of(ErrorCode errorCode, String traceId, String path) {
        ErrorResponse response = new ErrorResponse();
        response.setCode(errorCode.name());
        response.setMessage(errorCode.getDefaultMessage());
        response.setStatus(errorCode.getHttpStatus().value());
        response.setTimestamp(Instant.now());
        response.setTraceId(traceId);
        response.setPath(path);
        return response;
    }

    public static ErrorResponse of(ErrorCode errorCode, String message, String traceId, String path) {
        ErrorResponse response = of(errorCode, traceId, path);
        response.setMessage(message);
        return response;
    }

    public static ErrorResponse of(ErrorCode errorCode, List<String> details, String traceId, String path) {
        ErrorResponse response = of(errorCode, traceId, path);
        response.setDetails(details);
        return response;
    }
}
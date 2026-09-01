package com.somnguard.platform.error;

import java.util.List;

public record ErrorResponse(ErrorDetail error) {

    public record ErrorDetail(String code, String message, List<Detail> details, String trace_id) {}

    public record Detail(String field, String issue) {}

    public static ErrorResponse of(String code, String message, List<Detail> details, String traceId) {
        return new ErrorResponse(new ErrorDetail(code, message, details != null ? details : List.of(), traceId));
    }
}

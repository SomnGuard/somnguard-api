package com.somnguard.device_management.adapter.in.web.dto;

import java.util.List;

public record DevicePageResponse(
        List<DeviceResponse> data,
        Pagination pagination
) {
    public record Pagination(int page, int pageSize, long totalItems, int totalPages) {}
}

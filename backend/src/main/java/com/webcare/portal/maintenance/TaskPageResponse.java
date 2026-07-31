package com.webcare.portal.maintenance;

import java.util.List;

public record TaskPageResponse(List<TaskResponse> items, long totalItems) {
}

package com.hieu.analytics_service.controller;

import com.hieu.analytics_service.service.AnalyticsQueryService;
import com.hieu.common.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/** Admin endpoints over the ES analytics index. Kibana is the primary UI; these
 *  are the lightweight surfaces used by the Next admin dashboard. */
@RestController
@RequestMapping("/api/v1/analytics")
@Tag(name = "Analytics", description = "Admin queries over the analytics-events index")
@RequiredArgsConstructor
public class AnalyticsController {

    private final AnalyticsQueryService queryService;

    @GetMapping("/summary")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Aggregate counts + revenue between [from, to)")
    public ResponseEntity<ApiResponse<Map<String, Object>>> summary(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {
        return ResponseEntity.ok(ApiResponse.ok(queryService.summary(from, to)));
    }

    @GetMapping("/logs")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Recent analytics events (lightweight log feed)")
    public ResponseEntity<Map<String, Object>> logs(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String level,
            @RequestParam(required = false) String service,
            @RequestParam(defaultValue = "100") int size) {
        return ResponseEntity.ok(queryService.searchLogs(q, level, service, size));
    }

    @GetMapping("/revenue")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Daily revenue series between [from, to)")
    public ResponseEntity<List<Map<String, Object>>> revenue(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {
        return ResponseEntity.ok(queryService.revenueByDay(from, to));
    }
}

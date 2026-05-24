package com.hieu.voucher_service.controller;

import com.hieu.common.api.ApiResponse;
import com.hieu.voucher_service.dto.ApplyVoucherResponse;
import com.hieu.voucher_service.dto.CreateVoucherRequest;
import com.hieu.voucher_service.dto.ReleaseVoucherRequest;
import com.hieu.voucher_service.dto.UpdateVoucherRequest;
import com.hieu.voucher_service.dto.ValidateVoucherRequest;
import com.hieu.voucher_service.dto.VoucherDTO;
import com.hieu.voucher_service.service.VoucherApplicationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api/v1/vouchers")
@RequiredArgsConstructor
public class VoucherController {

    private final VoucherApplicationService voucherService;

    @PostMapping
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN', 'ADMIN')")
    public ResponseEntity<ApiResponse<VoucherDTO>> createVoucher(
            @Valid @RequestBody CreateVoucherRequest request) {
        VoucherDTO dto = voucherService.createVoucher(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(dto, "Voucher created"));
    }

    @GetMapping
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN', 'ADMIN')")
    public ResponseEntity<ApiResponse<Page<VoucherDTO>>> listVouchers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(ApiResponse.ok(voucherService.listVouchers(page, size)));
    }

    @GetMapping("/active")
    public ResponseEntity<ApiResponse<Page<VoucherDTO>>> listActiveVouchers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(ApiResponse.ok(voucherService.listActiveVouchers(page, size)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<VoucherDTO>> getVoucher(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(voucherService.getVoucher(id)));
    }

    @GetMapping("/code/{code}")
    public ResponseEntity<ApiResponse<VoucherDTO>> getVoucherByCode(@PathVariable String code) {
        return ResponseEntity.ok(ApiResponse.ok(voucherService.getVoucherByCode(code)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN', 'ADMIN')")
    public ResponseEntity<ApiResponse<VoucherDTO>> updateVoucher(
            @PathVariable Long id,
            @Valid @RequestBody UpdateVoucherRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(voucherService.updateVoucher(id, request), "Voucher updated"));
    }

    @DeleteMapping("/{id}/deactivate")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN', 'ADMIN')")
    public ResponseEntity<ApiResponse<VoucherDTO>> deactivateVoucher(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(voucherService.deactivateVoucher(id), "Voucher deactivated"));
    }

    /**
     * Validate và apply voucher — gọi bởi order-service khi checkout.
     */
    @PostMapping("/validate")
    public ResponseEntity<ApiResponse<ApplyVoucherResponse>> validateAndApply(
            @Valid @RequestBody ValidateVoucherRequest request) {
        ApplyVoucherResponse response = voucherService.validateAndApply(
                request.getCode(),
                request.getOrderAmount(),
                request.getUserId(),
                request.getOrderId(),
                request.getProductIds());
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    /**
     * Release voucher khi order bị cancel. Idempotent.
     */
    @PostMapping("/release")
    public ResponseEntity<ApiResponse<Void>> releaseVoucher(
            @Valid @RequestBody ReleaseVoucherRequest request) {
        voucherService.releaseVoucher(request.getCode(), request.getOrderId());
        return ResponseEntity.ok(ApiResponse.ok(null, "Voucher released"));
    }
}

package com.hieu.order_service.interfaces.rest;

import com.hieu.common.security.AuthenticatedUser;
import com.hieu.order_service.application.command.returnrequest.*;
import com.hieu.order_service.application.dto.PageDTO;
import com.hieu.order_service.application.dto.ReturnRequestDTO;
import com.hieu.order_service.application.handler.returnrequest.*;
import com.hieu.order_service.application.query.returnrequest.GetReturnRequestByIdQuery;
import com.hieu.order_service.application.query.returnrequest.ListUserReturnRequestsQuery;
import lombok.RequiredArgsConstructor;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/orders/return-requests")
@RequiredArgsConstructor
public class ReturnRequestController {

    private static final String FIELD_REFUND_AMOUNT = "refundAmount";


    private final RequestReturnHandler requestReturnHandler;
    private final ApproveReturnHandler approveReturnHandler;
    private final RejectReturnHandler rejectReturnHandler;
    private final CompleteReturnHandler completeReturnHandler;
    private final GetReturnRequestByIdHandler getReturnRequestByIdHandler;
    private final ListUserReturnRequestsHandler listUserReturnRequestsHandler;

    @PostMapping("/{orderId}")
    @ResponseStatus(HttpStatus.CREATED)
    public ReturnRequestDTO requestReturn(
            @PathVariable Long orderId,
            @Valid @RequestBody CreateReturnRequest req,
            @AuthenticationPrincipal AuthenticatedUser user) {
        return requestReturnHandler.handle(new RequestReturnCommand(
                orderId, user.userId(), req.reason(), req.returnType(), req.images()));
    }

    @GetMapping("/{returnRequestId}")
    public ReturnRequestDTO getById(@PathVariable Long returnRequestId,
                                    @AuthenticationPrincipal AuthenticatedUser user) {
        var isAdmin = user.roles().contains("ROLE_ADMIN");
        return getReturnRequestByIdHandler.handle(
                new GetReturnRequestByIdQuery(returnRequestId, user.userId(), isAdmin));
    }

    @GetMapping
    public PageDTO<ReturnRequestDTO> listMine(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal AuthenticatedUser user) {
        return listUserReturnRequestsHandler.handle(new ListUserReturnRequestsQuery(user.userId(), page, size));
    }

    @PostMapping("/{returnRequestId}/approve")
    @PreAuthorize("hasRole('ADMIN')")
    public ReturnRequestDTO approve(@PathVariable Long returnRequestId,
                                    @RequestBody Map<String, Object> body) {
        var refund = body.get(FIELD_REFUND_AMOUNT) != null ? new BigDecimal(body.get(FIELD_REFUND_AMOUNT).toString()) : null;
        return approveReturnHandler.handle(new ApproveReturnCommand(
                returnRequestId, (String) body.get("adminNote"), refund));
    }

    @PostMapping("/{returnRequestId}/reject")
    @PreAuthorize("hasRole('ADMIN')")
    public ReturnRequestDTO reject(@PathVariable Long returnRequestId,
                                   @RequestBody Map<String, String> body) {
        return rejectReturnHandler.handle(new RejectReturnCommand(returnRequestId, body.get("adminNote")));
    }

    @PostMapping("/{returnRequestId}/complete")
    @PreAuthorize("hasRole('ADMIN')")
    public ReturnRequestDTO complete(@PathVariable Long returnRequestId,
                                     @RequestBody(required = false) Map<String, Object> body) {
        var refund = body != null && body.get(FIELD_REFUND_AMOUNT) != null
                ? new BigDecimal(body.get(FIELD_REFUND_AMOUNT).toString()) : null;
        return completeReturnHandler.handle(new CompleteReturnCommand(returnRequestId, refund));
    }
}

package com.omnimerchant.agent.tool;

import com.omnimerchant.agent.service.CommercePlatformService;
import com.omnimerchant.agent.service.ToolAuditService;
import com.omnimerchant.tenant.context.TenantContextHolder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Spring AI Tool: order query.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderTools {

    private static final Pattern ORDER_ID_PATTERN = Pattern.compile("^(#?[A-Za-z0-9][A-Za-z0-9_-]{1,63})$");
    private final CommercePlatformService commerceService;
    private final ToolAuditService toolAuditService;

    @Tool(description = """
            Query detailed order information by order ID. \
            Returns order status, items, total amount, tracking number, and shipping address. \
            Use this tool when the customer asks about a specific order — \
            status inquiry, delivery ETA, order contents, or shipping details.
            """)
    public OrderQueryResult queryOrder(
            @ToolParam(description = "Order ID, format like #12345 or ORD-XXXXX")
            String orderId,
            @ToolParam(description = "Customer email for identity verification", required = false)
            String customerEmail) {
        var tenantId = TenantContextHolder.get();
        if (tenantId == null) {
            log.warn("queryOrder rejected because tenant context is missing");
            return unavailable(orderId, "MISSING_TENANT_CONTEXT",
                    "Order lookup requires a verified tenant context.");
        }

        if (orderId == null || !ORDER_ID_PATTERN.matcher(orderId).matches()) {
            log.warn("queryOrder rejected invalid orderId for tenant={}", tenantId);
            return unavailable(orderId, "INVALID_ORDER_ID",
                    "The order ID format is invalid.");
        }

        return toolAuditService.record("queryOrder", params("orderId", orderId, "customerEmailProvided",
                        customerEmail != null && !customerEmail.isBlank()),
                () -> {
                    var lookup = commerceService.queryOrder(orderId, customerEmail);
                    return new OrderQueryResult(
                            lookup.orderId(),
                            lookup.status(),
                            lookup.verified(),
                            lookup.items(),
                            lookup.totalAmount() == null ? null : lookup.totalAmount().toPlainString(),
                            lookup.currency(),
                            lookup.trackingNumber(),
                            lookup.trackingCarrier(),
                            lookup.trackingStatus(),
                            lookup.shippingAddress(),
                            lookup.message());
                });
    }

    @Tool(description = """
            Create an internal return request for a verified order. \
            This does not refund money or modify the external ecommerce platform. \
            It creates a pending human-review request when the customer wants to return items.
            """)
    public CommercePlatformService.ReturnActionResult createReturnRequest(
            @ToolParam(description = "Order ID or order number") String orderId,
            @ToolParam(description = "Customer email or phone for order ownership verification") String customerEmail,
            @ToolParam(description = "Reason for the return") String reason,
            @ToolParam(description = "JSON or short text describing requested return items", required = false) String items) {
        if (TenantContextHolder.get() == null) {
            return CommercePlatformService.ReturnActionResult.rejected(orderId, "MISSING_TENANT_CONTEXT",
                    "Return requests require a verified tenant context.");
        }
        return toolAuditService.record("createReturnRequest",
                params("orderId", orderId, "customerEmailProvided", customerEmail != null && !customerEmail.isBlank()),
                () -> commerceService.createReturnRequest(orderId, customerEmail, reason, items));
    }

    @Tool(description = """
            Request a refund or replacement for a verified order. \
            The AI never executes refunds directly; this only creates a pending human-approval request.
            """)
    public CommercePlatformService.ReturnActionResult requestRefundOrReplacement(
            @ToolParam(description = "Order ID or order number") String orderId,
            @ToolParam(description = "Customer email or phone for order ownership verification") String customerEmail,
            @ToolParam(description = "Action requested: refund or replacement") String action,
            @ToolParam(description = "Reason and context for the request") String reason) {
        if (TenantContextHolder.get() == null) {
            return CommercePlatformService.ReturnActionResult.rejected(orderId, "MISSING_TENANT_CONTEXT",
                    "Refund/replacement requests require a verified tenant context.");
        }
        return toolAuditService.record("requestRefundOrReplacement",
                params("orderId", orderId, "action", action, "customerEmailProvided",
                        customerEmail != null && !customerEmail.isBlank()),
                () -> commerceService.requestRefundOrReplacement(orderId, customerEmail, action, reason));
    }

    @Tool(description = """
            Request an address change for a verified order. \
            The AI never writes the external order directly; this creates a pending human-approval request.
            """)
    public CommercePlatformService.ReturnActionResult requestAddressChange(
            @ToolParam(description = "Order ID or order number") String orderId,
            @ToolParam(description = "Customer email or phone for order ownership verification") String customerEmail,
            @ToolParam(description = "New shipping address requested by the customer") String newAddress) {
        if (TenantContextHolder.get() == null) {
            return CommercePlatformService.ReturnActionResult.rejected(orderId, "MISSING_TENANT_CONTEXT",
                    "Address-change requests require a verified tenant context.");
        }
        return toolAuditService.record("requestAddressChange",
                params("orderId", orderId, "customerEmailProvided", customerEmail != null && !customerEmail.isBlank()),
                () -> commerceService.requestAddressChange(orderId, customerEmail, newAddress));
    }

    private OrderQueryResult unavailable(String orderId, String reason, String message) {
        return new OrderQueryResult(
                orderId,
                reason,
                false,
                List.of(),
                null,
                null,
                null,
                null,
                null,
                null,
                message);
    }

    private Map<String, Object> params(Object... entries) {
        var map = new LinkedHashMap<String, Object>();
        for (int i = 0; i + 1 < entries.length; i += 2) {
            map.put(String.valueOf(entries[i]), entries[i + 1]);
        }
        return map;
    }

    public record OrderQueryResult(
            String orderId,
            String status,
            boolean verified,
            List<Map<String, Object>> items,
            String totalAmount,
            String currency,
            String trackingNumber,
            String trackingCarrier,
            String trackingStatus,
            String shippingAddress,
            String message) {
    }
}

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
 * Spring AI Tool: logistics tracking.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LogisticsTools {

    private static final Pattern TRACKING_PATTERN = Pattern.compile("^[A-Za-z0-9][A-Za-z0-9_-]{3,63}$");
    private final CommercePlatformService commerceService;
    private final ToolAuditService toolAuditService;

    @Tool(description = """
            Track a shipment by tracking number. \
            Returns current status, estimated delivery date, and checkpoint history. \
            Use this tool when the customer asks about shipping status, \
            delivery ETA, or package location.
            """)
    public LogisticsResult trackLogistics(
            @ToolParam(description = "Tracking number from the carrier (e.g., FedEx, UPS, DHL)")
            String trackingNumber) {
        var tenantId = TenantContextHolder.get();
        if (tenantId == null) {
            log.warn("trackLogistics rejected because tenant context is missing");
            return unavailable(trackingNumber, "MISSING_TENANT_CONTEXT",
                    "Logistics lookup requires a verified tenant context.");
        }

        if (trackingNumber == null || !TRACKING_PATTERN.matcher(trackingNumber).matches()) {
            log.warn("trackLogistics rejected invalid tracking number for tenant={}", tenantId);
            return unavailable(trackingNumber, "INVALID_TRACKING_NUMBER",
                    "The tracking number format is invalid.");
        }

        return toolAuditService.record("trackLogistics", params("trackingNumber", trackingNumber), () -> {
            var lookup = commerceService.trackLogistics(trackingNumber);
            return new LogisticsResult(lookup.trackingNumber(), lookup.status(),
                    lookup.estimatedDelivery(), lookup.checkpoints(), lookup.message());
        });
    }

    private LogisticsResult unavailable(String trackingNumber, String reason, String message) {
        return new LogisticsResult(
                trackingNumber,
                reason,
                null,
                List.of(),
                message);
    }

    private Map<String, Object> params(Object... entries) {
        var map = new LinkedHashMap<String, Object>();
        for (int i = 0; i + 1 < entries.length; i += 2) {
            map.put(String.valueOf(entries[i]), entries[i + 1]);
        }
        return map;
    }

    public record LogisticsResult(
            String trackingNumber,
            String status,
            String estimatedDelivery,
            List<Map<String, Object>> checkpoints,
            String message) {
    }
}

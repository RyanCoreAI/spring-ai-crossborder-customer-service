package com.omnimerchant.agent.tool;

import com.omnimerchant.agent.service.CommercePlatformService;
import com.omnimerchant.agent.service.ToolAuditService;
import com.omnimerchant.tenant.context.TenantContextHolder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class ProductTools {

    private final CommercePlatformService commerceService;
    private final ToolAuditService toolAuditService;

    @Tool(description = """
            Search the tenant product catalog for ecommerce product advice. \
            Use this when customers ask about product recommendations, inventory, price, SKU, variants, \
            category fit, or buying guidance. Returns structured product cards from the tenant cache.
            """)
    public ProductSearchResult searchProductCatalog(
            @ToolParam(description = "Customer product query or keywords") String query,
            @ToolParam(description = "Optional category or product type", required = false) String category,
            @ToolParam(description = "Optional max price in store currency", required = false) BigDecimal maxPrice,
            @ToolParam(description = "Maximum number of products to return", required = false) Integer limit) {
        if (TenantContextHolder.get() == null) {
            return new ProductSearchResult("MISSING_TENANT_CONTEXT", List.of(),
                    "Product search requires a verified tenant context.");
        }
        return toolAuditService.record("searchProductCatalog",
                params("query", query, "category", category, "maxPrice", maxPrice),
                () -> {
                    var products = commerceService.searchProductCatalog(query, maxPrice, category,
                            limit == null ? 5 : limit);
                    return new ProductSearchResult(products.isEmpty() ? "NO_MATCH" : "OK", products,
                            products.isEmpty()
                                    ? "No matching products were found in this tenant catalog."
                                    : "Products come from the tenant catalog cache.");
                });
    }

    private Map<String, Object> params(Object... entries) {
        var map = new LinkedHashMap<String, Object>();
        for (int i = 0; i + 1 < entries.length; i += 2) {
            map.put(String.valueOf(entries[i]), entries[i + 1]);
        }
        return map;
    }

    public record ProductSearchResult(
            String status,
            List<CommercePlatformService.ProductRecommendation> products,
            String message) {
    }
}

package com.omnimerchant.agent.service;

import com.omnimerchant.agent.dto.IntegrationDtos;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.omnimerchant.agent.dto.CommerceDtos;
import com.omnimerchant.agent.entity.Customer;
import com.omnimerchant.agent.entity.IntegrationCredential;
import com.omnimerchant.agent.entity.OrderInfo;
import com.omnimerchant.agent.entity.Product;
import com.omnimerchant.agent.entity.ShopifySyncJob;
import com.omnimerchant.agent.entity.ShopifyBulkOperation;
import com.omnimerchant.agent.mapper.CustomerMapper;
import com.omnimerchant.agent.mapper.IntegrationCredentialMapper;
import com.omnimerchant.agent.mapper.OrderInfoMapper;
import com.omnimerchant.agent.mapper.ProductMapper;
import com.omnimerchant.agent.mapper.ShopifySyncJobMapper;
import com.omnimerchant.agent.mapper.ShopifyBulkOperationMapper;
import com.omnimerchant.common.exception.BusinessException;
import com.omnimerchant.common.exception.ErrorCode;
import com.omnimerchant.tenant.context.TenantContextHolder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class ShopifyIntegrationService {

    private static final String API_VERSION = "2026-01";
    private final IntegrationCredentialMapper credentialMapper;
    private final ProductMapper productMapper;
    private final OrderInfoMapper orderMapper;
    private final CustomerMapper customerMapper;
    private final ShopifySyncJobMapper syncJobMapper;
    private final ShopifyBulkOperationMapper bulkOperationMapper;
    private final CredentialCipher credentialCipher;
    private final ObjectMapper objectMapper;
    private final ShopifyWebhookService webhookService;
    private final ShopifyCredentialService credentialService;
    private final HttpClient httpClient = HttpClient.newHttpClient();

    public CommerceDtos.ShopifySyncResponse connect(CommerceDtos.ShopifyConnectRequest request) {
        return credentialService.connect(request);
    }

    public IntegrationDtos.ShopifyInstallResponse install(String shop) {
        return credentialService.install(shop);
    }

    public CommerceDtos.ShopifySyncResponse completeOAuthCallback(Map<String, String> params) {
        return credentialService.completeOAuthCallback(params);
    }

    @Transactional
    public CommerceDtos.ShopifySyncResponse sync() {
        var credential = activeCredential();
        var token = credentialCipher.decrypt(credential.getAccessTokenEncrypted());
        var products = 0;
        var customers = 0;
        var orders = 0;
        try {
            credentialService.ensureSyncJobs(requireTenant(), credential.getShopDomain());
            var jobs = syncJobMapper.selectList(new LambdaQueryWrapper<ShopifySyncJob>()
                    .eq(ShopifySyncJob::getShopDomain, credential.getShopDomain())
                    .in(ShopifySyncJob::getStatus, List.of("PENDING", "FAILED", "PARTIAL", "SUCCESS"))
                    .and(w -> w.isNull(ShopifySyncJob::getNextRunAt)
                            .or().le(ShopifySyncJob::getNextRunAt, LocalDateTime.now()))
                    .orderByAsc(ShopifySyncJob::getResource));
            if (jobs.isEmpty()) {
                jobs = syncJobMapper.selectList(new LambdaQueryWrapper<ShopifySyncJob>()
                        .eq(ShopifySyncJob::getShopDomain, credential.getShopDomain())
                        .orderByAsc(ShopifySyncJob::getResource));
            }
            for (var job : jobs) {
                var result = syncResourceJob(credential.getShopDomain(), token, job);
                if ("products".equals(result.resource())) {
                    products += result.imported();
                } else if ("customers".equals(result.resource())) {
                    customers += result.imported();
                } else if ("orders".equals(result.resource())) {
                    orders += result.imported();
                }
            }
            credential.setLastSyncAt(LocalDateTime.now());
            credential.setLastSyncStatus("SUCCESS");
            credential.setLastSyncError(null);
            credentialMapper.updateById(credential);
            return new CommerceDtos.ShopifySyncResponse("SUCCESS", "Shopify sync complete",
                    customers, orders, products);
        } catch (BusinessException e) {
            markCredentialSyncFailed(credential, e.getMessage());
            throw e;
        } catch (Exception e) {
            markCredentialSyncFailed(credential, e.getMessage());
            throw new BusinessException(ErrorCode.CHANNEL_API_ERROR, "Shopify 同步失败: " + e.getMessage());
        }
    }

    public IPage<IntegrationDtos.ShopifyJobVO> listJobs(int page, int size) {
        return syncJobMapper.selectPage(new Page<>(page, Math.max(1, Math.min(size, 100))),
                new LambdaQueryWrapper<ShopifySyncJob>().orderByDesc(ShopifySyncJob::getUpdatedAt))
                .convert(this::toJobVO);
    }

    @Transactional
    public IntegrationDtos.ShopifyJobVO retryJob(Long jobId) {
        var job = syncJobMapper.selectById(jobId);
        if (job == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Shopify sync job 不存在");
        }
        job.setStatus("PENDING");
        job.setNextRunAt(LocalDateTime.now());
        job.setLastError(null);
        syncJobMapper.updateById(job);
        return toJobVO(job);
    }

    public IPage<IntegrationDtos.ShopifyWebhookVO> listWebhooks(int page, int size) {
        return webhookService.list(page, size);
    }

    public IntegrationDtos.ShopifyWebhookVO processWebhookEvent(Long eventId) {
        return webhookService.process(eventId);
    }

    public IntegrationDtos.ShopifyWebhookVO replayWebhook(Long eventId) {
        return webhookService.replay(eventId);
    }

    public boolean verifyWebhook(String webhookSecret, String rawBody, String signature) {
        return webhookService.verify(webhookSecret, rawBody, signature);
    }

    public String decryptWebhookSecret(Long tenantId, String shopDomain) {
        return webhookService.decryptSecret(tenantId, shopDomain);
    }

    public String webhookVerificationSecret(Long tenantId, String shopDomain) {
        return webhookService.verificationSecret(tenantId, shopDomain);
    }

    public IPage<IntegrationDtos.ShopifyPrivacyRequestVO> listPrivacyRequests(int page, int size) {
        return webhookService.privacyRequests(page, size);
    }

    public IntegrationDtos.ShopifyBulkOperationVO startBulkInitialSync(String requestedResource) {
        var resource = valueOr(requestedResource, "").toLowerCase(Locale.ROOT);
        if (!List.of("products", "customers", "orders").contains(resource)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Bulk resource must be products, customers, or orders");
        }
        var credential = activeCredential();
        var token = credentialCipher.decrypt(credential.getAccessTokenEncrypted());
        try {
            var query = """
                    mutation {
                      bulkOperationRunQuery(query: \"\"\"%s\"\"\") {
                        bulkOperation { id status createdAt }
                        userErrors { field message }
                      }
                    }
                    """.formatted(bulkResourceQuery(resource));
            var root = objectMapper.readTree(graphql(credential.getShopDomain(), token, query));
            var payload = root.path("data").path("bulkOperationRunQuery");
            if (payload.path("userErrors").isArray() && !payload.path("userErrors").isEmpty()) {
                throw new BusinessException(ErrorCode.CHANNEL_API_ERROR,
                        "Shopify bulk operation rejected: " + payload.path("userErrors"));
            }
            var operation = payload.path("bulkOperation");
            var operationId = operation.path("id").asText(null);
            if (operationId == null || !operationId.startsWith("gid://shopify/BulkOperation/")) {
                throw new BusinessException(ErrorCode.CHANNEL_API_ERROR, "Shopify bulk operation id missing");
            }
            var row = new ShopifyBulkOperation();
            row.setTenantId(requireTenant());
            row.setShopDomain(credential.getShopDomain());
            row.setResource(resource);
            row.setExternalOperationId(operationId);
            row.setStatus(operation.path("status").asText("CREATED"));
            row.setObjectCount(0L);
            row.setStartedAt(firstNonNull(parseShopifyTime(operation.path("createdAt").asText(null)), LocalDateTime.now()));
            bulkOperationMapper.insert(row);
            return toBulkVO(row);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.CHANNEL_API_ERROR, "Shopify bulk start failed: " + e.getMessage());
        }
    }

    public IPage<IntegrationDtos.ShopifyBulkOperationVO> listBulkOperations(int page, int size) {
        return bulkOperationMapper.selectPage(new Page<>(page, Math.max(1, Math.min(size, 100))),
                        new LambdaQueryWrapper<ShopifyBulkOperation>().orderByDesc(ShopifyBulkOperation::getCreatedAt))
                .convert(this::toBulkVO);
    }

    @Transactional
    public IntegrationDtos.ShopifyBulkOperationVO refreshBulkOperation(Long id) {
        var row = requireBulkOperation(id);
        var credential = activeCredential();
        if (!credential.getShopDomain().equals(row.getShopDomain())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "Bulk operation does not belong to active shop");
        }
        try {
            var operationId = row.getExternalOperationId().replace("\\", "\\\\").replace("\"", "\\\"");
            var query = """
                    { bulkOperation(id: "%s") {
                        id status errorCode createdAt completedAt objectCount fileSize url partialDataUrl
                      }
                    }
                    """.formatted(operationId);
            var root = objectMapper.readTree(graphql(row.getShopDomain(),
                    credentialCipher.decrypt(credential.getAccessTokenEncrypted()), query));
            var operation = root.path("data").path("bulkOperation");
            if (operation.isMissingNode() || operation.isNull()) {
                throw new BusinessException(ErrorCode.CHANNEL_API_ERROR, "Shopify bulk operation not found");
            }
            row.setStatus(operation.path("status").asText(row.getStatus()));
            row.setErrorCode(operation.path("errorCode").isNull() ? null : operation.path("errorCode").asText(null));
            row.setObjectCount(operation.path("objectCount").asLong(0));
            row.setFileSize(operation.path("fileSize").isNull() ? null : operation.path("fileSize").asLong());
            row.setCompletedAt(parseShopifyTime(operation.path("completedAt").asText(null)));
            row.setResultUrlEncrypted(encryptOptional(operation.path("url").asText(null)));
            row.setPartialUrlEncrypted(encryptOptional(operation.path("partialDataUrl").asText(null)));
            bulkOperationMapper.updateById(row);
            return toBulkVO(row);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.CHANNEL_API_ERROR, "Shopify bulk refresh failed: " + e.getMessage());
        }
    }

    @Transactional
    public CommerceDtos.ShopifySyncResponse importBulkResult(Long id) {
        var row = requireBulkOperation(id);
        if (!"COMPLETED".equals(row.getStatus()) || row.getResultUrlEncrypted() == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Bulk operation is not completed or has no result URL");
        }
        var imported = 0;
        try {
            var request = HttpRequest.newBuilder()
                    .uri(URI.create(credentialCipher.decrypt(row.getResultUrlEncrypted())))
                    .GET().build();
            var response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new BusinessException(ErrorCode.CHANNEL_API_ERROR,
                        "Shopify bulk download HTTP " + response.statusCode());
            }
            try (var reader = new BufferedReader(new InputStreamReader(response.body(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.isBlank()) {
                        continue;
                    }
                    var node = objectMapper.readTree(line);
                    if (node.has("__parentId")) {
                        continue;
                    }
                    var batch = objectMapper.createArrayNode().add(node);
                    imported += switch (row.getResource()) {
                        case "products" -> importProducts(batch);
                        case "customers" -> importCustomers(batch);
                        case "orders" -> importOrders(batch);
                        default -> 0;
                    };
                }
            }
            row.setStatus("IMPORTED");
            row.setObjectCount((long) imported);
            bulkOperationMapper.updateById(row);
            return new CommerceDtos.ShopifySyncResponse("SUCCESS", "Shopify bulk result imported",
                    "customers".equals(row.getResource()) ? imported : 0,
                    "orders".equals(row.getResource()) ? imported : 0,
                    "products".equals(row.getResource()) ? imported : 0);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.CHANNEL_API_ERROR, "Shopify bulk import failed: " + e.getMessage());
        }
    }

    String bulkResourceQuery(String resource) {
        return switch (resource) {
            case "products" -> "{ products { edges { node { id title handle description vendor productType status } } } }";
            case "customers" -> "{ customers { edges { node { id email phone displayName firstName lastName numberOfOrders } } } }";
            case "orders" -> "{ orders { edges { node { id name email phone createdAt displayFinancialStatus displayFulfillmentStatus customer { id email phone displayName } totalPriceSet { shopMoney { amount currencyCode } } } } } }";
            default -> throw new BusinessException(ErrorCode.BAD_REQUEST, "Unsupported bulk resource: " + resource);
        };
    }

    private ShopifyBulkOperation requireBulkOperation(Long id) {
        var row = bulkOperationMapper.selectById(id);
        if (row == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Shopify bulk operation 不存在");
        }
        return row;
    }

    private String encryptOptional(String value) {
        return value == null || value.isBlank() ? null : credentialCipher.encrypt(value);
    }

    String shopifyGid(String resource, String id) {
        if (id == null || id.isBlank() || "null".equalsIgnoreCase(id)) {
            return null;
        }
        var trimmed = id.trim();
        if (trimmed.startsWith("gid://shopify/")) {
            return trimmed;
        }
        return "gid://shopify/" + resource + "/" + trimmed;
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (var value : values) {
            if (value != null && !value.isBlank() && !"null".equalsIgnoreCase(value)) {
                return value;
            }
        }
        return null;
    }

    private String valueOr(String value, String fallback) {
        return value == null || value.isBlank() || "null".equalsIgnoreCase(value) ? fallback : value;
    }

    @SafeVarargs
    private <T> T firstNonNull(T... values) {
        if (values == null) {
            return null;
        }
        for (var value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private LocalDateTime parseShopifyTime(String value) {
        if (value == null || value.isBlank() || "null".equalsIgnoreCase(value)) {
            return null;
        }
        try {
            return OffsetDateTime.parse(value).toLocalDateTime();
        } catch (Exception ignored) {
            try {
                return LocalDateTime.parse(value.replace("Z", ""));
            } catch (Exception e) {
                return null;
            }
        }
    }

    private String toJsonText(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            return String.valueOf(value);
        }
    }

    private void updateSyncJob(String shopDomain, String resource, int imported, String status, String throttle, String error) {
        var job = syncJobMapper.selectOne(new LambdaQueryWrapper<ShopifySyncJob>()
                .eq(ShopifySyncJob::getShopDomain, normalizeShopDomain(shopDomain))
                .eq(ShopifySyncJob::getResource, resource)
                .last("LIMIT 1"));
        if (job == null) {
            return;
        }
        job.setStatus(status);
        job.setAttempts((job.getAttempts() == null ? 0 : job.getAttempts()) + 1);
        job.setImportedCount(imported);
        job.setThrottleStatusJson(throttle);
        job.setLastError(error);
        job.setLastRunAt(LocalDateTime.now());
        job.setNextRunAt("SUCCESS".equals(status) ? null : LocalDateTime.now().plusMinutes(5));
        syncJobMapper.updateById(job);
    }

    private void markCredentialSyncFailed(IntegrationCredential credential, String error) {
        credential.setLastSyncAt(LocalDateTime.now());
        credential.setLastSyncStatus("FAILED");
        credential.setLastSyncError(error);
        credentialMapper.updateById(credential);
        updateSyncJob(credential.getShopDomain(), "orders", 0, "FAILED", null, error);
    }

    private SyncResult syncResourceJob(String shopDomain, String token, ShopifySyncJob job) {
        var resource = job.getResource();
        if ("fulfillments".equals(resource) || "refunds".equals(resource)) {
            markDerivedJob(job, "Covered by order sync and webhook processors.");
            return new SyncResult(resource, 0);
        }
        try {
            job.setStatus("RUNNING");
            job.setAttempts((job.getAttempts() == null ? 0 : job.getAttempts()) + 1);
            syncJobMapper.updateById(job);

            var root = objectMapper.readTree(graphql(shopDomain, token, resourceQuery(resource, job.getCursorValue())));
            if (root.has("errors")) {
                throw new BusinessException(ErrorCode.CHANNEL_API_ERROR, root.get("errors").toString());
            }
            var connection = root.path("data").path(resource);
            var nodes = connection.path("nodes");
            var imported = switch (resource) {
                case "products" -> importProducts(nodes);
                case "customers" -> importCustomers(nodes);
                case "orders" -> importOrders(nodes);
                default -> 0;
            };
            var pageInfo = connection.path("pageInfo");
            var hasNext = pageInfo.path("hasNextPage").asBoolean(false);
            var endCursor = pageInfo.path("endCursor").isNull() ? null : pageInfo.path("endCursor").asText(null);
            var cost = root.path("extensions").path("cost");
            job.setImportedCount(imported);
            job.setCursorValue(hasNext ? endCursor : null);
            job.setThrottleStatusJson(cost.isMissingNode() ? null : cost.toString());
            job.setLastError(null);
            job.setLastRunAt(LocalDateTime.now());
            job.setStatus(hasNext ? "PARTIAL" : "SUCCESS");
            job.setNextRunAt(hasNext ? nextRunFromThrottle(cost) : nextRunAfterFullSync(resource));
            syncJobMapper.updateById(job);
            return new SyncResult(resource, imported);
        } catch (BusinessException e) {
            markJobFailed(job, e.getMessage());
            throw e;
        } catch (Exception e) {
            markJobFailed(job, e.getMessage());
            throw new BusinessException(ErrorCode.CHANNEL_API_ERROR,
                    "Shopify " + resource + " sync failed: " + e.getMessage());
        }
    }

    private void markDerivedJob(ShopifySyncJob job, String message) {
        job.setStatus("SUCCESS");
        job.setLastError(message);
        job.setLastRunAt(LocalDateTime.now());
        job.setNextRunAt(LocalDateTime.now().plusHours(6));
        syncJobMapper.updateById(job);
    }

    private void markJobFailed(ShopifySyncJob job, String error) {
        job.setStatus(job.getAttempts() != null && job.getAttempts() >= 3 ? "DEAD" : "FAILED");
        job.setLastError(error);
        job.setLastRunAt(LocalDateTime.now());
        job.setNextRunAt(LocalDateTime.now().plusMinutes(5L * Math.max(1, job.getAttempts() == null ? 1 : job.getAttempts())));
        syncJobMapper.updateById(job);
    }

    LocalDateTime nextRunFromThrottle(JsonNode cost) {
        var throttle = cost.path("throttleStatus");
        var available = throttle.path("currentlyAvailable").asInt(1000);
        var restoreRate = Math.max(1, throttle.path("restoreRate").asInt(50));
        if (available >= 100) {
            return LocalDateTime.now().plusSeconds(5);
        }
        var waitSeconds = Math.min(300, Math.max(10, (100 - available) / restoreRate + 1));
        return LocalDateTime.now().plusSeconds(waitSeconds);
    }

    private LocalDateTime nextRunAfterFullSync(String resource) {
        return switch (resource) {
            case "products" -> LocalDateTime.now().plusHours(6);
            case "customers" -> LocalDateTime.now().plusHours(3);
            case "orders" -> LocalDateTime.now().plusMinutes(30);
            default -> LocalDateTime.now().plusHours(6);
        };
    }

    String resourceQuery(String resource, String cursor) {
        var after = cursor == null || cursor.isBlank() ? "" : ", after: \"" + cursor.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
        return switch (resource) {
            case "products" -> """
                    {
                      products(first: 50%s) {
                        pageInfo { hasNextPage endCursor }
                        nodes {
                          id title handle description vendor productType status tags
                          featuredImage { url }
                          variants(first: 20) {
                            nodes {
                              sku barcode inventoryQuantity
                              price
                              compareAtPrice
                            }
                          }
                        }
                      }
                    }
                    """.formatted(after);
            case "customers" -> """
                    {
                      customers(first: 50%s) {
                        pageInfo { hasNextPage endCursor }
                        nodes {
                          id email phone displayName firstName lastName numberOfOrders amountSpent { amount currencyCode }
                          defaultAddress { countryCodeV2 province city }
                        }
                      }
                    }
                    """.formatted(after);
            case "orders" -> """
                    {
                      orders(first: 50%s, sortKey: UPDATED_AT, reverse: true) {
                        pageInfo { hasNextPage endCursor }
                        nodes {
                          id name email phone createdAt processedAt cancelledAt displayFinancialStatus displayFulfillmentStatus
                          customer { id email phone displayName }
                          totalPriceSet { shopMoney { amount currencyCode } }
                          subtotalPriceSet { shopMoney { amount } }
                          totalShippingPriceSet { shopMoney { amount } }
                          totalTaxSet { shopMoney { amount } }
                          totalRefundedSet { shopMoney { amount } }
                          shippingAddress { address1 address2 city province zip countryCodeV2 phone name }
                          lineItems(first: 50) { nodes { title quantity sku originalUnitPriceSet { shopMoney { amount } } } }
                          fulfillments(first: 10) { trackingInfo { number company url } status createdAt updatedAt }
                        }
                      }
                    }
                    """.formatted(after);
            default -> throw new BusinessException(ErrorCode.BAD_REQUEST, "Unsupported Shopify sync resource: " + resource);
        };
    }

    private IntegrationCredential activeCredential() {
        var credential = credentialMapper.selectOne(new LambdaQueryWrapper<IntegrationCredential>()
                .eq(IntegrationCredential::getPlatform, "shopify")
                .eq(IntegrationCredential::getStatus, 1)
                .last("LIMIT 1"));
        if (credential == null) {
            throw new BusinessException(ErrorCode.CHANNEL_AUTH_FAILED, "Shopify 尚未连接");
        }
        return credential;
    }

    private String graphql(String shopDomain, String token, String query) throws Exception {
        var body = objectMapper.writeValueAsString(Map.of("query", query));
        var request = HttpRequest.newBuilder()
                .uri(URI.create("https://" + normalizeShopDomain(shopDomain) + "/admin/api/" + API_VERSION + "/graphql.json"))
                .header("Content-Type", "application/json")
                .header("X-Shopify-Access-Token", token)
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new BusinessException(ErrorCode.CHANNEL_API_ERROR,
                    "Shopify API HTTP " + response.statusCode() + ": " + response.body());
        }
        return response.body();
    }

    private int importProducts(JsonNode nodes) {
        var count = 0;
        if (!nodes.isArray()) {
            return 0;
        }
        for (var node : nodes) {
            var product = productMapper.selectOne(new LambdaQueryWrapper<Product>()
                    .eq(Product::getExternalProductId, text(node, "id"))
                    .last("LIMIT 1"));
            if (product == null) {
                product = new Product();
                product.setTenantId(requireTenant());
                product.setExternalProductId(text(node, "id"));
                product.setCurrency("USD");
                product.setTotalStock(0);
                product.setStockStatus("unknown");
                product.setVariantCount(1);
                product.setRequiresShipping(1);
                product.setIsTaxable(1);
            }
            product.setTitle(text(node, "title"));
            product.setHandle(text(node, "handle"));
            product.setDescriptionPlain(text(node, "description"));
            product.setVendor(text(node, "vendor"));
            product.setBrand(text(node, "vendor"));
            product.setProductType(text(node, "productType"));
            product.setCurrency("USD");
            product.setStatus(1);
            product.setSyncedAt(LocalDateTime.now());
            product.setVectorSynced(0);
            if (product.getId() == null) {
                productMapper.insert(product);
            } else {
                productMapper.updateById(product);
            }
            count++;
        }
        return count;
    }

    private int importCustomers(JsonNode nodes) {
        var count = 0;
        if (!nodes.isArray()) {
            return 0;
        }
        for (var node : nodes) {
            var customer = customerMapper.selectOne(new LambdaQueryWrapper<Customer>()
                    .eq(Customer::getExternalCustomerId, text(node, "id"))
                    .last("LIMIT 1"));
            if (customer == null) {
                customer = new Customer();
                customer.setTenantId(requireTenant());
                customer.setExternalCustomerId(text(node, "id"));
            }
            customer.setEmail(text(node, "email"));
            customer.setPhone(text(node, "phone"));
            customer.setDisplayName(text(node, "displayName"));
            customer.setTotalOrders(intValue(node, "numberOfOrders"));
            customer.setSyncedAt(LocalDateTime.now());
            customer.setSyncStatus(1);
            if (customer.getId() == null) {
                customerMapper.insert(customer);
            } else {
                customerMapper.updateById(customer);
            }
            count++;
        }
        return count;
    }

    private int importOrders(JsonNode nodes) {
        var count = 0;
        if (!nodes.isArray()) {
            return 0;
        }
        for (var node : nodes) {
            var order = orderMapper.selectOne(new LambdaQueryWrapper<OrderInfo>()
                    .eq(OrderInfo::getExternalOrderId, text(node, "id"))
                    .last("LIMIT 1"));
            if (order == null) {
                order = new OrderInfo();
                order.setTenantId(requireTenant());
                order.setExternalOrderId(text(node, "id"));
                order.setPlatform("shopify");
            }
            order.setExternalOrderNumber(text(node, "name"));
            order.setCustomerEmail(node.path("customer").path("email").asText(null));
            order.setCustomerName(node.path("customer").path("displayName").asText(null));
            order.setOrderStatus(node.path("displayFulfillmentStatus").asText("unknown").toLowerCase());
            order.setPaymentStatus(node.path("displayFinancialStatus").asText("unknown").toLowerCase());
            order.setFulfillmentStatus(node.path("displayFulfillmentStatus").asText("unknown").toLowerCase());
            order.setCurrency(node.path("totalPriceSet").path("shopMoney").path("currencyCode").asText("USD"));
            order.setTotalAmount(decimal(node.path("totalPriceSet").path("shopMoney").path("amount").asText("0")));
            order.setRefundedAmount(BigDecimal.ZERO);
            order.setItemCount(node.path("lineItems").path("nodes").size());
            order.setTotalQuantity(order.getItemCount());
            order.setOrderItems(node.path("lineItems").path("nodes").toString());
            order.setPlacedAt(LocalDateTime.now());
            order.setSyncedAt(LocalDateTime.now());
            order.setSyncSource("SHOPIFY");
            if (order.getId() == null) {
                orderMapper.insert(order);
            } else {
                orderMapper.updateById(order);
            }
            count++;
        }
        return count;
    }

    private String syncQuery() {
        return """
                {
                  products(first: 20) { nodes { id title handle description vendor productType } }
                  customers(first: 20) { nodes { id email phone displayName numberOfOrders } }
                  orders(first: 20, sortKey: CREATED_AT, reverse: true) {
                    nodes {
                      id name displayFinancialStatus displayFulfillmentStatus
                      customer { email displayName }
                      totalPriceSet { shopMoney { amount currencyCode } }
                      lineItems(first: 10) { nodes { title quantity sku } }
                    }
                  }
                }
                """;
    }

    private Long requireTenant() {
        var tenantId = TenantContextHolder.get();
        if (tenantId == null) {
            throw new BusinessException(ErrorCode.TENANT_MISSING, "缺少租户上下文");
        }
        return tenantId;
    }

    private boolean isValidShopDomain(String value) {
        return value != null && value.matches("^[a-z0-9][a-z0-9-]*\\.myshopify\\.com$");
    }

    private String normalizeShopDomain(String value) {
        if (value == null) {
            return "";
        }
        return value.toLowerCase().replace("https://", "").replace("http://", "").replace("/", "").trim();
    }

    private String text(JsonNode node, String field) {
        var value = node.path(field);
        return value.isMissingNode() || value.isNull() ? null : value.asText();
    }

    private Integer intValue(JsonNode node, String field) {
        var value = node.path(field);
        return value.isNumber() ? value.asInt() : 0;
    }

    private BigDecimal decimal(String value) {
        try {
            return new BigDecimal(value);
        } catch (Exception e) {
            return BigDecimal.ZERO;
        }
    }

    private IntegrationDtos.ShopifyJobVO toJobVO(ShopifySyncJob j) {
        return new IntegrationDtos.ShopifyJobVO(j.getId(), j.getShopDomain(), j.getResource(), j.getCursorValue(),
                j.getStatus(), j.getAttempts(), j.getLastError(), j.getNextRunAt(), j.getLastRunAt(),
                j.getImportedCount(), j.getThrottleStatusJson());
    }

    private IntegrationDtos.ShopifyBulkOperationVO toBulkVO(ShopifyBulkOperation row) {
        return new IntegrationDtos.ShopifyBulkOperationVO(row.getId(), row.getShopDomain(), row.getResource(),
                row.getExternalOperationId(), row.getStatus(), row.getObjectCount() == null ? 0 : row.getObjectCount(),
                row.getFileSize(), row.getResultUrlEncrypted() != null, row.getErrorCode(), row.getStartedAt(),
                row.getCompletedAt());
    }

    private record SyncResult(String resource, int imported) {
    }

}

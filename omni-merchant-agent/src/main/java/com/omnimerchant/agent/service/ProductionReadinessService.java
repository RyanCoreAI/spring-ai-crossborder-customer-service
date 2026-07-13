package com.omnimerchant.agent.service;

import com.omnimerchant.agent.dto.GovernanceDtos;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.omnimerchant.agent.dto.CommerceDtos;
import com.omnimerchant.agent.entity.AgentIdempotencyGuard;
import com.omnimerchant.agent.entity.DataRetentionPolicy;
import com.omnimerchant.agent.entity.SloPolicy;
import com.omnimerchant.agent.entity.SupportRolePolicy;
import com.omnimerchant.agent.mapper.AgentIdempotencyGuardMapper;
import com.omnimerchant.agent.mapper.DataRetentionPolicyMapper;
import com.omnimerchant.agent.mapper.SloPolicyMapper;
import com.omnimerchant.agent.mapper.SupportRolePolicyMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ProductionReadinessService {

    private final SupportRolePolicyMapper rolePolicyMapper;
    private final DataRetentionPolicyMapper retentionPolicyMapper;
    private final SloPolicyMapper sloPolicyMapper;
    private final AgentIdempotencyGuardMapper idempotencyGuardMapper;
    private final CommerceApprovalService approvalService;

    public GovernanceDtos.ProductionReadinessVO snapshot() {
        var roles = rolePolicies();
        var retentionRows = retentionPolicies();
        var security = List.of(
                readiness("tenant_fail_closed", "租户隔离 fail-closed", "IMPLEMENTED",
                        "TenantInterceptor + JWT tenantIds/platformAdmin + MyBatis tenant handler", "持续补跨租户回归用例", "LOW"),
                readiness("widget_session_token", "买家咨询组件短期 token", "IMPLEMENTED",
                        "/api/widget/session 签发短期 customerSessionToken，chat 校验 tenant/conversation", "增加渠道级密钥轮换", "LOW"),
                readiness("tool_approval_gate", "高风险工具审批流", "IMPLEMENTED",
                        "return_request / commerce_action_request；退款、补发、改地址、取消订单不由 AI 直接执行", "接入真实外部写操作前补幂等键和回滚说明", "MEDIUM"),
                readiness("rbac_abac", "RBAC/ABAC 权限模型", "PARTIAL",
                        roles.isEmpty() ? "当前区分 platformAdmin、租户 membership 和后台 JWT；细粒度页面/动作权限仍是路线图" : "support_role_policy 已声明页面、工具和审批权限策略",
                        "继续把 role policy 接入 method-level 权限和前端按钮级权限", "MEDIUM"),
                readiness("pii_redaction", "PII 脱敏与 trace 边界", "PARTIAL",
                        "trace/eval 默认记录摘要和元数据；demo profile 才允许完整 transcript", "补 DLP 规则和导出脱敏测试", "MEDIUM"),
                readiness("sso_scim", "OIDC/SAML/SCIM 企业身份", "ROADMAP",
                        "文档和 readiness 中标注，不伪装已接通", "按 Auth0/Keycloak/OIDC 做独立 profile", "LOW")
        );
        var shopify = List.of(
                shopify("oauth_install", "OAuth 安装流", "IMPLEMENTED", "/api/integrations/shopify/install + oauth/callback HMAC/state", "opt-in", "补真实 dev-store smoke"),
                shopify("webhook_hmac_dedupe", "Webhook HMAC、去重与乱序保护", "IMPLEMENTED", "X-Shopify-Hmac-Sha256 + X-Shopify-Webhook-Id 去重 + resource checkpoint 版本保护", "default-on", "补真实 dev-store 乱序事件 smoke"),
                shopify("cursor_sync", "GraphQL cursor、bulk sync 与 throttle backoff", "IMPLEMENTED", "shopify_sync_job cursor/throttleStatus/nextRunAt + bulk operation tracking", "opt-in", "补真实 dev-store bulk result smoke"),
                shopify("dlq_replay", "失败队列和重放", "IMPLEMENTED", "FAILED/DEAD 状态 + replay endpoint", "manual", "补重放权限细分"),
                shopify("gdpr_webhooks", "GDPR mandatory webhooks", "IMPLEMENTED", "customers/data_request、customers/redact、shop/redact 已验签、持久化并执行本地数据请求/脱敏流程", "default-on", "补 Shopify App Review 环境验证"),
                shopify("external_write_actions", "真实退款/取消/改地址外部写操作", "NOT_ENABLED", "默认只进入内部审批流，AI 不直接执行外部写操作", "disabled", "接真实写操作前补审批、幂等、审计和回滚")
        );
        var runbooks = List.of(
                runbook("LLM provider unavailable", "MODEL_UNAVAILABLE / LLM_TIMEOUT spike", "切换降级模型或转人工，保留 traceId", "平台管理员", "POLICY_DECLARED"),
                runbook("Redis rate limiter unavailable", "RATE_LIMIT failure 或 fallback quota exhausted", "付费 LLM endpoint fail-closed，检查 Redis 和本地 fallback 配额", "后端/SRE", "POLICY_DECLARED"),
                runbook("Shopify throttle/backlog", "webhookBacklog、deadShopifyJobs 或 throttleStatus 异常", "暂停重试，检查 nextRunAt，按店铺限流恢复", "集成负责人", "POLICY_DECLARED"),
                runbook("RAG poisoning spike", "poisoning block rate 或 quarantined docs 激增", "暂停索引、人工审核、从 RAG Workbench 复现 query", "知识库管理员", "POLICY_DECLARED")
        );
        var retention = retentionRows.isEmpty() ? defaultRetentionPolicies() : retentionRows;
        return new GovernanceDtos.ProductionReadinessVO(security, roles, approvalService.policies(), retention,
                shopify, runbooks, recentAgentGuards(),
                List.of("不承诺 App Store embedded admin UI 已完成", "不执行真实退款/取消/改地址外部写操作",
                        "不把 WhatsApp/Instagram/Facebook/SMS/Voice 显示为已接通"), LocalDateTime.now());
    }

    public List<GovernanceDtos.SupportRolePolicyVO> rolePolicies() {
        return rolePolicyMapper.selectList(new LambdaQueryWrapper<SupportRolePolicy>()
                        .orderByAsc(SupportRolePolicy::getRoleKey))
                .stream().map(this::toRoleView).toList();
    }

    public List<GovernanceDtos.DataRetentionPolicyVO> retentionPolicies() {
        return retentionPolicyMapper.selectList(new LambdaQueryWrapper<DataRetentionPolicy>()
                        .orderByAsc(DataRetentionPolicy::getDataSet))
                .stream().map(this::toRetentionView).toList();
    }

    public List<GovernanceDtos.SloPolicyVO> sloPolicies() {
        return sloPolicyMapper.selectList(new LambdaQueryWrapper<SloPolicy>()
                        .orderByDesc(SloPolicy::getActive).orderByAsc(SloPolicy::getSloKey))
                .stream().map(this::toSloView).toList();
    }

    public List<GovernanceDtos.AgentGuardVO> recentAgentGuards() {
        return idempotencyGuardMapper.selectList(new LambdaQueryWrapper<AgentIdempotencyGuard>()
                        .orderByDesc(AgentIdempotencyGuard::getLastSeenAt).last("LIMIT 20"))
                .stream().map(this::toGuardView).toList();
    }

    private List<GovernanceDtos.DataRetentionPolicyVO> defaultRetentionPolicies() {
        return List.of(
                retention("conversation/chat_message", 180, "默认摘要脱敏", "ROADMAP", "ROADMAP", "POLICY_DECLARED", "需要补后台租户配置和清理 job"),
                retention("agent_run/agent_step/tool_call_log", 90, "默认不保存完整 prompt/tool result", "ROADMAP", "ROADMAP", "POLICY_DECLARED", "用于可观测与回放，生产应按租户缩短保留期"),
                retention("agent_eval_*", 365, "评测输入应使用脱敏样例", "SUPPORTED_BY_REPORTS", "ROADMAP", "PARTIAL", "deterministic eval 已可生成报告，删除/导出流程待补"),
                retention("audit_event", 730, "仅记录操作摘要和资源 ID", "SUPPORTED_BY_API", "RESTRICTED", "IMPLEMENTED", "关键审计日志不提供普通管理员删除入口")
        );
    }

    private GovernanceDtos.SupportRolePolicyVO toRoleView(SupportRolePolicy row) {
        return new GovernanceDtos.SupportRolePolicyVO(row.getId(), row.getRoleKey(), row.getRoleLabel(),
                row.getPermissionsJson(), row.getToolPolicyJson(),
                row.getApprovalLimit() == null ? null : row.getApprovalLimit().toPlainString(), row.getStatus());
    }

    private GovernanceDtos.DataRetentionPolicyVO toRetentionView(DataRetentionPolicy row) {
        return new GovernanceDtos.DataRetentionPolicyVO(row.getDataSet(), row.getRetentionDays(), row.getMaskingDefault(),
                row.getExportSupport(), row.getDeletionSupport(), row.getStatus(), row.getNotes());
    }

    private GovernanceDtos.SloPolicyVO toSloView(SloPolicy row) {
        return new GovernanceDtos.SloPolicyVO(row.getId(), row.getSloKey(), row.getSloLabel(), row.getTargetValue(),
                row.getUnit(), row.getWindowMinutes(), row.getSeverityOnBreach(), row.getRunbook(), row.getActive());
    }

    private GovernanceDtos.AgentGuardVO toGuardView(AgentIdempotencyGuard row) {
        return new GovernanceDtos.AgentGuardVO(row.getId(), row.getConversationUuid(), row.getGuardKey(), row.getToolName(),
                row.getRequestHash(), row.getStatus(), row.getFirstSeenAt(), row.getLastSeenAt());
    }

    private GovernanceDtos.ReadinessControlVO readiness(String key, String label, String status,
                                                       String evidence, String nextStep, String riskLevel) {
        return new GovernanceDtos.ReadinessControlVO(key, label, status, evidence, nextStep, riskLevel);
    }

    private GovernanceDtos.DataRetentionPolicyVO retention(String dataSet, Integer days, String masking,
                                                          String exportSupport, String deletionSupport,
                                                          String status, String notes) {
        return new GovernanceDtos.DataRetentionPolicyVO(dataSet, days, masking, exportSupport, deletionSupport, status, notes);
    }

    private GovernanceDtos.ShopifyCapabilityVO shopify(String key, String label, String status,
                                                      String evidence, String defaultMode, String nextStep) {
        return new GovernanceDtos.ShopifyCapabilityVO(key, label, status, evidence, defaultMode, nextStep);
    }

    private GovernanceDtos.RunbookVO runbook(String incident, String triggerSignal, String firstAction,
                                            String owner, String status) {
        return new GovernanceDtos.RunbookVO(incident, triggerSignal, firstAction, owner, status);
    }
}

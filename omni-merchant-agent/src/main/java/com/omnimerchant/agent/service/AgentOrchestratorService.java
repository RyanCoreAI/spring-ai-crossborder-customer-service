package com.omnimerchant.agent.service;

import com.omnimerchant.agent.dto.GovernanceDtos;

import com.omnimerchant.agent.dto.CommerceDtos;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;

@Service
public class AgentOrchestratorService {

    public SpecialistPlan plan(String intent, String userMessage) {
        var normalizedIntent = intent == null ? "UNKNOWN" : intent.toUpperCase(Locale.ROOT);
        var message = userMessage == null ? "" : userMessage.toLowerCase(Locale.ROOT);
        var angry = message.contains("angry") || message.contains("furious") || message.contains("投诉")
                || message.contains("生气") || message.contains("糟糕");
        return switch (normalizedIntent) {
            case "ORDER_STATUS" -> new SpecialistPlan("order", "订单智能体",
                    angry ? List.of("queryOrder", "escalateToHuman") : List.of("queryOrder"),
                    "MEDIUM", true, false, angry);
            case "LOGISTICS" -> new SpecialistPlan("order", "订单/物流智能体",
                    angry ? List.of("queryOrder", "trackLogistics", "escalateToHuman")
                            : List.of("queryOrder", "trackLogistics"),
                    angry ? "HIGH" : "MEDIUM", false, false, angry);
            case "RETURN_REFUND", "CANCEL_ORDER", "ADDRESS_CHANGE" -> new SpecialistPlan("return", "退货/退款智能体",
                    List.of("queryOrder", "createReturnRequest", "requestRefundOrReplacement", "requestAddressChange", "escalateToHuman"),
                    "HIGH", true, true, true);
            case "PRODUCT_ADVICE" -> new SpecialistPlan("product", "商品顾问智能体",
                    List.of("searchProductCatalog"), "LOW", false, false, false);
            case "POLICY_QA" -> new SpecialistPlan("policy_rag", "政策 RAG 智能体",
                    List.of("refundPolicyRAG"), "MEDIUM", false, false, false);
            case "COMPLAINT", "HUMAN_REQUEST" -> new SpecialistPlan("handoff", "人工交接智能体",
                    List.of("escalateToHuman"), "HIGH", false, false, true);
            default -> new SpecialistPlan("triage", "意图分流智能体",
                    List.of(), "LOW", false, false, false);
        };
    }

    public GovernanceDtos.AgentWorkflowVO describeWorkflow() {
        var nodes = List.of(
                node("triage", "意图分流", "识别语言、意图、风险和是否需要身份校验", "detectLanguage, classifyIntent", "IMPLEMENTED"),
                node("order", "订单智能体", "订单查询、身份校验和物流状态解释", "queryOrder, trackLogistics", "BACKED_BY_TOOLS"),
                node("return", "退货/退款智能体", "创建审批请求，不直接执行外部退款或取消", "createReturnRequest, requestRefundOrReplacement, requestAddressChange", "APPROVAL_GATED"),
                node("product", "商品顾问智能体", "商品搜索、库存和预算约束推荐", "searchProductCatalog", "BACKED_BY_TOOLS"),
                node("policy_rag", "政策 RAG 智能体", "政策检索、引用和证据充分度判断", "refundPolicyRAG", "BACKED_BY_RAG_WORKBENCH"),
                node("risk", "安全智能体", "prompt injection、越权、敏感信息和危险动作拦截", "safetyGate", "ENFORCED"),
                node("handoff", "人工交接智能体", "生成工单、摘要和 SLA 队列", "escalateToHuman", "BACKED_BY_TICKETS"),
                node("qa", "质检智能体", "关闭会话后进入 QA 队列和评测回归", "qaReview", "PARTIAL")
        );
        var policies = List.of(
                new GovernanceDtos.AgentPolicyVO("tool_allowlist", "不同 specialist 只能调用自己的工具集合", "AgentOrchestratorService.plan"),
                new GovernanceDtos.AgentPolicyVO("approval_gate", "退款、补发、改地址、取消订单进入人工审批", "commerce_action_request / return_request"),
                new GovernanceDtos.AgentPolicyVO("tenant_fail_closed", "缺租户上下文、跨租户访问和 widget token mismatch 拒绝", "TenantInterceptor + JWT claims"),
                new GovernanceDtos.AgentPolicyVO("trace_replay", "每次路由、工具调用、RAG、失败归因可回放", "agent_run / agent_step / tool_call_log")
        );
        return new GovernanceDtos.AgentWorkflowVO("Supervisor-Worker 客服工作流", "ORCHESTRATOR_BACKBONE", nodes, policies);
    }

    private GovernanceDtos.AgentNodeVO node(String key, String label, String responsibility, String tools, String status) {
        return new GovernanceDtos.AgentNodeVO(key, label, responsibility, tools, status);
    }

    public record SpecialistPlan(
            String specialistKey,
            String specialistLabel,
            List<String> toolAllowlist,
            String riskLevel,
            boolean requiresIdentityVerification,
            boolean requiresApproval,
            boolean recommendHumanHandoff) {
    }
}

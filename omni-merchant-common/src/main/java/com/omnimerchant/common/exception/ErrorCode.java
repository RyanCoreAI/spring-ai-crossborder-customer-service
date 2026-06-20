package com.omnimerchant.common.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    SUCCESS("200", "操作成功"),
    BAD_REQUEST("400", "请求参数错误"),
    UNAUTHORIZED("401", "未授权访问"),
    FORBIDDEN("403", "禁止访问"),
    NOT_FOUND("404", "资源不存在"),
    INTERNAL_ERROR("500", "服务器内部错误"),

    // 多租户
    TENANT_NOT_FOUND("T001", "租户不存在"),
    TENANT_MISSING("T002", "缺少租户标识"),

    // AI Agent
    AGENT_CALL_FAILED("A001", "AI 调用失败"),
    INTENT_NOT_RECOGNIZED("A002", "意图未识别"),
    TOOL_EXECUTION_FAILED("A003", "工具执行失败"),

    // 知识库
    RAG_RETRIEVAL_FAILED("K001", "知识检索失败"),
    DOCUMENT_INGEST_FAILED("K002", "文档摄入失败"),

    // 限流 & 计费
    RATE_LIMITED("R001", "请求频率超限，请稍后再试"),
    BUDGET_EXCEEDED("R002", "月度 Token 预算已用尽，请联系管理员升级套餐"),
    CONCURRENT_LIMITED("R003", "并发会话数超限"),
    TENANT_DISABLED("R004", "租户已停用或欠费"),

    // 渠道
    CHANNEL_AUTH_FAILED("C001", "渠道授权失败"),
    CHANNEL_API_ERROR("C002", "渠道 API 调用异常");

    private final String code;
    private final String message;
}

package com.omnimerchant.agent.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("tool_call_log")
public class ToolCallLog {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private String traceId;
    private String spanId;
    private Long tenantId;
    private String conversationUuid;
    private String messageUuid;
    private String toolCallId;
    private String toolName;
    private String toolVersion;
    private String params;
    private String paramsHash;
    private Integer success;
    private String result;
    private Integer resultSizeBytes;
    private String errorCode;
    private String errorMessage;
    private String errorStack;
    private LocalDateTime startedAt;
    private LocalDateTime endedAt;
    private Integer latencyMs;
    private Integer cacheHit;
    private Integer retryCount;
    private Integer isRetry;
    private String originalCallId;
    private Integer iterationIndex;
    private String triggeredByModel;
    private String clientIp;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}

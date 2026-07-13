package com.omnimerchant.agent.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("translation_event")
public class TranslationEvent {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private String conversationUuid;
    private String messageUuid;
    private String traceId;
    private String direction;
    private String sourceLanguage;
    private String targetLanguage;
    private BigDecimal detectionConfidence;
    private String sourceTextRedacted;
    private String translatedTextRedacted;
    private String sourceHash;
    private String translatedHash;
    private String provider;
    private String model;
    private String status;
    private Integer latencyMs;
    private String fallbackReason;
    private LocalDateTime createdAt;
}

package com.omnimerchant.agent.language;

import lombok.Builder;
import lombok.Data;

/**
 * 多语言预处理后的消息体。
 */
@Data
@Builder
public class ProcessedMessage {

    /** 用户原始输入 */
    private String originalText;

    /** 检测到的语言 ISO 639-1 */
    private String detectedLanguage;

    /** 翻译为英语后的文本（用于 LLM 处理） */
    private String translatedText;

    /** 是否需要翻译（源语言不是英语） */
    private boolean needsTranslation;

    /** 检测置信度 [0, 1]（Lingua 不直接提供，基于规则估算） */
    private double confidence;
}

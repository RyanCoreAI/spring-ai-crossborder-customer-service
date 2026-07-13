package com.omnimerchant.agent.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("agent_conversation_state")
public class AgentConversationState {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private String conversationUuid;
    private String state;
    private String lastTraceId;
    private String lastReason;
    @Version
    private Integer version;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

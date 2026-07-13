package com.omnimerchant.agent.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("agent_state_transition")
public class AgentStateTransition {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private String conversationUuid;
    private String traceId;
    private String fromState;
    private String toState;
    private String triggerType;
    private String triggerName;
    private String reasonRedacted;
    private LocalDateTime createdAt;
}

package com.omnimerchant.agent.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("agent_idempotency_guard")
public class AgentIdempotencyGuard {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long tenantId;
    private String conversationUuid;
    private String guardKey;
    private String toolName;
    private String requestHash;
    private String status;
    private LocalDateTime firstSeenAt;
    private LocalDateTime lastSeenAt;
}

package com.omnimerchant.agent.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("channel_sync_cursor")
public class ChannelSyncCursor {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private Long channelAccountId;
    private String cursorEncrypted;
    private LocalDateTime updatedAt;
}

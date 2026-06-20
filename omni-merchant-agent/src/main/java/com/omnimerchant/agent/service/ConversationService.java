package com.omnimerchant.agent.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.omnimerchant.agent.dto.ConversationVO;
import com.omnimerchant.agent.entity.ChatMessage;
import com.omnimerchant.agent.entity.Conversation;

import java.util.List;

public interface ConversationService extends IService<Conversation> {

    IPage<ConversationVO> listConversations(Long tenantId, Integer status, int page, int size);

    ConversationVO getByUuid(String conversationUuid);

    List<ChatMessage> getMessages(String conversationUuid);
}

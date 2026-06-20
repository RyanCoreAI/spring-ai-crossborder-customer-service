package com.omnimerchant.agent.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.omnimerchant.agent.dto.ConversationVO;
import com.omnimerchant.agent.entity.ChatMessage;
import com.omnimerchant.agent.entity.Conversation;
import com.omnimerchant.agent.mapper.ChatMessageMapper;
import com.omnimerchant.agent.mapper.ConversationMapper;
import com.omnimerchant.agent.service.ConversationService;
import com.omnimerchant.common.exception.BusinessException;
import com.omnimerchant.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ConversationServiceImpl extends ServiceImpl<ConversationMapper, Conversation>
        implements ConversationService {

    private final ChatMessageMapper messageMapper;

    @Override
    public IPage<ConversationVO> listConversations(Long tenantId, Integer status, int page, int size) {
        var wrapper = new LambdaQueryWrapper<Conversation>()
                .eq(tenantId != null, Conversation::getTenantId, tenantId)
                .eq(status != null, Conversation::getStatus, status)
                .orderByDesc(Conversation::getStartedAt);
        var result = page(new Page<>(page, size), wrapper);
        return result.convert(this::toVO);
    }

    @Override
    public ConversationVO getByUuid(String conversationUuid) {
        var conv = getOne(new LambdaQueryWrapper<Conversation>()
                .eq(Conversation::getConversationUuid, conversationUuid));
        if (conv == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "会话不存在");
        }
        return toVO(conv);
    }

    @Override
    public List<ChatMessage> getMessages(String conversationUuid) {
        return messageMapper.selectList(new LambdaQueryWrapper<ChatMessage>()
                .eq(ChatMessage::getConversationUuid, conversationUuid)
                .orderByAsc(ChatMessage::getSeqNo));
    }

    private ConversationVO toVO(Conversation c) {
        var vo = new ConversationVO();
        BeanUtils.copyProperties(c, vo);
        vo.setStatusLabel(ConversationVO.statusLabel(c.getStatus()));
        return vo;
    }
}

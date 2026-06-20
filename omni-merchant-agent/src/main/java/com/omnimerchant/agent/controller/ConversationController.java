package com.omnimerchant.agent.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.omnimerchant.agent.dto.ConversationVO;
import com.omnimerchant.agent.entity.ChatMessage;
import com.omnimerchant.agent.service.ConversationService;
import com.omnimerchant.common.dto.R;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/conversations")
@RequiredArgsConstructor
public class ConversationController {

    private final ConversationService conversationService;

    @GetMapping
    public R<IPage<ConversationVO>> list(
            @RequestParam(required = false) Long tenantId,
            @RequestParam(required = false) Integer status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return R.ok(conversationService.listConversations(tenantId, status, page, size));
    }

    @GetMapping("/{conversationUuid}")
    public R<ConversationVO> getByUuid(@PathVariable String conversationUuid) {
        return R.ok(conversationService.getByUuid(conversationUuid));
    }

    @GetMapping("/{conversationUuid}/messages")
    public R<List<ChatMessage>> getMessages(@PathVariable String conversationUuid) {
        return R.ok(conversationService.getMessages(conversationUuid));
    }
}

package com.omnimerchant.config;

import com.omnimerchant.advice.GlobalExceptionHandler;
import com.omnimerchant.agent.controller.ChannelIntegrationController;
import com.omnimerchant.agent.service.ChannelIntegrationService;
import com.omnimerchant.agent.service.ChannelCredentialService;
import com.omnimerchant.common.exception.BusinessException;
import com.omnimerchant.common.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class ChannelSendControllerTest {
    @Test void missingStableKeyReturnsHttp400BeforeSending() throws Exception {
        var service=mock(ChannelIntegrationService.class);
        var mvc=MockMvcBuilders.standaloneSetup(new ChannelIntegrationController(service,mock(ChannelCredentialService.class)))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
        mvc.perform(post("/api/channels/1/send").contentType(MediaType.APPLICATION_JSON)
                .content("{\"conversationUuid\":\"conv\",\"message\":\"hello\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("400"));
        verifyNoInteractions(service);
    }
    @Test void keyReusedForDifferentPayloadReturnsHttp400() throws Exception {
        var service=mock(ChannelIntegrationService.class);
        when(service.send(anyLong(),any())).thenThrow(new BusinessException(ErrorCode.BAD_REQUEST,"幂等键已用于不同的发送内容"));
        var mvc=MockMvcBuilders.standaloneSetup(new ChannelIntegrationController(service,mock(ChannelCredentialService.class)))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
        mvc.perform(post("/api/channels/1/send").contentType(MediaType.APPLICATION_JSON)
                .content("{\"conversationUuid\":\"conv\",\"message\":\"hello\",\"idempotencyKey\":\"intent-1\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("400"));
    }
}

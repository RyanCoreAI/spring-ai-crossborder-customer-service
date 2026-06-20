package com.omnimerchant.agent.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.omnimerchant.agent.dto.CommerceDtos;
import com.omnimerchant.agent.service.AgentEvalRunnerService;
import com.omnimerchant.agent.service.CommercePlatformService;
import com.omnimerchant.agent.service.ReActAgentService;
import com.omnimerchant.agent.service.ShopifyIntegrationService;
import com.omnimerchant.common.util.JwtUtil;
import com.omnimerchant.tenant.context.TenantContextHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CommerceControllerTest {

    @Mock
    CommercePlatformService commerceService;
    @Mock
    ShopifyIntegrationService shopifyService;
    @Mock
    AgentEvalRunnerService evalRunnerService;
    @Mock
    ReActAgentService reActAgentService;

    private final JwtUtil jwtUtil = new JwtUtil("01234567890123456789012345678901", 86_400_000);
    private CommerceController controller;

    @BeforeEach
    void setUp() {
        TenantContextHolder.clear();
        controller = new CommerceController(commerceService, shopifyService, evalRunnerService,
                reActAgentService, new ObjectMapper(), jwtUtil);
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void widgetChatWithoutTokenShouldReturn401() throws IOException {
        var servletResponse = new MockHttpServletResponse();

        var emitter = controller.widgetChat(null, widgetRequest("OM-FASHION", "conv-1"), servletResponse);

        assertThat(emitter).isNull();
        assertThat(servletResponse.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED.value());
        assertThat(servletResponse.getContentType()).startsWith("application/json");
        assertThat(servletResponse.getContentAsString()).contains("\"code\":\"401\"");
        verifyNoInteractions(reActAgentService);
    }

    @Test
    void widgetChatExpiredTokenShouldReturn401() throws IOException {
        var token = jwtUtil.generateWidgetCustomerToken("ava@example.com", 7L, "OM-FASHION", "conv-1",
                new Date(System.currentTimeMillis() - 1_000));
        var servletResponse = new MockHttpServletResponse();

        var emitter = controller.widgetChat("Bearer " + token, widgetRequest("OM-FASHION", "conv-1"), servletResponse);

        assertThat(emitter).isNull();
        assertThat(servletResponse.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED.value());
        assertThat(servletResponse.getContentAsString()).contains("\"code\":\"401\"");
        verifyNoInteractions(reActAgentService);
    }

    @Test
    void widgetChatTenantMismatchShouldReturn403() throws IOException {
        var token = widgetToken("OM-FASHION", "conv-1");
        var servletResponse = new MockHttpServletResponse();

        var emitter = controller.widgetChat("Bearer " + token,
                widgetRequest("OM-ELECTRONICS", "conv-1"), servletResponse);

        assertThat(emitter).isNull();
        assertThat(servletResponse.getStatus()).isEqualTo(HttpStatus.FORBIDDEN.value());
        assertThat(servletResponse.getContentAsString()).contains("\"code\":\"403\"");
        verifyNoInteractions(reActAgentService);
    }

    @Test
    void widgetChatConversationMismatchShouldReturn403() throws IOException {
        var token = widgetToken("OM-FASHION", "conv-1");
        var servletResponse = new MockHttpServletResponse();

        var emitter = controller.widgetChat("Bearer " + token,
                widgetRequest("OM-FASHION", "conv-2"), servletResponse);

        assertThat(emitter).isNull();
        assertThat(servletResponse.getStatus()).isEqualTo(HttpStatus.FORBIDDEN.value());
        assertThat(servletResponse.getContentAsString()).contains("\"code\":\"403\"");
        verifyNoInteractions(reActAgentService);
    }

    @Test
    void widgetChatValidTokenShouldEnterStreamAndClearTenantContext() throws IOException {
        var token = widgetToken("OM-FASHION", "conv-1");
        when(reActAgentService.chat(eq(7L), eq("conv-1"), eq("hello"), eq("UNKNOWN")))
                .thenReturn(Flux.empty());

        var emitter = controller.widgetChat("Bearer " + token,
                widgetRequest("OM-FASHION", "conv-1"), new MockHttpServletResponse());

        assertThat(emitter).isInstanceOf(SseEmitter.class);
        assertThat(TenantContextHolder.get()).isNull();
        verify(reActAgentService).chat(eq(7L), eq("conv-1"), eq("hello"), eq("UNKNOWN"));
    }

    private String widgetToken(String tenantCode, String conversationUuid) {
        return jwtUtil.generateWidgetCustomerToken("ava@example.com", 7L, tenantCode, conversationUuid, 7_200_000);
    }

    private CommerceDtos.WidgetChatRequest widgetRequest(String tenantCode, String conversationUuid) {
        return new CommerceDtos.WidgetChatRequest(tenantCode, conversationUuid, "hello", "UNKNOWN");
    }
}

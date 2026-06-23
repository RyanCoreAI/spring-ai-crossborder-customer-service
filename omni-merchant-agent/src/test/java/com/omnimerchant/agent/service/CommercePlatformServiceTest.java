package com.omnimerchant.agent.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.omnimerchant.agent.dto.CommerceDtos;
import com.omnimerchant.agent.entity.Conversation;
import com.omnimerchant.agent.mapper.AgentEvalCaseMapper;
import com.omnimerchant.agent.mapper.ConversationMapper;
import com.omnimerchant.agent.mapper.CustomerMapper;
import com.omnimerchant.agent.mapper.EscalationRecordMapper;
import com.omnimerchant.agent.mapper.OrderInfoMapper;
import com.omnimerchant.agent.mapper.ProductMapper;
import com.omnimerchant.agent.mapper.ReturnRequestMapper;
import com.omnimerchant.agent.mapper.ToolCallLogMapper;
import com.omnimerchant.agent.mapper.WebhookEventMapper;
import com.omnimerchant.common.util.JwtUtil;
import com.omnimerchant.tenant.context.TenantContextHolder;
import com.omnimerchant.tenant.entity.Tenant;
import com.omnimerchant.tenant.mapper.TenantMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CommercePlatformServiceTest {

    @Mock
    CustomerMapper customerMapper;
    @Mock
    OrderInfoMapper orderMapper;
    @Mock
    ProductMapper productMapper;
    @Mock
    EscalationRecordMapper escalationMapper;
    @Mock
    ToolCallLogMapper toolCallLogMapper;
    @Mock
    ReturnRequestMapper returnRequestMapper;
    @Mock
    WebhookEventMapper webhookEventMapper;
    @Mock
    AgentEvalCaseMapper evalCaseMapper;
    @Mock
    ConversationMapper conversationMapper;
    @Mock
    TenantMapper tenantMapper;

    private final JwtUtil jwtUtil = new JwtUtil("01234567890123456789012345678901", 86_400_000);
    private CommercePlatformService service;

    @BeforeEach
    void setUp() {
        TenantContextHolder.clear();
        service = new CommercePlatformService(
                customerMapper,
                orderMapper,
                productMapper,
                escalationMapper,
                toolCallLogMapper,
                returnRequestMapper,
                webhookEventMapper,
                evalCaseMapper,
                conversationMapper,
                tenantMapper,
                new ObjectMapper(),
                jwtUtil);
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void createWidgetSessionShouldReturnTenantBoundCustomerToken() {
        var tenant = new Tenant();
        tenant.setId(7L);
        tenant.setTenantCode("OM-FASHION");
        tenant.setDefaultLang("en");
        tenant.setWelcomeMessage("Welcome");
        when(tenantMapper.selectOne(any())).thenReturn(tenant);
        when(conversationMapper.insert(any(Conversation.class))).thenReturn(1);

        var response = service.createWidgetSession(new CommerceDtos.WidgetSessionRequest(
                "OM-FASHION", "ava@example.com", "Ava", "en"));

        assertThat(response.customerSessionToken()).isNotBlank();
        assertThat(response.expiresAt()).isNotBlank();
        assertThat(Instant.parse(response.expiresAt())).isAfter(Instant.now().plusSeconds(7_000));
        assertThat(response.tenantId()).isEqualTo(7L);
        assertThat(response.tenantCode()).isEqualTo("OM-FASHION");
        assertThat(response.welcomeMessage()).isEqualTo("Welcome");
        assertThat(TenantContextHolder.get()).isNull();

        var principal = jwtUtil.parseWidgetCustomerToken(response.customerSessionToken());
        assertThat(principal.subject()).isEqualTo("ava@example.com");
        assertThat(principal.tenantId()).isEqualTo(7L);
        assertThat(principal.tenantCode()).isEqualTo("OM-FASHION");
        assertThat(principal.conversationUuid()).isEqualTo(response.conversationUuid());

        var captor = ArgumentCaptor.forClass(Conversation.class);
        verify(conversationMapper).insert(captor.capture());
        assertThat(captor.getValue().getTenantId()).isEqualTo(7L);
        assertThat(captor.getValue().getConversationUuid()).isEqualTo(response.conversationUuid());
    }
}

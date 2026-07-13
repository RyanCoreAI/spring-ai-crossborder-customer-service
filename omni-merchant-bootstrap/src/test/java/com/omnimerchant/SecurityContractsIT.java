package com.omnimerchant;

import com.omnimerchant.agent.advisor.TokenUsageProducer;
import com.omnimerchant.agent.service.ReActAgentService;
import com.omnimerchant.agent.dto.ChatStreamEvent;
import com.omnimerchant.common.constant.Constants;
import com.omnimerchant.common.util.JwtUtil;
import org.junit.jupiter.api.Test;
import org.springframework.ai.anthropic.AnthropicChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import reactor.core.publisher.Flux;

import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(properties = {
        "admin.email=ci-admin@example.com",
        "admin.password=ci-password",
        "admin.jwt-secret=01234567890123456789012345678901",
        "omnimerchant.integrations.encryption-key=01234567890123456789012345678901",
        "spring.ai.openai.api-key=ci-placeholder",
        "spring.ai.anthropic.api-key=ci-placeholder",
        "rocketmq.name-server=127.0.0.1:9876",
        "omnimerchant.message.enabled=false"
})
@AutoConfigureMockMvc
class SecurityContractsIT {

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("omni_merchant")
            .withUsername("omnimerchant")
            .withPassword("test-mysql-password")
            .withEnv("MYSQL_ROOT_HOST", "%");

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"))
            .withDatabaseName("omni_merchant")
            .withUsername("omnimerchant")
            .withPassword("test-postgres-password");

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7-alpine")
            .withExposedPorts(6379);

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JwtUtil jwtUtil;

    @MockitoBean
    ReActAgentService reActAgentService;

    @MockitoBean
    TokenUsageProducer tokenUsageProducer;

    @MockitoBean(name = "openAiChatModel")
    OpenAiChatModel openAiChatModel;

    @MockitoBean(name = "deepSeekChatModel")
    OpenAiChatModel deepSeekChatModel;

    @MockitoBean(name = "anthropicChatModel")
    AnthropicChatModel anthropicChatModel;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.druid.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.druid.username", MYSQL::getUsername);
        registry.add("spring.datasource.druid.password", MYSQL::getPassword);
        registry.add("spring.datasource.druid.driver-class-name", MYSQL::getDriverClassName);
        registry.add("spring.flyway.url", MYSQL::getJdbcUrl);
        registry.add("spring.flyway.user", () -> "root");
        registry.add("spring.flyway.password", MYSQL::getPassword);
        registry.add("omnimerchant.pgvector.url", POSTGRES::getJdbcUrl);
        registry.add("omnimerchant.pgvector.username", POSTGRES::getUsername);
        registry.add("omnimerchant.pgvector.password", POSTGRES::getPassword);
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
    }

    @Test
    void chatWithoutTokenShouldReturn401BeforeService() throws Exception {
        mockMvc.perform(post("/api/chat/stream")
                        .header(Constants.HEADER_TENANT_ID, "7")
                        .contentType("application/json")
                        .content(chatBody()))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(reActAgentService);
    }

    @Test
    void chatTenantMismatchShouldReturn403BeforeService() throws Exception {
        mockMvc.perform(post("/api/chat/stream")
                        .header("Authorization", "Bearer " + tenantToken(7L))
                        .header(Constants.HEADER_TENANT_ID, "8")
                        .contentType("application/json")
                        .content(chatBody()))
                .andExpect(status().isForbidden());

        verifyNoInteractions(reActAgentService);
    }

    @Test
    void chatTenantMembershipShouldReachStreamingService() throws Exception {
        when(reActAgentService.chatEvents(eq(7L), eq("conv-1"), eq("hello"), eq("UNCLEAR")))
                .thenReturn(Flux.just(ChatStreamEvent.finalAnswer("ok")));

        MvcResult result = mockMvc.perform(post("/api/chat/stream")
                        .header("Authorization", "Bearer " + tenantToken(7L))
                        .header(Constants.HEADER_TENANT_ID, "7")
                        .contentType("application/json")
                        .content(chatBody()))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(result))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("ok")));
    }

    @Test
    void widgetChatWithoutCustomerTokenShouldReturn401BeforeService() throws Exception {
        mockMvc.perform(post("/api/widget/chat/stream")
                        .contentType("application/json")
                        .content(widgetChatBody("OM-FASHION", "conv-1")))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(reActAgentService);
    }

    @Test
    void widgetChatBadCustomerTokenShouldReturn401BeforeService() throws Exception {
        mockMvc.perform(post("/api/widget/chat/stream")
                        .header("Authorization", "Bearer bad-token")
                        .contentType("application/json")
                        .content(widgetChatBody("OM-FASHION", "conv-1")))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(reActAgentService);
    }

    @Test
    void widgetChatConversationMismatchShouldReturn403BeforeService() throws Exception {
        mockMvc.perform(post("/api/widget/chat/stream")
                        .header("Authorization", "Bearer " + widgetToken(7L, "OM-FASHION", "conv-1"))
                        .contentType("application/json")
                        .content(widgetChatBody("OM-FASHION", "conv-2")))
                .andExpect(status().isForbidden());

        verifyNoInteractions(reActAgentService);
    }

    @Test
    void widgetChatTenantMismatchShouldReturn403BeforeService() throws Exception {
        mockMvc.perform(post("/api/widget/chat/stream")
                        .header("Authorization", "Bearer " + widgetToken(7L, "OM-FASHION", "conv-1"))
                        .contentType("application/json")
                        .content(widgetChatBody("OM-ELECTRONICS", "conv-1")))
                .andExpect(status().isForbidden());

        verifyNoInteractions(reActAgentService);
    }

    @Test
    void widgetChatCustomerTokenShouldReachStreamingService() throws Exception {
        when(reActAgentService.chatEvents(eq(7L), eq("conv-1"), eq("hello"), eq("UNKNOWN")))
                .thenReturn(Flux.just(ChatStreamEvent.finalAnswer("widget-ok")));

        MvcResult result = mockMvc.perform(post("/api/widget/chat/stream")
                        .header("Authorization", "Bearer " + widgetToken(7L, "OM-FASHION", "conv-1"))
                        .contentType("application/json")
                        .content(widgetChatBody("OM-FASHION", "conv-1")))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(result))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("widget-ok")));
    }

    private String tenantToken(Long tenantId) {
        return jwtUtil.generateToken("owner@example.com", "TENANT_USER", List.of(tenantId), false);
    }

    private String widgetToken(Long tenantId, String tenantCode, String conversationUuid) {
        return jwtUtil.generateWidgetCustomerToken("ava@example.com", tenantId, tenantCode,
                conversationUuid, 7_200_000);
    }

    private String chatBody() {
        return """
                {"conversationUuid":"conv-1","message":"hello","intent":"UNCLEAR"}
                """;
    }

    private String widgetChatBody(String tenantCode, String conversationUuid) {
        return """
                {"tenantCode":"%s","conversationUuid":"%s","message":"hello","intent":"UNKNOWN"}
                """.formatted(tenantCode, conversationUuid);
    }
}

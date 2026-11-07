package com.omnimerchant.agent.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.omnimerchant.agent.entity.*;
import com.omnimerchant.agent.mapper.*;
import com.omnimerchant.channel.*;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class WechatWebhookPaginationTest {
    private final ChannelAccountMapper accounts = mock(ChannelAccountMapper.class);
    private final ChannelWebhookEventMapper events = mock(ChannelWebhookEventMapper.class);
    private final ChannelOutboxEventMapper outboxes = mock(ChannelOutboxEventMapper.class);
    private final ChannelSyncCursorMapper cursors = mock(ChannelSyncCursorMapper.class);
    private final ChannelIntegrationService integration = mock(ChannelIntegrationService.class);
    private final CredentialCipher cipher = mock(CredentialCipher.class);
    private final ChannelAdapter adapter = mock(ChannelAdapter.class);
    private final ChannelWebhookEvent event = new ChannelWebhookEvent();
    private final ChannelOutboxEvent outbox = new ChannelOutboxEvent();
    private final ChannelSyncCursor cursor = new ChannelSyncCursor();
    private WechatWebhookRuntimeService service;

    @BeforeEach
    void setUp() {
        for (var type : List.of(ChannelAccount.class, ChannelWebhookEvent.class, ChannelOutboxEvent.class,
                ChannelSyncCursor.class)) {
            TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), "pagination-test"), type);
        }
        var account = new ChannelAccount(); account.setId(2L); account.setTenantId(1L); account.setChannel("WECHAT_KF");
        event.setId(3L); event.setTenantId(1L); event.setChannelAccountId(2L); event.setAttempts(0);
        event.setEncryptedPayload("{\"body\":\"callback\",\"params\":{}}");
        cursor.setChannelAccountId(2L); cursor.setCursorEncrypted("0");
        when(events.update(isNull(), any())).thenReturn(1);
        when(events.selectById(3L)).thenReturn(event);
        when(accounts.selectById(2L)).thenReturn(account);
        when(cursors.selectOne(any())).thenReturn(cursor);
        when(outboxes.selectOne(any())).thenReturn(outbox);
        when(cipher.encrypt(anyString())).thenAnswer(call -> call.getArgument(0));
        when(cipher.decrypt(anyString())).thenAnswer(call -> call.getArgument(0));
        when(adapter.channel()).thenReturn("WECHAT_KF");
        when(integration.accountConfig(account)).thenReturn(new ChannelAccountConfig(2L, 1L, "WECHAT_KF", "test", "kf",
                "LIVE", true, true, false, null, null, null, null, null, "kf", null, null, Map.of()));
        service = new WechatWebhookRuntimeService(accounts, events, outboxes, cursors, integration,
                mock(ChannelCredentialService.class), cipher, new ObjectMapper(), List.of(adapter), Runnable::run);
    }

    @Test
    void pageBudgetKeepsTheEventPendingUntilAllPagesAreDrained() {
        var pulls = new AtomicInteger();
        when(adapter.pullMessages(any(), any(), any(), anyString(), anyString())).thenAnswer(call -> {
            int page = pulls.incrementAndGet();
            return new ChannelInboundBatch(List.of(), String.valueOf(page), page < 7);
        });
        service.processEvent(1L, 3L);
        assertThat(pulls).hasValue(5);
        assertThat(event.getStatus()).isEqualTo("RECEIVED");
        assertThat(event.getProcessedAt()).isNull();
        assertThat(event.getAttempts()).isZero();
        assertThat(outbox.getStatus()).isEqualTo("PENDING");
        assertThat(outbox.getPublishedAt()).isNull();
        assertThat(cursor.getCursorEncrypted()).isEqualTo("5");
        service.processEvent(1L, 3L);
        assertThat(pulls).hasValue(7);
        assertThat(event.getStatus()).isEqualTo("SUCCESS");
        assertThat(outbox.getStatus()).isEqualTo("PUBLISHED");
    }

    @Test
    void nonAdvancingCursorFailsInsteadOfSchedulingAnEndlessReplay() {
        when(adapter.pullMessages(any(), any(), any(), anyString(), anyString()))
                .thenReturn(new ChannelInboundBatch(List.of(), "0", true));
        service.processEvent(1L, 3L);
        assertThat(event.getStatus()).isEqualTo("FAILED");
        assertThat(event.getAttempts()).isEqualTo(1);
        assertThat(outbox.getStatus()).isEqualTo("PENDING");
        verify(adapter, times(1)).pullMessages(any(), any(), any(), anyString(), anyString());
    }
}

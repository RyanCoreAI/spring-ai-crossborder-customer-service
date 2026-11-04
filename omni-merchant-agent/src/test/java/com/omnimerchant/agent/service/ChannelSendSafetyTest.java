package com.omnimerchant.agent.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.omnimerchant.agent.dto.IntegrationDtos;
import com.omnimerchant.agent.entity.*;
import com.omnimerchant.agent.mapper.*;
import com.omnimerchant.agent.language.MultiLingualEngine;
import com.omnimerchant.channel.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ChannelSendSafetyTest {
    private final ChannelAccountMapper accounts=mock(ChannelAccountMapper.class);
    private final ChannelAdapter adapter=mock(ChannelAdapter.class);
    private final ChannelSendLedger ledger=mock(ChannelSendLedger.class);
    private final ChannelCredentialService credentials=mock(ChannelCredentialService.class);
    private final ChannelIntegrationService service=new ChannelIntegrationService(accounts,
            mock(ChannelConversationMapper.class),mock(ChannelMessageMapper.class),mock(ChannelDeliveryReceiptMapper.class),
            mock(ChannelCustomerIdentityMapper.class),mock(ConversationMapper.class),mock(ChatMessageMapper.class),
            new ObjectMapper(),List.of(adapter),credentials,mock(MultiLingualEngine.class),mock(TranslationEvidenceService.class),ledger);
    private final ChannelMessage message=new ChannelMessage();
    @BeforeEach void setup(){
        var account=new ChannelAccount();account.setId(1L);account.setTenantId(42L);account.setChannel("WECHAT_KF");
        when(accounts.selectById(1L)).thenReturn(account);when(adapter.channel()).thenReturn("WECHAT_KF");
        when(credentials.resolvedConfig(account)).thenReturn(Map.of("fixtureMode",true));
        message.setId(9L);message.setMessageUuid("stable-provider-id");message.setDeliveryStatus("SENDING");
    }
    private IntegrationDtos.ChannelSendRequest request(String key){
        return new IntegrationDtos.ChannelSendRequest("conv","thread","customer","hello",key);
    }
    @Test void unknownIntentNeverCallsProviderAgain(){
        when(ledger.reserve(anyLong(),anyString(),anyString(),anyString(),anyString()))
                .thenReturn(new ChannelSendLedger.Reservation(message,false));
        assertThat(service.send(1L,request("request-1")).deliveryStatus()).isEqualTo("UNKNOWN");
        verify(adapter,never()).sendMessage(any(),any());
    }
    @Test void receiptFailureAfterProviderAcceptsReturnsUnknownAndKeepsIntent(){
        when(ledger.reserve(anyLong(),anyString(),anyString(),anyString(),anyString()))
                .thenReturn(new ChannelSendLedger.Reservation(message,true));
        var accepted=new ChannelSendResult(true,"provider-1","SENT","FIXTURE",null,null);
        when(adapter.sendMessage(any(),any())).thenReturn(accepted);
        doThrow(new IllegalStateException("database unavailable")).when(ledger).complete(message,accepted);
        var result=service.send(1L,request("request-1"));
        assertThat(result.deliveryStatus()).isEqualTo("UNKNOWN");assertThat(result.messageId()).isEqualTo(9L);
        var order=inOrder(ledger,adapter);order.verify(ledger).reserve(anyLong(),anyString(),anyString(),anyString(),anyString());
        order.verify(adapter).sendMessage(any(),argThat(out->out.idempotencyKey().equals("stable-provider-id")));
        order.verify(ledger).complete(message,accepted);order.verify(ledger).markUnknown(message);
    }
    @Test void missingStableKeyCannotDispatch(){
        assertThatThrownBy(()->service.send(1L,request(null))).hasMessageContaining("idempotencyKey");
        verifyNoInteractions(ledger);verify(adapter,never()).sendMessage(any(),any());
    }
}

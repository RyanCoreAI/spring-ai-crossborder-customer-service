package com.omnimerchant.agent.service;

import com.omnimerchant.agent.entity.ChannelAccount;
import com.omnimerchant.agent.entity.ChannelMessage;
import com.omnimerchant.agent.mapper.*;
import com.omnimerchant.tenant.context.TenantContextHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ChannelSendLedgerTest {
    private final ChannelAccountMapper accounts=mock(ChannelAccountMapper.class);
    private final ChannelMessageMapper messages=mock(ChannelMessageMapper.class);
    private final ChannelSendLedger ledger=new ChannelSendLedger(accounts,messages,mock(ChannelDeliveryReceiptMapper.class));
    @AfterEach void clear(){TenantContextHolder.clear();}
    private void tenantAccount(){
        TenantContextHolder.set(42L);var account=new ChannelAccount();account.setId(1L);account.setTenantId(42L);
        when(accounts.selectForSending(1L)).thenReturn(account);
    }
    @Test void reservesIntentAfterTenantScopedAccountLock() {
        tenantAccount();
        var reservation=ledger.reserve(1L,"conv","hello","request-1","fingerprint");
        assertThat(reservation.fresh()).isTrue();
        assertThat(reservation.message().getDeliveryStatus()).isEqualTo("SENDING");
        assertThat(reservation.message().getIdempotencyKey()).isEqualTo("request-1");
        var order=inOrder(accounts,messages);order.verify(accounts).selectForSending(1L);
        order.verify(messages).selectOne(any());order.verify(messages).insert(any(ChannelMessage.class));
    }
    @Test void sameKeyReusesIntentButDifferentPayloadIsRejected() {
        tenantAccount();var existing=new ChannelMessage();existing.setMessageUuid("fingerprint");
        when(messages.selectOne(any())).thenReturn(existing);
        assertThat(ledger.reserve(1L,"conv","hello","request-1","fingerprint").fresh()).isFalse();
        assertThatThrownBy(()->ledger.reserve(1L,"conv","changed","request-1","other"))
                .hasMessageContaining("不同的发送内容");
        verify(messages,never()).insert(any(ChannelMessage.class));
    }
}

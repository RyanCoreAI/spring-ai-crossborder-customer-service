package com.omnimerchant.agent.channel;

import com.omnimerchant.channel.ChannelAccountConfig;
import com.omnimerchant.channel.ChannelOutboundMessage;
import com.omnimerchant.channel.wechat.WechatKfChannelAdapter;
import me.chanjar.weixin.common.util.crypto.WxCryptUtil;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class WechatKfChannelAdapterTest {

    private static final String TOKEN = "secret-token";
    private static final String RECEIVE_ID = "corp-demo";
    private static final String AES_KEY = Base64.getEncoder().withoutPadding()
            .encodeToString("0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8));

    private final WechatKfChannelAdapter adapter = new WechatKfChannelAdapter();

    @Test
    void shouldVerifyAndDecryptLiveUrlChallenge() {
        var encrypted = new WxCryptUtil(TOKEN, AES_KEY, RECEIVE_ID).encryptContext("challenge-ok");

        var result = adapter.verifyWebhook(liveConfig(), Map.of(
                "timestamp", encrypted.getTimeStamp(),
                "nonce", encrypted.getNonce(),
                "msg_signature", encrypted.getSignature(),
                "echostr", encrypted.getEncrypt()), Map.of(), null);

        assertThat(result.valid()).isTrue();
        assertThat(result.mode()).isEqualTo("LIVE");
        assertThat(result.challengeResponse()).isEqualTo("challenge-ok");
    }

    @Test
    void shouldRejectBadLiveWebhookSignature() {
        var encrypted = new WxCryptUtil(TOKEN, AES_KEY, RECEIVE_ID).encryptContext("challenge-ok");

        var result = adapter.verifyWebhook(liveConfig(), Map.of(
                "timestamp", encrypted.getTimeStamp(),
                "nonce", encrypted.getNonce(),
                "msg_signature", "bad",
                "echostr", encrypted.getEncrypt()), Map.of(), null);

        assertThat(result.valid()).isFalse();
        assertThat(result.reason()).contains("validation failed");
    }

    @Test
    void shouldDecryptAndParseLiveInboundMessage() {
        var plain = """
                <xml><MsgId><![CDATA[msg-live-1]]></MsgId><ToUserName><![CDATA[kf-1]]></ToUserName>
                <FromUserName><![CDATA[buyer-1]]></FromUserName><Content><![CDATA[我想查订单]]></Content>
                <MsgType><![CDATA[text]]></MsgType></xml>
                """;
        var encrypted = new WxCryptUtil(TOKEN, AES_KEY, RECEIVE_ID).encryptContext(plain);
        var outer = "<xml><Encrypt><![CDATA[" + encrypted.getEncrypt() + "]]></Encrypt></xml>";
        var params = Map.of(
                "timestamp", encrypted.getTimeStamp(),
                "nonce", encrypted.getNonce(),
                "msg_signature", encrypted.getSignature());

        var message = adapter.ingestMessage(liveConfig(), params, Map.of(), outer);

        assertThat(message.externalMessageId()).isEqualTo("msg-live-1");
        assertThat(message.externalCustomerId()).isEqualTo("buyer-1");
        assertThat(message.body()).isEqualTo("我想查订单");
    }

    @Test
    void shouldParseFixtureJsonInboundMessage() {
        var body = """
                {"messageId":"msg-1","externalThreadId":"thread-1","externalCustomerId":"buyer-1","customerEmail":"buyer@example.com","content":"我想查订单","language":"zh"}
                """;

        var message = adapter.ingestMessage(fixtureConfig(), Map.of(), Map.of(), body);
        var identity = adapter.mapIdentity(message);

        assertThat(message.externalMessageId()).isEqualTo("msg-1");
        assertThat(message.externalThreadId()).isEqualTo("thread-1");
        assertThat(message.body()).isEqualTo("我想查订单");
        assertThat(identity.identityType()).isEqualTo("EMAIL");
        assertThat(identity.displayValueMasked()).isEqualTo("b***@example.com");
    }

    @Test
    void fixtureModeIsExplicitlyReported() {
        var result = adapter.sendMessage(fixtureConfig(),
                new ChannelOutboundMessage("conv-1", "thread-1", "buyer-1", "你好", "idem-1"));

        assertThat(result.success()).isTrue();
        assertThat(result.mode()).isEqualTo("FIXTURE");
        assertThat(result.deliveryStatus()).isEqualTo("SENT");
    }

    private ChannelAccountConfig liveConfig() {
        return new ChannelAccountConfig(1001L, 1L, "WECHAT_KF", "WeChat Live", "kf-1",
                "WAITING_CREDENTIALS", true, true, false, TOKEN, AES_KEY, RECEIVE_ID,
                "corp-demo", "corp-secret", "kf-1", null, null, Map.of());
    }

    private ChannelAccountConfig fixtureConfig() {
        return new ChannelAccountConfig(1001L, 1L, "WECHAT_KF", "WeChat Fixture", "kf-1",
                "FIXTURE", true, true, true, null, null, null, null,
                null, "kf-1", null, null, Map.of("fixtureMode", true));
    }
}

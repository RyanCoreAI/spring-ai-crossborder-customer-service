package com.omnimerchant.agent.controller;

import com.omnimerchant.agent.context.CallContextHolder;
import com.omnimerchant.agent.router.ModelRouter;
import com.omnimerchant.common.dto.R;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 测试 Controller：验证 LLM 连通性。
 */
@Slf4j
@RestController
@RequestMapping("/api/test")
public class TestChatController {

    private final ModelRouter modelRouter;

    public TestChatController(ModelRouter modelRouter) {
        this.modelRouter = modelRouter;
    }

    /**
     * 简单对话测试。
     * <pre>
     * POST /api/test/chat
     * {"message": "Hello, who are you?"}
     * </pre>
     */
    @PostMapping("/chat")
    public R<Map<String, Object>> chat(@RequestBody Map<String, String> body) {
        var userMessage = body.getOrDefault("message", "Say hello in one sentence.");
        var start = System.currentTimeMillis();

        try {
            CallContextHolder.set("TEST", "test-chat");
            var routed = modelRouter.route("UNCLEAR");
            if (!routed.available()) {
                return R.fail("503", "LLM provider is not configured. Set OPENAI_API_KEY, ANTHROPIC_API_KEY, or DEEPSEEK_API_KEY.");
            }
            var response = routed.chatModel().call(new Prompt(userMessage));
            var output = response.getResult().getOutput();
            var metadata = response.getMetadata();
            var modelName = metadata != null && metadata.getModel() != null
                    ? metadata.getModel()
                    : routed.modelName();

            var elapsed = System.currentTimeMillis() - start;
            log.info("LLM test call: latency={}ms, model={}", elapsed, routed.modelName());

            return R.ok(Map.of(
                    "reply", output.getText(),
                    "model", modelName,
                    "latencyMs", elapsed
            ));
        } finally {
            CallContextHolder.clear();
        }
    }
}

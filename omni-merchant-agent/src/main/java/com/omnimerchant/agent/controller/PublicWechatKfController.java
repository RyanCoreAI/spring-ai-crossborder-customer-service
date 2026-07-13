package com.omnimerchant.agent.controller;

import com.omnimerchant.agent.service.WechatWebhookRuntimeService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/public/channels/wechat-kf")
@RequiredArgsConstructor
public class PublicWechatKfController {

    private final WechatWebhookRuntimeService service;

    @GetMapping(value = "/{callbackKey}", produces = MediaType.TEXT_PLAIN_VALUE)
    public String verify(@PathVariable String callbackKey,
                         @RequestParam Map<String, String> params,
                         HttpServletRequest request) {
        return service.verifyChallenge(callbackKey, params, headers(request));
    }

    @PostMapping(value = "/{callbackKey}", produces = MediaType.TEXT_PLAIN_VALUE)
    public String receive(@PathVariable String callbackKey,
                          @RequestParam Map<String, String> params,
                          @RequestBody(required = false) String body,
                          HttpServletRequest request) {
        return service.accept(callbackKey, params, headers(request), body == null ? "" : body);
    }

    private Map<String, String> headers(HttpServletRequest request) {
        var result = new LinkedHashMap<String, String>();
        for (var name : Collections.list(request.getHeaderNames())) {
            result.put(name.toLowerCase(), request.getHeader(name));
        }
        return result;
    }
}

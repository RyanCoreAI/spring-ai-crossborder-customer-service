package com.omnimerchant.message;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

/**
 * 消息模块自动配置。
 */
@Configuration
@ConditionalOnProperty(prefix = "omnimerchant.message", name = "enabled", havingValue = "true", matchIfMissing = true)
@ComponentScan(basePackages = "com.omnimerchant.message")
public class MessageAutoConfiguration {
}

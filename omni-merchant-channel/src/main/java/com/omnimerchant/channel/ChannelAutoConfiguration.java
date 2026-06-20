package com.omnimerchant.channel;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

/**
 * 渠道模块自动配置。
 */
@Configuration
@ComponentScan(basePackages = "com.omnimerchant.channel")
public class ChannelAutoConfiguration {
}

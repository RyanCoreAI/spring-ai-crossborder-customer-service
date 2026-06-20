package com.omnimerchant.agent;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

/**
 * Agent 模块自动配置。
 */
@Configuration
@ComponentScan(basePackages = "com.omnimerchant.agent")
public class AgentAutoConfiguration {
}

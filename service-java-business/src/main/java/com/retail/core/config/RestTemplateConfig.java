package com.retail.core.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

/**
 * RestTemplate Bean 配置.
 * <p>触发时机: Spring 容器启动时注册, 供下游 Python 服务调用方注入使用
 * (AgentHttpClient / MemoryAgentClient / KnowledgeSyncNotifier / KbFileParseClient 等).
 * <p>解决的问题: 统一提供 HTTP 客户端基础设施, 避免各处自行 new RestTemplate 造成配置分散;
 * 复用同一实例以共享连接池与序列化配置.
 * <p>使用约束: 当前为默认连接/读取超时 (无穷), 下游 Python 地址由各客户端自身的
 * {@code agent.python-base} 配置决定, 与本 Bean 解耦; 连接与读取超时如后续需要收紧,
 * 应在此统一配置, 勿在各客户端散落.
 */
@Configuration
public class RestTemplateConfig {
    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
}

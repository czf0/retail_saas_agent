package com.retail.core.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 异步线程池配置.
 * <p>
 * 统一管理本服务中所有后台异步任务(SSE 流式转发,长期记忆抽取,审批恢复 SSE 等),
 * 替代业务代码中零散的 {@code new Thread(...).start()},避免并发高时线程数无上限膨胀
 * (频繁创建/销毁也消耗 OS 资源,且可能触发 FD/句柄上限).
 * <p>
 * 线程池参数:
 * <ul>
 *   <li>核心 10 常驻线程,最大 40,队列 1024;SSE 请求 TTL 120s,属于短时 IO 密集型,
 *       核心数保守按 2~3 倍 CPU 核数估算,上限 40 保证高峰期仍有排队空间不被直接拒绝;</li>
 *   <li>拒绝策略 CallerRunsPolicy:队列满时提交线程(SSE HTTP 请求线程)自身执行,
 *       相当于对上游触发反压,避免任务被静默丢弃;</li>
 *   <li>线程名前缀 "async-worker-":jstack / Arthas 排查线程时容易定位.</li>
 * </ul>
 * <p>
 * 使用方式:
 * <pre>
 *   private final Executor asyncExecutor;  // 构造注入
 *   asyncExecutor.execute(() -> { ... });  // 替代 new Thread(() -> {...}).start()
 * </pre>
 */
@Configuration
public class AsyncExecutorConfig {

    /**
     * 通用业务异步线程池(SSE 转发/记忆抽取等共用).
     * <p>Bean 名 {@code asyncExecutor},供 {@code @Qualifier("asyncExecutor")} 显式指定.</p>
     */
    @Bean("asyncExecutor")
    public Executor asyncExecutor() {
        ThreadPoolTaskExecutor exec = new ThreadPoolTaskExecutor();
        exec.setCorePoolSize(10);
        exec.setMaxPoolSize(40);
        exec.setQueueCapacity(1024);
        exec.setKeepAliveSeconds(120);
        exec.setThreadNamePrefix("async-worker-");
        // 队列+最大池都满时由提交线程自己执行,反压上游 HTTP 线程(避免静默丢任务)
        exec.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        exec.setWaitForTasksToCompleteOnShutdown(true);
        exec.setAwaitTerminationSeconds(30);
        exec.initialize();
        return exec;
    }
}

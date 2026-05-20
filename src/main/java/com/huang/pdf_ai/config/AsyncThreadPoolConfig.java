package com.huang.pdf_ai.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

@Configuration
public class AsyncThreadPoolConfig {

    @Bean(name = "chatDbExecutor")
    public Executor chatDbExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();

        // ===== 核心配置（AI聊天存库最优）=====
        executor.setCorePoolSize(2);          // 核心线程（常驻）
        executor.setMaxPoolSize(4);           // 最大线程
        executor.setQueueCapacity(100);       // 队列容量（足够应对突发）
        executor.setKeepAliveSeconds(60);     // 空闲回收时间
        executor.setThreadNamePrefix("chat-db-"); // 线程名（方便排查）

        // ===== 拒绝策略（队列满了怎么办）=====
        // CALLER_RUNS：由调用线程同步执行，不丢任务
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());

        executor.initialize();
        return executor;
    }
}
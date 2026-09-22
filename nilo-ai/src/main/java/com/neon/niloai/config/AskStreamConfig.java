package com.neon.niloai.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * 问答流式接口的线程池<hr/>
 * 一次提问要等模型把工具跑完，不能占着处理 HTTP 的线程
 */
@Configuration
public class AskStreamConfig
{
    @Bean(name = "askStreamExecutor")
    public Executor askStreamExecutor()
    {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(32);
        executor.setThreadNamePrefix("ask-stream-");
        executor.initialize();
        return executor;
    }
}

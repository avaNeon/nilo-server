package com.neon.niloweb.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;

@Configuration
@EnableScheduling
public class SchedulingConfig implements SchedulingConfigurer
{

    /**
     * 配置定时任务
     */
    @Override
    public void configureTasks(ScheduledTaskRegistrar taskRegistrar)
    {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(4); // 线程池大小为4
        scheduler.setThreadNamePrefix("nilo-scheduler-"); // 线程名前缀
        scheduler.setWaitForTasksToCompleteOnShutdown(true); // 关闭应用时，等待定时任务执行完再彻底关闭应用
        scheduler.setAwaitTerminationSeconds(30); // 关闭时最多等待30s
        scheduler.initialize();

        taskRegistrar.setTaskScheduler(scheduler);
    }
}

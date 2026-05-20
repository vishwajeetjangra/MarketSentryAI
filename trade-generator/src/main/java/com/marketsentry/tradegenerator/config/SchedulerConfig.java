package com.marketsentry.tradegenerator.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@Configuration
public class SchedulerConfig {

    /**
     * One shared scheduler thread pool. It runs both our controllable
     * generator task AND the @Scheduled methods in AnomalyInjectorService.
     * Pool size 5 because the anomaly injector has 3 scheduled methods plus
     * the generator — a pool of 1 would serialize them and a slow task could
     * starve the others.
     */
    @Bean
    public TaskScheduler taskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(5);
        scheduler.setThreadNamePrefix("trade-gen-");
        scheduler.setWaitForTasksToCompleteOnShutdown(true);
        scheduler.initialize();
        return scheduler;
    }
}

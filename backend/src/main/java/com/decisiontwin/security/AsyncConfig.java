package com.decisiontwin.security;

import org.springframework.context.annotation.*;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import java.util.concurrent.Executor;

@Configuration
public class AsyncConfig {
  @Bean("simulationExecutor")
  public Executor simulationExecutor(){
    ThreadPoolTaskExecutor executor=new ThreadPoolTaskExecutor();executor.setCorePoolSize(3);executor.setMaxPoolSize(6);executor.setQueueCapacity(50);executor.setThreadNamePrefix("simulation-");executor.setWaitForTasksToCompleteOnShutdown(true);executor.initialize();return executor;
  }
}

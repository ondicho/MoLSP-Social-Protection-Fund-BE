package MoLSP.go.ke.Social_Assistance_Fund.notifications;

import org.slf4j.MDC;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import tools.jackson.databind.ObjectMapper;

import java.util.concurrent.ThreadPoolExecutor;

@Configuration
@EnableAsync
@EnableScheduling
@EnableConfigurationProperties(NotificationProperties.class)
public class NotificationConfig {

    /**
     * Bounded pool for post-commit dispatch. When saturated, the task is discarded safely because the
     * row is already durable in the outbox and the scheduled poller will deliver it.
     */
    @Bean(name = "notificationExecutor")
    public ThreadPoolTaskExecutor notificationExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("notify-");
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(200);
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.DiscardPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(10);
        executor.setTaskDecorator(task -> {
            var context = MDC.getCopyOfContextMap();
            return () -> {
                var previous = MDC.getCopyOfContextMap();
                if (context == null) MDC.clear(); else MDC.setContextMap(context);
                try {
                    task.run();
                } finally {
                    if (previous == null) MDC.clear(); else MDC.setContextMap(previous);
                }
            };
        });
        return executor;
    }

    @Bean
    @Conditional(TechVoyageConfiguredCondition.class)
    public NotificationGateway techVoyageNotificationGateway(NotificationProperties properties, ObjectMapper json) {
        return new TechVoyageNotificationGateway(properties.techvoyage(), json);
    }

    @Bean
    @ConditionalOnProperty(name = "app.notifications.provider", havingValue = "stub")
    public NotificationGateway stubNotificationGateway() {
        return new StubNotificationGateway();
    }
}

package MoLSP.go.ke.Social_Assistance_Fund.notifications;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

class NotificationConfigTests {
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(NotificationConfig.class)
            .withBean(ObjectMapper.class, () -> JsonMapper.builder().build())
            .withPropertyValues("app.notifications.max-attempts=3", "app.notifications.retry-base-delay=PT1S",
                    "app.notifications.retry-max-delay=PT10S", "app.notifications.lease=PT1M",
                    "app.notifications.batch-size=5");

    @Test
    void blankCommsConfigurationStartsWithoutAGateway() {
        runner.withPropertyValues("app.notifications.provider=techvoyage",
                        "app.notifications.techvoyage.auth-url=", "app.notifications.techvoyage.send-url=",
                        "app.notifications.techvoyage.username=", "app.notifications.techvoyage.password=")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean(NotificationGateway.class);
                });
    }

    @Test
    void completeTechVoyageConfigurationRegistersTheGateway() {
        runner.withPropertyValues("app.notifications.provider=techvoyage",
                        "app.notifications.techvoyage.auth-url=http://127.0.0.1:1/auth",
                        "app.notifications.techvoyage.send-url=http://127.0.0.1:1/send",
                        "app.notifications.techvoyage.username=user", "app.notifications.techvoyage.password=secret")
                .run(context -> assertThat(context).getBean(NotificationGateway.class)
                        .isInstanceOf(TechVoyageNotificationGateway.class));
    }

    @Test
    void stubProviderRegistersTheStubGateway() {
        runner.withPropertyValues("app.notifications.provider=stub")
                .run(context -> assertThat(context).getBean(NotificationGateway.class)
                        .isInstanceOf(StubNotificationGateway.class));
    }

    @Test
    void retryBackoffIsExponentialAndCapped() {
        var properties = new NotificationProperties("none", 5, java.time.Duration.ofSeconds(30),
                java.time.Duration.ofMinutes(2), java.time.Duration.ofMinutes(1), 10, null, null, null);
        assertThat(properties.backoffForAttempt(1)).hasSeconds(30);
        assertThat(properties.backoffForAttempt(2)).hasSeconds(60);
        assertThat(properties.backoffForAttempt(3)).hasSeconds(120);
        assertThat(properties.backoffForAttempt(9)).hasSeconds(120);
    }
}

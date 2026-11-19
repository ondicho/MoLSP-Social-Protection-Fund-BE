package MoLSP.go.ke.Social_Assistance_Fund.utils;

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.PatternLayout;
import ch.qos.logback.classic.joran.JoranConfigurator;
import ch.qos.logback.classic.spi.LoggingEvent;
import ch.qos.logback.classic.spi.ThrowableProxy;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PiiMaskerTests {
    @org.junit.jupiter.api.io.TempDir
    java.nio.file.Path logDirectory;

    @Test
    void masksSecretsAndSensitiveIdentifiersAndPreventsLogInjection() {
        String input = "Authorization: Bearer abc123 password=\"very secret\" "
                + "token=abc456 passportNumber=A1234567 id_number=12345678 "
                + "phone=+254712345678 email=jane@example.test fullName=\"Jane Doe\"\n"
                + "also +254 712 345 678 and 0712345678";
        String masked = PiiMasker.mask(input);
        for (String secret : new String[]{"abc123", "very secret", "abc456", "A1234567", "12345678",
                "+254712345678", "jane@example.test", "Jane Doe", "0712345678", "+254 712 345 678", "\n"}) {
            assertFalse(masked.contains(secret), secret);
        }
        assertTrue(masked.contains("[REDACTED]"));
    }

    @Test
    void actualLoggingConfigurationLoadsAndMasksWithoutPrintingThrowableSecrets() throws Exception {
        LoggerContext context = new LoggerContext();
        try {
            context.putProperty("LOG_PATH", logDirectory.toString());
            var configurator = new JoranConfigurator();
            configurator.setContext(context);
            configurator.doConfigure(getClass().getResource("/logback-spring.xml"));
            assertFalse(context.getStatusManager().getCopyOfStatusList().stream()
                    .anyMatch(status -> status.getLevel() == ch.qos.logback.core.status.Status.ERROR));
            PatternLayout layout = new PatternLayout();
            layout.setContext(context);
            layout.setPattern("%safeMessage%n%nopex");
            layout.start();
            LoggingEvent event = new LoggingEvent();
            event.setMessage("password=secret-value passport=A1234567");
            event.setThrowableProxy(new ThrowableProxy(new RuntimeException("secret throwable")));
            String rendered = layout.doLayout(event);
            assertFalse(rendered.contains("secret-value"));
            assertFalse(rendered.contains("A1234567"));
            assertFalse(rendered.contains("secret throwable"));
            layout.stop();
        } finally {
            context.stop();
        }
    }

    @Test
    void fileStreamsAreSeparatedMaskedAndRotatable() throws Exception {
        LoggerContext context = new LoggerContext();
        context.setMDCAdapter(new ch.qos.logback.classic.util.LogbackMDCAdapter());
        context.putProperty("LOG_PATH", logDirectory.toString());
        try {
            var configurator = new JoranConfigurator();
            configurator.setContext(context);
            configurator.doConfigure(getClass().getResource("/logback-spring.xml"));
            context.getLogger("saf.access").info("event=http_dispatch_completed status=200");
            context.getLogger("MoLSP.go.ke.Social_Assistance_Fund").warn("event=example_warning");
            context.getLogger("MoLSP.go.ke.Social_Assistance_Fund").error("password=do-not-log-this");
            String access = java.nio.file.Files.readString(logDirectory.resolve("access.log"));
            String errors = java.nio.file.Files.readString(logDirectory.resolve("error.log"));
            String application = java.nio.file.Files.readString(logDirectory.resolve("application.log"));
            assertTrue(access.contains("http_dispatch_completed"));
            assertFalse(access.contains("example_warning"));
            assertTrue(application.contains("example_warning"));
            assertFalse(application.contains("http_dispatch_completed"));
            assertFalse(errors.contains("example_warning"));
            assertFalse(errors.contains("do-not-log-this"));
            assertTrue(errors.contains("[REDACTED]"));
            var appender = (ch.qos.logback.core.rolling.RollingFileAppender<?>)
                    context.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME).getAppender("APPLICATION");
            var policy = (ch.qos.logback.core.rolling.SizeAndTimeBasedRollingPolicy<?>)
                    appender.getRollingPolicy();
            assertEquals(14, policy.getMaxHistory());
            assertTrue(policy.getFileNamePattern().endsWith(".log.gz"));
            policy.getTimeBasedFileNamingAndTriggeringPolicy()
                    .setCurrentTime(System.currentTimeMillis() + java.time.Duration.ofDays(1).toMillis());
            context.getLogger("MoLSP.go.ke.Social_Assistance_Fund").warn("event=next_day");
            assertTrue(java.nio.file.Files.isDirectory(logDirectory.resolve("archive")));
        } finally {
            context.stop();
        }
    }
}

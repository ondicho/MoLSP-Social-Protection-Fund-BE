package MoLSP.go.ke.Social_Assistance_Fund.notifications;

import org.springframework.boot.autoconfigure.condition.ConditionOutcome;
import org.springframework.boot.autoconfigure.condition.SpringBootCondition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

import java.util.List;

/**
 * Matches only when TechVoyage is selected and every endpoint/credential is non-blank, so an empty
 * environment variable leaves the gateway unregistered instead of failing application startup.
 */
class TechVoyageConfiguredCondition extends SpringBootCondition {
    private static final List<String> REQUIRED = List.of("auth-url", "send-url", "username", "password");

    @Override
    public ConditionOutcome getMatchOutcome(ConditionContext context, AnnotatedTypeMetadata metadata) {
        var env = context.getEnvironment();
        if (!"techvoyage".equalsIgnoreCase(env.getProperty("app.notifications.provider", "").strip())) {
            return ConditionOutcome.noMatch("TechVoyage provider not selected");
        }
        for (String key : REQUIRED) {
            String value = env.getProperty("app.notifications.techvoyage." + key);
            if (value == null || value.isBlank()) {
                return ConditionOutcome.noMatch("TechVoyage " + key + " not configured");
            }
        }
        return ConditionOutcome.match("TechVoyage configured");
    }
}

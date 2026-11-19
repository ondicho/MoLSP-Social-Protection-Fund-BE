package MoLSP.go.ke.Social_Assistance_Fund.notifications;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "app.notifications")
public record NotificationProperties(
        String provider,
        int maxAttempts,
        Duration retryBaseDelay,
        Duration retryMaxDelay,
        Duration lease,
        int batchSize,
        String messageTitle,
        String ministryName,
        TechVoyage techvoyage) {

    public NotificationProperties {
        provider = provider == null || provider.isBlank() ? "none" : provider.strip();
        if (maxAttempts < 1 || batchSize < 1 || retryBaseDelay == null || retryMaxDelay == null || lease == null
                || retryBaseDelay.isNegative() || retryMaxDelay.compareTo(retryBaseDelay) < 0
                || lease.isNegative() || lease.isZero()) {
            throw new IllegalArgumentException("Invalid notification retry configuration");
        }
        messageTitle = messageTitle == null || messageTitle.isBlank() ? "Social Assistance Fund" : messageTitle.strip();
        ministryName = ministryName == null || ministryName.isBlank()
                ? "Ministry of Labour and Social Protection" : ministryName.strip();
    }

    public Duration backoffForAttempt(int attempt) {
        long multiplier = 1L << Math.min(Math.max(attempt - 1, 0), 20);
        Duration delay = retryBaseDelay.multipliedBy(multiplier);
        return delay.compareTo(retryMaxDelay) > 0 ? retryMaxDelay : delay;
    }

    public record TechVoyage(String authUrl, String sendUrl, String username, String password,
                             Duration connectTimeout, Duration readTimeout, Duration tokenTtl) {
        @Override
        public String toString() {
            return "TechVoyage[authUrl=" + authUrl + ", sendUrl=" + sendUrl + ", credentials=******]";
        }
    }
}

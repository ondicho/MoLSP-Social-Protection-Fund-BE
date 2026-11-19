package MoLSP.go.ke.Social_Assistance_Fund.notifications;

import MoLSP.go.ke.Social_Assistance_Fund.enums.NotificationChannel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

@Slf4j
public class TechVoyageNotificationGateway implements NotificationGateway {
    private static final Set<String> REFERENCE_FIELDS = Set.of("id", "messageId", "reference", "requestId");
    private final NotificationProperties.TechVoyage config;
    private final ObjectMapper json;
    private final RestClient client;
    private final Duration tokenTtl;
    private volatile CachedToken token;

    public TechVoyageNotificationGateway(NotificationProperties.TechVoyage config, ObjectMapper json) {
        this.config = config;
        this.json = json;
        Duration connectTimeout = config.connectTimeout() == null ? Duration.ofSeconds(5) : config.connectTimeout();
        Duration readTimeout = config.readTimeout() == null ? Duration.ofSeconds(10) : config.readTimeout();
        this.tokenTtl = config.tokenTtl() == null ? Duration.ofMinutes(10) : config.tokenTtl();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(connectTimeout).build());
        factory.setReadTimeout(readTimeout);
        this.client = RestClient.builder().requestFactory(factory).build();
    }

    @Override
    public String providerName() {
        return "techvoyage";
    }

    @Override
    public DeliveryResult send(OutboundNotification notification) {
        try {
            ProviderResponse response = post(accessToken(false), notification);
            if (response.status() == 401 || response.status() == 403) {
                response = post(accessToken(true), notification);
            }
            return interpret(notification, response);
        } catch (AuthenticationFailure failure) {
            log.warn("event=notification_provider_auth_failed provider=techvoyage notificationId={} category={}",
                    notification.id(), failure.getMessage());
            return DeliveryResult.retryable("PROVIDER_AUTH_FAILED");
        } catch (RestClientException | IllegalStateException exception) {
            log.warn("event=notification_provider_unreachable provider=techvoyage notificationId={} error={}",
                    notification.id(), exception.getClass().getSimpleName());
            return DeliveryResult.retryable("PROVIDER_UNREACHABLE");
        }
    }

    private DeliveryResult interpret(OutboundNotification notification, ProviderResponse response) {
        if (response.status() < 200 || response.status() >= 300) {
            log.warn("event=notification_provider_rejected provider=techvoyage notificationId={} httpStatus={}",
                    notification.id(), response.status());
            return DeliveryResult.retryable("PROVIDER_HTTP_" + response.status());
        }
        JsonNode body = parse(response.body());
        if (body != null && body.isObject()) {
            JsonNode success = body.get("success");
            JsonNode status = body.get("status");
            if ((success != null && success.isBoolean() && !success.asBoolean())
                    || (status != null && status.isString()
                    && Set.of("failed", "error").contains(status.asString().toLowerCase()))) {
                log.warn("event=notification_provider_reported_failure provider=techvoyage notificationId={}",
                        notification.id());
                return DeliveryResult.retryable("PROVIDER_REPORTED_FAILURE");
            }
            for (String field : REFERENCE_FIELDS) {
                JsonNode value = body.get(field);
                if (value != null && value.isValueNode() && !value.asString().isBlank()) {
                    String reference = value.asString();
                    return DeliveryResult.delivered(reference.length() > 200 ? reference.substring(0, 200) : reference);
                }
            }
        }
        return DeliveryResult.delivered(null);
    }

    private ProviderResponse post(String accessToken, OutboundNotification notification) {
        Map<String, Object> to = new LinkedHashMap<>();
        to.put("email", notification.channel() == NotificationChannel.EMAIL ? notification.recipient() : "");
        to.put("phoneNumber", notification.channel() == NotificationChannel.SMS ? notification.recipient() : "");
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("to", to);
        payload.put("body", notification.body());
        payload.put("subject", notification.subject());
        payload.put("title", notification.title());
        payload.put("channel", notification.channel().providerValue());
        return client.post().uri(config.sendUrl())
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .body(json.writeValueAsString(payload))
                .exchange((request, response) -> new ProviderResponse(response.getStatusCode().value(),
                        new String(response.getBody().readAllBytes(), StandardCharsets.UTF_8)));
    }

    private synchronized String accessToken(boolean forceRefresh) {
        CachedToken cached = token;
        if (!forceRefresh && cached != null && cached.expiresAt().isAfter(Instant.now())) {
            return cached.value();
        }
        token = null;
        ProviderResponse response = client.post().uri(config.authUrl())
                .contentType(MediaType.APPLICATION_JSON)
                .body(json.writeValueAsString(Map.of("username", config.username(), "password", config.password())))
                .exchange((request, res) -> new ProviderResponse(res.getStatusCode().value(),
                        new String(res.getBody().readAllBytes(), StandardCharsets.UTF_8)));
        if (response.status() < 200 || response.status() >= 300) {
            throw new AuthenticationFailure("AUTH_HTTP_" + response.status());
        }
        JsonNode body = parse(response.body());
        JsonNode accessToken = body == null ? null : body.get("accessToken");
        if (accessToken == null || !accessToken.isString() || accessToken.asString().isBlank()) {
            throw new AuthenticationFailure("AUTH_TOKEN_MISSING");
        }
        token = new CachedToken(accessToken.asString(), Instant.now().plus(tokenTtl));
        return token.value();
    }

    private JsonNode parse(String body) {
        if (body == null || body.isBlank()) {
            return null;
        }
        try {
            return json.readTree(body);
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private record ProviderResponse(int status, String body) {
    }

    private record CachedToken(String value, Instant expiresAt) {
    }

    private static final class AuthenticationFailure extends RuntimeException {
        private AuthenticationFailure(String category) {
            super(category, null, false, false);
        }
    }
}

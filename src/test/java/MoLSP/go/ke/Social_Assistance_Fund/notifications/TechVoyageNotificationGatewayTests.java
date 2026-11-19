package MoLSP.go.ke.Social_Assistance_Fund.notifications;

import MoLSP.go.ke.Social_Assistance_Fund.enums.NotificationChannel;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class TechVoyageNotificationGatewayTests {
    private final JsonMapper json = JsonMapper.builder().build();
    private final AtomicInteger logins = new AtomicInteger();
    private final AtomicInteger sendStatus = new AtomicInteger(200);
    private final List<String> authorizations = new CopyOnWriteArrayList<>();
    private final List<JsonNode> payloads = new CopyOnWriteArrayList<>();
    private HttpServer server;
    private TechVoyageNotificationGateway gateway;

    @BeforeEach
    void start() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/auth", exchange -> {
            JsonNode credentials = json.readTree(exchange.getRequestBody().readAllBytes());
            boolean valid = "user".equals(credentials.get("username").asString())
                    && "secret".equals(credentials.get("password").asString());
            byte[] body = ("{\"accessToken\":\"token-" + logins.incrementAndGet() + "\"}").getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(valid ? 200 : 401, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.createContext("/send", exchange -> {
            authorizations.add(exchange.getRequestHeaders().getFirst("Authorization"));
            payloads.add(json.readTree(exchange.getRequestBody().readAllBytes()));
            int status = sendStatus.get() == 401 && authorizations.size() > 1 ? 200 : sendStatus.get();
            byte[] body = "{\"success\":true,\"messageId\":\"msg-42\"}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        String base = "http://127.0.0.1:" + server.getAddress().getPort();
        gateway = new TechVoyageNotificationGateway(new NotificationProperties.TechVoyage(base + "/auth",
                base + "/send", "user", "secret", Duration.ofSeconds(2), Duration.ofSeconds(2),
                Duration.ofMinutes(5)), json);
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    private OutboundNotification sms() {
        return new OutboundNotification(UUID.randomUUID(), NotificationChannel.SMS, "254712345678",
                "Application approved", "Social Assistance Fund", "Dear Jane, approved.");
    }

    @Test
    void sendsProviderPayloadWithCachedBearerToken() {
        DeliveryResult first = gateway.send(sms());
        DeliveryResult second = gateway.send(sms());

        assertTrue(first.delivered());
        assertEquals("msg-42", first.providerReference());
        assertTrue(second.delivered());
        assertEquals(1, logins.get());
        assertEquals(List.of("Bearer token-1", "Bearer token-1"), authorizations);
        JsonNode payload = payloads.getFirst();
        assertEquals("254712345678", payload.get("to").get("phoneNumber").asString());
        assertEquals("", payload.get("to").get("email").asString());
        assertEquals("sms", payload.get("channel").asString());
        assertEquals("Dear Jane, approved.", payload.get("body").asString());
        assertEquals("Application approved", payload.get("subject").asString());
        assertEquals("Social Assistance Fund", payload.get("title").asString());
    }

    @Test
    void refreshesTokenOnceWhenProviderRejectsIt() {
        sendStatus.set(401);
        DeliveryResult result = gateway.send(sms());
        assertTrue(result.delivered());
        assertEquals(2, logins.get());
        assertEquals(List.of("Bearer token-1", "Bearer token-2"), authorizations);
    }

    @Test
    void providerErrorsAndOutagesAreRetryable() {
        sendStatus.set(503);
        DeliveryResult failed = gateway.send(sms());
        assertFalse(failed.delivered());
        assertTrue(failed.retryable());
        assertEquals("PROVIDER_HTTP_503", failed.errorCategory());

        server.stop(0);
        DeliveryResult unreachable = gateway.send(sms());
        assertTrue(unreachable.retryable());
        assertEquals("PROVIDER_UNREACHABLE", unreachable.errorCategory());
    }

    @Test
    void invalidCredentialsAreReportedWithoutThrowing() {
        String base = "http://127.0.0.1:" + server.getAddress().getPort();
        var badGateway = new TechVoyageNotificationGateway(new NotificationProperties.TechVoyage(base + "/auth",
                base + "/send", "user", "wrong", null, null, null), json);
        DeliveryResult result = badGateway.send(sms());
        assertTrue(result.retryable());
        assertEquals("PROVIDER_AUTH_FAILED", result.errorCategory());
        assertTrue(payloads.isEmpty());
    }
}

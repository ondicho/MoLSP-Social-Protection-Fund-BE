package MoLSP.go.ke.Social_Assistance_Fund.filters;

import MoLSP.go.ke.Social_Assistance_Fund.utils.ResponseHandler;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.slf4j.LoggerFactory;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class CorrelationIdFilterTests {
    private final ResponseHandler responses = new ResponseHandler();
    private final CorrelationIdFilter filter = new CorrelationIdFilter(responses);

    @AfterEach
    void cleanup() {
        MDC.clear();
    }

    @Test
    void requestResponseMdcAndEnvelopeUseSameId() throws Exception {
        var request = new MockHttpServletRequest("GET", "/any");
        request.addHeader("X-Request-ID", "test-123");
        var response = new MockHttpServletResponse();
        filter.doFilter(request, response, (req, res) -> {
            assertEquals("test-123", MDC.get("requestId"));
            var body = responses.success("ok", responses.requestId(request)).getBody();
            assertEquals("test-123", body.getRequestId());
            assertEquals("test-123", responses.success("ok", null).getBody().getRequestId());
            assertEquals("test-123", responses.success("ok", "different").getBody().getRequestId());
        });
        assertEquals("test-123", response.getHeader("X-Request-ID"));
        assertNull(MDC.get("requestId"));
    }

    @Test
    void invalidIdIsReplacedAndMdcIsRestoredAfterFailure() {
        var request = new MockHttpServletRequest();
        request.addHeader("X-Request-ID", "unsafe\ninjection");
        var response = new MockHttpServletResponse();
        MDC.put("requestId", "outer-context");
        assertThrows(ServletException.class, () -> filter.doFilter(request, response, (req, res) -> {
            assertDoesNotThrow(() -> UUID.fromString(MDC.get("requestId")));
            throw new ServletException("test failure");
        }));
        assertEquals("outer-context", MDC.get("requestId"));
        assertDoesNotThrow(() -> UUID.fromString(response.getHeader("X-Request-ID")));
    }

    @Test
    void logsMatchedRouteNotRawPathOrQueryAndWarnsOnRejection() throws Exception {
        Logger logger = (Logger) LoggerFactory.getLogger("saf.access");
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            var request = new MockHttpServletRequest("PUT", "/api/applications/private-identity");
            request.setQueryString("email=private@example.test");
            var response = new MockHttpServletResponse();
            filter.doFilter(request, response, (req, res) -> {
                req.setAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE, "/api/applications/{id}");
                ((jakarta.servlet.http.HttpServletResponse) res).setStatus(400);
            });
            assertEquals(1, appender.list.size());
            ILoggingEvent event = appender.list.getFirst();
            assertEquals(Level.WARN, event.getLevel());
            assertTrue(event.getFormattedMessage().contains("method=PUT route=/api/applications/{id} status=400"));
            assertFalse(event.getFormattedMessage().contains("private"));
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }

    @Test
    void suppressesOnlySuccessfulHealthChecks() throws Exception {
        Logger logger = (Logger) LoggerFactory.getLogger("saf.access");
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            var request = new MockHttpServletRequest("GET", "/actuator/health/readiness");
            var response = new MockHttpServletResponse();
            filter.doFilter(request, response, (req, res) -> {});
            assertTrue(appender.list.isEmpty());
            response.setStatus(503);
            filter.doFilter(request, response, (req, res) -> {});
            assertEquals(1, appender.list.size());
            assertEquals(Level.ERROR, appender.list.getFirst().getLevel());
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }
}

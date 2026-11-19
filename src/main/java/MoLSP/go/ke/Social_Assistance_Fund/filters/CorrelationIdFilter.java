package MoLSP.go.ke.Social_Assistance_Fund.filters;

import MoLSP.go.ke.Social_Assistance_Fund.utils.ResponseHandler;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerMapping;

import java.io.IOException;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@RequiredArgsConstructor
public class CorrelationIdFilter extends OncePerRequestFilter {
    private static final Logger ACCESS_LOG = LoggerFactory.getLogger("saf.access");
    private final ResponseHandler responses;

    @Override
    protected boolean shouldNotFilterAsyncDispatch() {
        return false;
    }

    @Override
    protected boolean shouldNotFilterErrorDispatch() {
        return false;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String previous = MDC.get("requestId");
        String requestId = responses.requestId(request);
        response.setHeader("X-Request-ID", requestId);
        MDC.put("requestId", requestId);
        long start = System.nanoTime();
        boolean completed = false;
        try {
            chain.doFilter(request, response);
            completed = true;
        } finally {
            int status = completed ? response.getStatus() : HttpServletResponse.SC_INTERNAL_SERVER_ERROR;
            boolean healthyProbe = status >= 200 && status < 300
                    && ("GET".equals(request.getMethod()) || "HEAD".equals(request.getMethod()))
                    && switch (request.getRequestURI()) {
                        case "/actuator/health", "/actuator/health/readiness", "/actuator/health/liveness" -> true;
                        default -> false;
                    };
            if (!healthyProbe) {
                Object pattern = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
                String route = pattern == null ? "UNRESOLVED" : pattern.toString();
                String method = switch (request.getMethod()) {
                    case "GET", "POST", "PUT", "PATCH", "DELETE", "HEAD", "OPTIONS" -> request.getMethod();
                    default -> "OTHER";
                };
                var level = status >= 500 ? ACCESS_LOG.atError()
                        : status >= 400 ? ACCESS_LOG.atWarn() : ACCESS_LOG.atInfo();
                level.log("event=http_dispatch_completed method={} route={} status={} durationMs={}",
                        method, route, status, (System.nanoTime() - start) / 1_000_000);
            }
            if (previous == null) {
                MDC.remove("requestId");
            } else {
                MDC.put("requestId", previous);
            }
        }
    }
}

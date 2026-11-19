package MoLSP.go.ke.Social_Assistance_Fund.utils;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Map;

public final class ApiProblemDetails {

    private ApiProblemDetails() {
    }

    public static ProblemDetail create(HttpStatusCode status, String detail, HttpServletRequest request) {
        return create(status, detail, request, Map.of());
    }

    public static ProblemDetail create(HttpStatusCode status, String detail, HttpServletRequest request,
                                       Map<String, List<String>> errors) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty("timestamp", Instant.now().toString());
        if (!errors.isEmpty()) {
            problem.setProperty("errors", errors);
        }
        return problem;
    }
}

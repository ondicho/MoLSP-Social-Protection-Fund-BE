package MoLSP.go.ke.Social_Assistance_Fund.utils;

import MoLSP.go.ke.Social_Assistance_Fund.dtos.ResponseWrapper;
import MoLSP.go.ke.Social_Assistance_Fund.dtos.PaginatedResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.slf4j.MDC;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@Slf4j
public class ResponseHandler {

    public <T> ResponseEntity<ResponseWrapper<T>> createResponse(
            boolean success, String message, T data, String error, HttpStatus status, String requestId) {
        if (success != status.is2xxSuccessful() || status == HttpStatus.NO_CONTENT) {
            throw new IllegalArgumentException("Response status must match success and allow a body");
        }
        ResponseWrapper<T> body = ResponseWrapper.<T>builder()
                .success(success).message(message).data(data).error(error)
                .requestId(resolveRequestId(requestId))
                .build();
        return ResponseEntity.status(status).contentType(MediaType.APPLICATION_JSON)
                .header("X-Request-ID", body.getRequestId()).body(body);
    }

    public <T> ResponseEntity<ResponseWrapper<T>> success(T data, String requestId) {
        return success(data, "Operation successful", requestId);
    }

    public <T> ResponseEntity<ResponseWrapper<T>> success(T data, String message, String requestId) {
        return createResponse(true, message, data, null, HttpStatus.OK, requestId);
    }

    public <T> ResponseEntity<ResponseWrapper<T>> created(T data, String message, String requestId) {
        return createResponse(true, message, data, null, HttpStatus.CREATED, requestId);
    }

    public <T> ResponseEntity<ResponseWrapper<PaginatedResponse<T>>> paginated(
            Page<T> page, String message, String requestId) {
        return success(PaginatedResponse.from(page), message, requestId);
    }

    public ResponseEntity<ResponseWrapper<ProblemDetail>> error(
            HttpStatus status, String detail, HttpServletRequest request, Map<String, List<String>> errors) {
        if (!status.isError()) {
            throw new IllegalArgumentException("Error responses require an error status");
        }
        ProblemDetail problem = ApiProblemDetails.create(status, detail, request, errors);
        var response = createResponse(false, status.getReasonPhrase(), problem, detail, status, requestId(request));
        if (status.is4xxClientError()) {
            log.warn("event=request_rejected status={} category={} invalidFieldCount={}",
                    status.value(), status.name(), errors.size());
        }
        problem.setProperty("timestamp", response.getBody().getTimestamp());
        problem.setProperty("requestId", response.getBody().getRequestId());
        return response;
    }

    public ResponseEntity<ResponseWrapper<Void>> badRequest(String error, String requestId) {
        return failure(HttpStatus.BAD_REQUEST, error, requestId);
    }

    public ResponseEntity<ResponseWrapper<Void>> unauthorized(String error, String requestId) {
        return failure(HttpStatus.UNAUTHORIZED, error, requestId);
    }

    public ResponseEntity<ResponseWrapper<Void>> forbidden(String error, String requestId) {
        return failure(HttpStatus.FORBIDDEN, error, requestId);
    }

    public ResponseEntity<ResponseWrapper<Void>> notFound(String error, String requestId) {
        return failure(HttpStatus.NOT_FOUND, error, requestId);
    }

    public ResponseEntity<ResponseWrapper<Void>> conflict(String error, String requestId) {
        return failure(HttpStatus.CONFLICT, error, requestId);
    }

    public ResponseEntity<ResponseWrapper<Void>> internalError(String error, String requestId) {
        return failure(HttpStatus.INTERNAL_SERVER_ERROR, error, requestId);
    }

    private ResponseEntity<ResponseWrapper<Void>> failure(HttpStatus status, String error, String requestId) {
        return createResponse(false, status.getReasonPhrase(), null, error, status, requestId);
    }

    public String requestId(HttpServletRequest request) {
        Object existing = request.getAttribute("requestId");
        if (existing instanceof String id && !id.isBlank()) {
            return id;
        }
        String header = request.getHeader("X-Request-ID");
        String id = header != null && header.matches("[A-Za-z0-9._-]{1,100}")
                ? header : UUID.randomUUID().toString();
        request.setAttribute("requestId", id);
        return id;
    }

    private String resolveRequestId(String supplied) {
        String current = MDC.get("requestId");
        if (current != null) {
            return current;
        }
        return supplied != null && supplied.matches("[A-Za-z0-9._-]{1,100}")
                ? supplied : UUID.randomUUID().toString();
    }
}

package MoLSP.go.ke.Social_Assistance_Fund.exceptions;

import MoLSP.go.ke.Social_Assistance_Fund.utils.ApiProblemDetails;
import MoLSP.go.ke.Social_Assistance_Fund.utils.ResponseHandler;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.validation.BindException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

class GlobalExceptionHandlerTests {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler(new ResponseHandler());
    private final MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/applications");

    @Test
    void invalidPhoneReturnsFieldLevelErrorWithoutPersonalData() {
        var response = handler.handlePhone(new InvalidPhoneNumberException(), request);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals(Map.of("phoneNumber",
                        List.of("A valid phone number from an allowed country is required")),
                response.getBody().getData().getProperties().get("errors"));
    }

    @Test
    void mvcSerializesExceptionWithStandardEnvelope() throws Exception {
        var mvc = standaloneSetup(new ErrorController()).setControllerAdvice(handler).build();
        mvc.perform(get("/test/missing")
                        .header("X-Request-ID", "test-request-1"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.data.status").value(404))
                .andExpect(jsonPath("$.requestId").value("test-request-1"))
                .andExpect(jsonPath("$.data.instance").value("/test/missing"));
    }

    @RestController
    static class ErrorController {
        @GetMapping("/test/missing")
        public void missing() {
            throw new ResourceNotFoundException("Application", 14);
        }
    }

    @Test
    void bindingValidationReturnsFieldErrorsInProblemDetails() {
        var bindingResult = new BeanPropertyBindingResult(new Object(), "application");
        bindingResult.addError(new FieldError("application", "firstName", "must not be blank"));
        bindingResult.addError(new FieldError("application", "firstName", "must be at most 100 characters"));
        bindingResult.addError(new FieldError("application", "applicationDate", "must be a past or present date"));

        var response = handler.handleBind(new BindException(bindingResult), request);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertFalse(response.getBody().isSuccess());
        assertEquals("Request validation failed", response.getBody().getError());
        assertEquals(response.getBody().getRequestId(), response.getHeaders().getFirst("X-Request-ID"));
        ProblemDetail problem = response.getBody().getData();
        assertNotNull(problem);
        assertEquals(400, problem.getStatus());
        assertEquals("Request validation failed", problem.getDetail());
        assertEquals("/api/applications", problem.getInstance().toString());
        assertEquals(Map.of(
                "firstName", List.of("must not be blank", "must be at most 100 characters"),
                "applicationDate", List.of("must be a past or present date")
        ), problem.getProperties().get("errors"));
        assertIsoInstant(problem.getProperties().get("timestamp").toString());
    }

    @Test
    void notFoundAndDuplicateExceptionsMapToConsistentStatuses() {
        var missing = handler.handleNotFound(new ResourceNotFoundException("Application", 14), request);
        var duplicate = handler.handleDuplicate(new DuplicateResourceException("User", "user@example.test"), request);

        assertEquals(HttpStatus.NOT_FOUND, missing.getStatusCode());
        assertEquals("Application not found with identifier: 14", missing.getBody().getData().getDetail());
        assertEquals(HttpStatus.CONFLICT, duplicate.getStatusCode());
        assertEquals("User already exists with identifier: user@example.test", duplicate.getBody().getData().getDetail());
        assertEquals(missing.getBody().getData().getInstance(), duplicate.getBody().getData().getInstance());
        assertIsoInstant(missing.getBody().getTimestamp());
    }

    @Test
    void problemDetailsUtilityCreatesIsoTimestampAndRequestInstance() {
        ProblemDetail problem = ApiProblemDetails.create(HttpStatus.CONFLICT, "State conflict", request);

        assertEquals(HttpStatus.CONFLICT.value(), problem.getStatus());
        assertEquals("/api/applications", problem.getInstance().toString());
        assertFalse(problem.getProperties().containsKey("errors"));
        assertIsoInstant(problem.getProperties().get("timestamp").toString());
    }

    private void assertIsoInstant(String value) {
        assertEquals(value, Instant.parse(value).toString());
    }
}

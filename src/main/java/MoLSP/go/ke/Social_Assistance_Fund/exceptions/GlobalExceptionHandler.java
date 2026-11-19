package MoLSP.go.ke.Social_Assistance_Fund.exceptions;

import MoLSP.go.ke.Social_Assistance_Fund.dtos.ResponseWrapper;
import MoLSP.go.ke.Social_Assistance_Fund.utils.ResponseHandler;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.InvalidDataAccessApiUsageException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.BindException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
@RequiredArgsConstructor
public class GlobalExceptionHandler {

    private final ResponseHandler responseHandler;

    @ExceptionHandler(InvalidPhoneNumberException.class)
    public ResponseEntity<ResponseWrapper<ProblemDetail>> handlePhone(
            InvalidPhoneNumberException exception, HttpServletRequest request) {
        return respond(HttpStatus.BAD_REQUEST, "Request validation failed", request,
                Map.of("phoneNumber", List.of(exception.getMessage())));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ResponseWrapper<ProblemDetail>> handleValidation(MethodArgumentNotValidException exception,
                                                          HttpServletRequest request) {
        return respond(HttpStatus.BAD_REQUEST, "Request validation failed", request,
                exception.getBindingResult().getFieldErrors().stream()
                        .collect(Collectors.groupingBy(
                                error -> error.getField(),
                                TreeMap::new,
                                Collectors.mapping(error -> error.getDefaultMessage(), Collectors.toList())
                        )));
    }

    @ExceptionHandler(BindException.class)
    public ResponseEntity<ResponseWrapper<ProblemDetail>> handleBind(BindException exception, HttpServletRequest request) {
        return respond(HttpStatus.BAD_REQUEST, "Request validation failed", request,
                exception.getBindingResult().getFieldErrors().stream()
                        .collect(Collectors.groupingBy(
                                error -> error.getField(),
                                TreeMap::new,
                                Collectors.mapping(error -> error.getDefaultMessage(), Collectors.toList())
                        )));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ResponseWrapper<ProblemDetail>> handleConstraintViolation(ConstraintViolationException exception,
                                                                   HttpServletRequest request) {
        Map<String, List<String>> errors = exception.getConstraintViolations().stream()
                .collect(Collectors.groupingBy(
                        violation -> violation.getPropertyPath().toString(),
                        TreeMap::new,
                        Collectors.mapping(violation -> violation.getMessage(), Collectors.toList())
                ));
        return respond(HttpStatus.BAD_REQUEST, "Request validation failed", request, errors);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ResponseWrapper<ProblemDetail>> handleUnreadable(HttpServletRequest request) {
        return respond(HttpStatus.BAD_REQUEST, "Request body is missing or malformed", request);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ResponseWrapper<ProblemDetail>> handleMissingParam(MissingServletRequestParameterException exception,
                                                              HttpServletRequest request) {
        return respond(HttpStatus.BAD_REQUEST,
                "Missing required parameter: " + exception.getParameterName(), request);
    }

    @ExceptionHandler(MissingServletRequestPartException.class)
    public ResponseEntity<ResponseWrapper<ProblemDetail>> handleMissingPart(MissingServletRequestPartException exception,
                                                            HttpServletRequest request) {
        return respond(HttpStatus.BAD_REQUEST,
                "Missing required part: " + exception.getRequestPartName(), request);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ResponseWrapper<ProblemDetail>> handleTypeMismatch(MethodArgumentTypeMismatchException exception,
                                                              HttpServletRequest request) {
        return respond(HttpStatus.BAD_REQUEST,
                "Invalid value for parameter: " + exception.getName(), request);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ResponseWrapper<ProblemDetail>> handleIllegalArg(IllegalArgumentException exception,
                                                            HttpServletRequest request) {
        return respond(HttpStatus.BAD_REQUEST, safeMessage(exception, "Invalid request"), request);
    }

    @ExceptionHandler(InvalidDataAccessApiUsageException.class)
    public ResponseEntity<ResponseWrapper<ProblemDetail>> handleInvalidDataAccess(HttpServletRequest request) {
        return respond(HttpStatus.BAD_REQUEST, "Invalid query parameter", request);
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ResponseWrapper<ProblemDetail>> handleBadCredentials(HttpServletRequest request) {
        return respond(HttpStatus.UNAUTHORIZED, "Invalid email or password", request);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ResponseWrapper<ProblemDetail>> handleAuth(HttpServletRequest request) {
        return respond(HttpStatus.UNAUTHORIZED, "Authentication failed", request);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ResponseWrapper<ProblemDetail>> handleAccessDenied(HttpServletRequest request) {
        return respond(HttpStatus.FORBIDDEN, "You do not have permission to perform this action", request);
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ResponseWrapper<ProblemDetail>> handleNotFound(ResourceNotFoundException exception,
                                                         HttpServletRequest request) {
        return respond(HttpStatus.NOT_FOUND, exception.getMessage(), request);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ResponseWrapper<ProblemDetail>> handleNoResource(HttpServletRequest request) {
        return respond(HttpStatus.NOT_FOUND, "Endpoint not found", request);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ResponseWrapper<ProblemDetail>> handleMethodNotAllowed(HttpRequestMethodNotSupportedException exception,
                                                                 HttpServletRequest request) {
        return respond(HttpStatus.METHOD_NOT_ALLOWED,
                "HTTP method " + exception.getMethod() + " is not supported for this endpoint", request);
    }

    @ExceptionHandler(DuplicateResourceException.class)
    public ResponseEntity<ResponseWrapper<ProblemDetail>> handleDuplicate(DuplicateResourceException exception,
                                                          HttpServletRequest request) {
        return respond(HttpStatus.CONFLICT, exception.getMessage(), request);
    }

    @ExceptionHandler(InvalidStateTransitionException.class)
    public ResponseEntity<ResponseWrapper<ProblemDetail>> handleInvalidTransition(
            InvalidStateTransitionException exception, HttpServletRequest request) {
        return respond(HttpStatus.CONFLICT, exception.getMessage(), request);
    }

    @ExceptionHandler({DataIntegrityViolationException.class, OptimisticLockingFailureException.class})
    public ResponseEntity<ResponseWrapper<ProblemDetail>> handleConflict(HttpServletRequest request) {
        return respond(HttpStatus.CONFLICT, "The request conflicts with the current resource state", request);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ResponseWrapper<ProblemDetail>> handleMaxUpload(HttpServletRequest request) {
        return respond(HttpStatus.PAYLOAD_TOO_LARGE, "File size exceeds the maximum allowed limit", request);
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ResponseWrapper<ProblemDetail>> handleMediaType(HttpServletRequest request) {
        return respond(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Unsupported media type", request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ResponseWrapper<ProblemDetail>> handleGeneric(Exception exception, HttpServletRequest request) {
        log.error("event=unhandled_request_error exceptionType={}", exception.getClass().getSimpleName());
        return respond(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred", request);
    }

    private ResponseEntity<ResponseWrapper<ProblemDetail>> respond(HttpStatus status, String detail, HttpServletRequest request) {
        return respond(status, detail, request, Map.of());
    }

    private ResponseEntity<ResponseWrapper<ProblemDetail>> respond(HttpStatus status, String detail, HttpServletRequest request,
                                                  Map<String, List<String>> errors) {
        return responseHandler.error(status, detail, request, errors);
    }

    private String safeMessage(IllegalArgumentException exception, String fallback) {
        return exception.getMessage() == null || exception.getMessage().isBlank()
                ? fallback
                : exception.getMessage();
    }
}

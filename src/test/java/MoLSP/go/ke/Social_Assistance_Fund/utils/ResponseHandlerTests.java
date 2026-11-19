package MoLSP.go.ke.Social_Assistance_Fund.utils;

import MoLSP.go.ke.Social_Assistance_Fund.dtos.ResponseWrapper;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ResponseHandlerTests {
    private final ResponseHandler handler = new ResponseHandler();

    @Test
    void successAndCreatedPreserveTypedDataAndMetadata() {
        var response = handler.success(Map.of("id", 1), "request-1");
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(response.getBody().isSuccess());
        assertEquals(1, response.getBody().getData().get("id"));
        assertNull(response.getBody().getError());
        assertEquals("request-1", response.getHeaders().getFirst("X-Request-ID"));
        assertNotNull(Instant.parse(response.getBody().getTimestamp()));
        assertEquals(HttpStatus.CREATED, handler.created("new", "Created", "request-1").getStatusCode());
        assertTrue(ResponseWrapper.success("data").isSuccess());
    }

    @Test
    void failureConveniencesUseMatchingStatuses() {
        assertEquals(HttpStatus.BAD_REQUEST, handler.badRequest("bad", "id").getStatusCode());
        assertEquals(HttpStatus.UNAUTHORIZED, handler.unauthorized("bad", "id").getStatusCode());
        assertEquals(HttpStatus.FORBIDDEN, handler.forbidden("bad", "id").getStatusCode());
        assertEquals(HttpStatus.NOT_FOUND, handler.notFound("bad", "id").getStatusCode());
        assertEquals(HttpStatus.CONFLICT, handler.conflict("bad", "id").getStatusCode());
        var response = handler.internalError("Unexpected error", "id");
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertFalse(response.getBody().isSuccess());
        assertNull(response.getBody().getData());
        assertEquals("Unexpected error", response.getBody().getError());
    }

    @Test
    void requestIdsAreValidatedAndStableWithinRequest() {
        var request = new MockHttpServletRequest();
        request.addHeader("X-Request-ID", "client-123");
        assertEquals("client-123", handler.requestId(request));
        var invalid = new MockHttpServletRequest();
        invalid.addHeader("X-Request-ID", "unsafe\nvalue");
        String generated = handler.requestId(invalid);
        assertEquals(generated, handler.requestId(invalid));
        assertDoesNotThrow(() -> java.util.UUID.fromString(generated));
    }

    @Test
    void serializationUsesRequestedFieldsAndIsoDates() {
        var body = handler.success(Map.of("date", LocalDate.of(2026, 11, 17)), "id").getBody();
        var json = JsonMapper.builder().build().readTree(JsonMapper.builder().build().writeValueAsString(body));
        assertEquals(6, json.size());
        assertTrue(json.get("success").asBoolean());
        assertEquals("2026-11-17", json.get("data").get("date").asString());
        assertNotNull(Instant.parse(json.get("timestamp").asString()));
        assertTrue(json.has("error"));
    }

    @Test
    void paginationAndSingleObjectsStayInsideResponseWrapperData() throws Exception {
        var paginated = handler.paginated(
                new PageImpl<>(List.of("item"), PageRequest.of(0, 2), 1),
                "Records retrieved", "page-request");
        var mapper = JsonMapper.builder().build();
        var pageJson = mapper.readTree(mapper.writeValueAsString(paginated.getBody()));

        assertTrue(pageJson.has("success"));
        assertEquals("page-request", pageJson.get("requestId").asString());
        assertTrue(pageJson.get("data").has("content"));
        assertEquals(1, pageJson.get("data").get("content").size());
        assertTrue(pageJson.get("data").has("pageNo"));
        assertFalse(pageJson.has("content"));
        assertFalse(pageJson.has("pageNo"));

        var singleJson = mapper.readTree(mapper.writeValueAsString(handler.success("item", "single-request").getBody()));
        assertEquals("item", singleJson.get("data").asString());
        assertEquals("single-request", singleJson.get("requestId").asString());
    }

    @Test
    void inconsistentStatusOrBodylessResponseIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> handler.createResponse(true, "bad", null, null, HttpStatus.BAD_REQUEST, "id"));
        assertThrows(IllegalArgumentException.class,
                () -> handler.createResponse(true, "bad", null, null, HttpStatus.NO_CONTENT, "id"));
    }
}

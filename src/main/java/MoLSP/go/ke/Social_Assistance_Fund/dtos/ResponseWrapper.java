package MoLSP.go.ke.Social_Assistance_Fund.dtos;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;

@Data
@Builder
public class ResponseWrapper<T> {
    private boolean success;
    private String message;
    private T data;
    private String error;
    @Builder.Default
    private String timestamp = Instant.now().toString();
    private String requestId;

    public static <T> ResponseWrapper<T> success(T data) {
        return success(data, "Operation successful");
    }

    public static <T> ResponseWrapper<T> success(T data, String message) {
        return ResponseWrapper.<T>builder().success(true).message(message).data(data).build();
    }
}

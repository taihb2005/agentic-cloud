package vn.edu.hust.kat.common.api.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OperationFailure<F> {

    @JsonProperty("code")
    private String code;

    @JsonProperty("message")
    private String message;

    @JsonProperty("data")
    private F data;

    public static <F> OperationFailure<F> of(
            FailureCode code,
            String message,
            F data
    ) {
        return OperationFailure.<F>builder()
                .code(code.getCode())
                .message(message)
                .data(data)
                .build();
    }

    public static <F> OperationFailure<F> of(
            FailureCode code,
            String message
    ) {
        return OperationFailure.<F>builder()
                .code(code.getCode())
                .message(message)
                .build();
    }

    // ============================================================
    // Operation
    // ============================================================

    public static BodyBuilder notFound() {
        return new BodyBuilder(FailureCode.NOT_FOUND);
    }

    public static BodyBuilder forbidden() {
        return new BodyBuilder(FailureCode.FORBIDDEN);
    }

    public static BodyBuilder conflict() {
        return new BodyBuilder(FailureCode.CONFLICT);
    }

    public static BodyBuilder invalidRequest() {
        return new BodyBuilder(FailureCode.INVALID_REQUEST);
    }

    public static BodyBuilder alreadyExists() {
        return new BodyBuilder(FailureCode.ALREADY_EXISTS);
    }

    public static BodyBuilder invalidState() {
        return new BodyBuilder(FailureCode.INVALID_STATE);
    }

    public static BodyBuilder internalError() {
        return new BodyBuilder(FailureCode.INTERNAL_ERROR);
    }

    public static BodyBuilder invalidOperation() {
        return new BodyBuilder(FailureCode.INVALID_OPERATION);
    }

    public static BodyBuilder nodeNotFound() {
        return new BodyBuilder(FailureCode.NODE_NOT_FOUND);
    }

    public static BodyBuilder badRequest() {
        return new BodyBuilder(FailureCode.BAD_REQUEST);
    }

    public static BodyBuilder moveFailed() {
        return new BodyBuilder(FailureCode.MOVE_FAILED);
    }

    public static BodyBuilder nameAlreadyExists() {
        return new BodyBuilder(FailureCode.NAME_ALREADY_EXISTS);
    }

    public static BodyBuilder invalidDestination() {
        return new BodyBuilder(FailureCode.INVALID_DESTINATION);
    }

    public static BodyBuilder destinationError() {
        return new BodyBuilder(FailureCode.DESTINATION_ERROR);
    }

    public static BodyBuilder destinationNotFound() {
        return new BodyBuilder(FailureCode.DESTINATION_NOT_FOUND);
    }

    // ============================================================
    // BodyBuilder
    // ============================================================

    @AllArgsConstructor
    public static class BodyBuilder {

        private final FailureCode code;

        public <F> OperationFailure<F> body(String message, F data)
        {
            return OperationFailure.of(code, message, data);
        }

        public OperationFailure<Void> body(String message) {
            return OperationFailure.of(code, message);
        }
    }
}
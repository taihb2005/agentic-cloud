package vn.edu.hust.kat.common.api.response;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum FailureCode {

    // General
    NOT_FOUND("NOT_FOUND"),
    FORBIDDEN("FORBIDDEN"),
    CONFLICT("CONFLICT"),
    INVALID_REQUEST("INVALID_REQUEST"),
    ALREADY_EXISTS("ALREADY_EXISTS"),
    INVALID_STATE("INVALID_STATE"),
    INTERNAL_ERROR("INTERNAL_ERROR"),

    // For storage outbox
    INVALID_OPERATION("INVALID_OPERATION"),
    NODE_NOT_FOUND("NODE_NOT_FOUND"),
    BAD_REQUEST("BAD_REQUEST"),
    MOVE_FAILED("MOVE_FAILED"),
    NAME_ALREADY_EXISTS("NAME_ALREADY_EXISTS"),
    INVALID_DESTINATION("INVALID_DESTINATION"),
    DESTINATION_ERROR("DESTINATION_ERROR"),
    DESTINATION_NOT_FOUND("DESTINATION_NOT_FOUND");

    private final String code;
}
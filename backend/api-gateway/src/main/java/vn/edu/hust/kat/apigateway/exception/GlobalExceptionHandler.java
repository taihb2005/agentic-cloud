package vn.edu.hust.kat.apigateway.exception;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.web.reactive.error.ErrorWebExceptionHandler;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import vn.edu.hust.kat.common.api.response.ApiResponse;

// Handler global exception from api gateway
@Component
@Order(-1)
public class GlobalExceptionHandler implements ErrorWebExceptionHandler {

    private final ObjectMapper objectMapper;

    public GlobalExceptionHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public Mono<Void> handle(
            ServerWebExchange exchange,
            Throwable exception
    ) {
        ServerHttpResponse response = exchange.getResponse();

        if (response.isCommitted()) {
            return Mono.error(exception);
        }

        HttpStatus statusCode = HttpStatus.INTERNAL_SERVER_ERROR;
        StringBuilder message = new StringBuilder("Internal server error");

        if (exception instanceof ResponseStatusException responseStatusException) {
            //Replace with the desired http code
            statusCode = HttpStatus.valueOf(responseStatusException.getStatusCode().value());

            //Replace with the desired message
            message.setLength(0);
            message.append(responseStatusException.getMessage());
        }

        ApiResponse error = ApiResponse.error(message.toString(), statusCode);

        response.setStatusCode(HttpStatus.OK);
        response.getHeaders()
                .setContentType(MediaType.APPLICATION_JSON);

        try {
            byte[] bytes = objectMapper.writeValueAsBytes(error);

            DataBuffer buffer = response.bufferFactory()
                    .wrap(bytes);

            return response.writeWith(
                    Mono.just(buffer)
            );

        } catch (JsonProcessingException e) {
            return Mono.error(e);
        }
    }
}

package com.hotelbooking.gateway.error;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hotelbooking.gateway.tracing.TraceIdResolver;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class JsonErrorWriter {

    private final ObjectMapper objectMapper;
    private final TraceIdResolver traceIdResolver;

    public Mono<Void> write(ServerWebExchange exchange, HttpStatus status, String message) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(status);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        ErrorResponse body = toErrorResponse(exchange, status, message);
        return Mono.fromCallable(() -> toDataBuffer(response, body))
                .flatMap(buffer -> response.writeWith(Mono.just(buffer)));
    }

    private ErrorResponse toErrorResponse(ServerWebExchange exchange, HttpStatus status, String message) {
        return new ErrorResponse(
                Instant.now(),
                status.value(),
                status.getReasonPhrase(),
                message,
                exchange.getRequest().getPath().value(),
                traceIdResolver.resolve(exchange).orElse(null));
    }

    private DataBuffer toDataBuffer(ServerHttpResponse response, ErrorResponse body) throws Exception {
        return response.bufferFactory().wrap(objectMapper.writeValueAsBytes(body));
    }
}

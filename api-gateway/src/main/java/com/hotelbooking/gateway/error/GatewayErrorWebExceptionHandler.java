package com.hotelbooking.gateway.error;

import io.netty.channel.ConnectTimeoutException;
import java.net.ConnectException;
import java.util.concurrent.TimeoutException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.reactive.error.ErrorWebExceptionHandler;
import org.springframework.cloud.gateway.support.NotFoundException;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Slf4j
@Component
@Order(GatewayErrorWebExceptionHandler.ORDER)
@RequiredArgsConstructor
public class GatewayErrorWebExceptionHandler implements ErrorWebExceptionHandler {

    static final int ORDER = -2;
    static final String UPSTREAM_UNAVAILABLE_MESSAGE = "Upstream service unavailable";

    private final JsonErrorWriter errorWriter;

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable ex) {
        if (exchange.getResponse().isCommitted()) {
            return Mono.error(ex);
        }
        HttpStatus status = resolveStatus(ex);
        log.warn(
                "{} {} failed with {}: {}",
                exchange.getRequest().getMethod(),
                exchange.getRequest().getPath().value(),
                status.value(),
                ex.toString());
        return errorWriter.write(exchange, status, resolveMessage(ex, status));
    }

    private HttpStatus resolveStatus(Throwable ex) {
        if (ex instanceof ResponseStatusException statusException) {
            HttpStatus status =
                    HttpStatus.resolve(statusException.getStatusCode().value());
            return status == null ? HttpStatus.INTERNAL_SERVER_ERROR : status;
        }
        if (ex instanceof TimeoutException || ex instanceof ConnectTimeoutException) {
            return HttpStatus.GATEWAY_TIMEOUT;
        }
        if (ex instanceof ConnectException) {
            return HttpStatus.SERVICE_UNAVAILABLE;
        }
        return HttpStatus.INTERNAL_SERVER_ERROR;
    }

    private String resolveMessage(Throwable ex, HttpStatus status) {
        if (ex instanceof NotFoundException) {
            return UPSTREAM_UNAVAILABLE_MESSAGE;
        }
        if (ex instanceof ResponseStatusException statusException && statusException.getReason() != null) {
            return statusException.getReason();
        }
        return status.getReasonPhrase();
    }
}

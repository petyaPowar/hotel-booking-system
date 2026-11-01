package com.hotelbooking.gateway.filter;

import com.hotelbooking.gateway.tracing.TraceIdResolver;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Slf4j
@Component
@RequiredArgsConstructor
public class RequestLoggingFilter implements GlobalFilter, Ordered {

    public static final String TRACE_ID_HEADER = "X-Trace-Id";

    private final TraceIdResolver traceIdResolver;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        long startNanos = System.nanoTime();
        String traceId = traceIdResolver.resolve(exchange).orElse("");
        exchange.getResponse().beforeCommit(() -> addTraceIdHeader(exchange, traceId));
        return chain.filter(exchange).doFinally(signal -> logCompletion(exchange, traceId, startNanos));
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }

    private Mono<Void> addTraceIdHeader(ServerWebExchange exchange, String traceId) {
        exchange.getResponse().getHeaders().set(TRACE_ID_HEADER, traceId);
        return Mono.empty();
    }

    private void logCompletion(ServerWebExchange exchange, String traceId, long startNanos) {
        ServerHttpRequest request = exchange.getRequest();
        log.info(
                "{} {} -> {} in {} ms traceId={}",
                request.getMethod(),
                request.getPath().value(),
                exchange.getResponse().getStatusCode(),
                Duration.ofNanos(System.nanoTime() - startNanos).toMillis(),
                traceId);
    }
}

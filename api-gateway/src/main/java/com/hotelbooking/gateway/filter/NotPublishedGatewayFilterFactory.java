package com.hotelbooking.gateway.filter;

import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

@Component
public class NotPublishedGatewayFilterFactory extends AbstractGatewayFilterFactory<Object> {

    private static final String MESSAGE = "Resource not found";

    @Override
    public GatewayFilter apply(Object config) {
        return (exchange, chain) -> Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, MESSAGE));
    }
}

package com.hotelbooking.common.web;

import jakarta.servlet.http.HttpServletRequest;

public final class RequestPaths {

    public static final String FORWARDED_PREFIX_HEADER = "X-Forwarded-Prefix";

    private RequestPaths() {}

    public static String publicPath(HttpServletRequest request) {
        String prefix = request.getHeader(FORWARDED_PREFIX_HEADER);
        return prefix == null ? request.getRequestURI() : prefix + request.getRequestURI();
    }
}

package com.hotelbooking.booking.client;

import com.hotelbooking.booking.security.JwtTokenIssuer;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ServiceTokenInterceptor implements ClientHttpRequestInterceptor {

    private final JwtTokenIssuer tokenIssuer;

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution)
            throws IOException {
        request.getHeaders().setBearerAuth(tokenIssuer.issueServiceToken());
        return execution.execute(request, body);
    }
}

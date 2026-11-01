package com.hotelbooking.booking.config;

import com.hotelbooking.booking.client.ServiceTokenInterceptor;
import org.springframework.boot.autoconfigure.web.client.RestClientBuilderConfigurer;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.ClientHttpRequestFactorySettings;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class HotelClientConfig {

    @Bean
    @LoadBalanced
    public RestClient.Builder loadBalancedRestClientBuilder(
            RestClientBuilderConfigurer configurer, HotelClientProperties properties) {
        return configurer.configure(RestClient.builder()).requestFactory(requestFactory(properties));
    }

    @Bean
    public RestClient hotelRestClient(
            RestClient.Builder loadBalancedRestClientBuilder,
            HotelClientProperties properties,
            ServiceTokenInterceptor serviceTokenInterceptor) {
        return loadBalancedRestClientBuilder
                .baseUrl(properties.baseUrl())
                .requestInterceptor(serviceTokenInterceptor)
                .build();
    }

    private static ClientHttpRequestFactory requestFactory(HotelClientProperties properties) {
        ClientHttpRequestFactorySettings settings = ClientHttpRequestFactorySettings.defaults()
                .withConnectTimeout(properties.connectTimeout())
                .withReadTimeout(properties.readTimeout());
        return ClientHttpRequestFactoryBuilder.jdk().build(settings);
    }
}

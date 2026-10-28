package com.hotelbooking.booking.config;

import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.web.client.RestClient;

import org.springframework.http.client.JdkClientHttpRequestFactory;
import java.net.http.HttpClient;
import java.time.Duration;
import org.slf4j.MDC;

@Configuration
public class RestClientConfig {

    @Bean
    @Primary
    public RestClient.Builder restClientBuilder() {
        return RestClient.builder();
    }

    @Bean
    @LoadBalanced
    public RestClient.Builder loadBalancedRestClientBuilder() {
        return RestClient.builder();
    }

    @Bean
    public RestClient hotelRestClient(
            @LoadBalanced RestClient.Builder builder
    ) {
        return builder
                .baseUrl("http://hotel-service")
                .requestFactory(hotelRequestFactory())
                .requestInterceptor((request, body, execution) -> {

                    String traceId = MDC.get("traceId");

                    if (traceId != null) {
                        request.getHeaders().set(
                                "X-Trace-Id",
                                traceId
                        );
                    }

                    return execution.execute(request, body);
                })
                .build();
    }
    private JdkClientHttpRequestFactory hotelRequestFactory() {
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(2))
                .build();

        JdkClientHttpRequestFactory factory =
                new JdkClientHttpRequestFactory(client);

        factory.setReadTimeout(Duration.ofSeconds(5));

        return factory;
    }
}
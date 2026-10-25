package com.clearing.marketgateway.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class TradeServiceClientConfig {

    @Value("${services.trade-service.uri}")
    private String tradeServiceUri;

    @Bean
    public RestClient tradeServiceRestClient(RestClient.Builder builder) {
        return builder
                .baseUrl(tradeServiceUri)
                .build();
    }
}

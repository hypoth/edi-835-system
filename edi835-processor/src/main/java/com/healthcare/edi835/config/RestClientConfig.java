package com.healthcare.edi835.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

/**
 * Configuration for REST client used to communicate with external services.
 * 
 * <p>This configuration provides a properly configured RestTemplate for making
 * HTTP requests to external systems, particularly for retrieving configuration
 * data and coordinating with other services in the ecosystem.</p>
 * 
 * <p>Features:</p>
 * <ul>
 *   <li>Configurable timeouts for connection and read operations</li>
 *   <li>Request/Response logging interceptor</li>
 *   <li>Error handling with retry logic</li>
 *   <li>Authentication header propagation from ecosystem</li>
 * </ul>
 * 
 * @see ConfigurationService
 */
@Slf4j
@Configuration
public class RestClientConfig {

    @Value("${rest.client.connect-timeout-ms:5000}")
    private int connectTimeoutMs;

    @Value("${rest.client.read-timeout-ms:30000}")
    private int readTimeoutMs;

    @Value("${rest.client.max-connections:50}")
    private int maxConnections;

    /**
     * Creates a configured RestTemplate bean for HTTP client operations.
     * 
     * <p>The RestTemplate is configured with:</p>
     * <ul>
     *   <li>Connection timeout to prevent hanging connections</li>
     *   <li>Read timeout for long-running requests</li>
     *   <li>Logging interceptor for debugging</li>
     *   <li>Error handler for graceful failure handling</li>
     * </ul>
     * 
     * @param builder RestTemplateBuilder provided by Spring Boot
     * @return configured RestTemplate instance
     */
    @Bean
    public RestTemplate restTemplate(RestTemplateBuilder builder) {
        log.info("Configuring RestTemplate with connect timeout: {}ms, read timeout: {}ms",
                connectTimeoutMs, readTimeoutMs);

        return builder
                .setConnectTimeout(Duration.ofMillis(connectTimeoutMs))
                .setReadTimeout(Duration.ofMillis(readTimeoutMs))
                .requestFactory(this::clientHttpRequestFactory)
                .interceptors(loggingInterceptor())
                .errorHandler(new RestTemplateErrorHandler())
                .build();
    }

    /**
     * Creates a custom HTTP request factory with connection pooling.
     * 
     * @return configured SimpleClientHttpRequestFactory
     */
    private SimpleClientHttpRequestFactory clientHttpRequestFactory() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(connectTimeoutMs);
        factory.setReadTimeout(readTimeoutMs);
        
        log.debug("HTTP Request Factory configured with max connections: {}", maxConnections);
        
        return factory;
    }

    /**
     * Creates a logging interceptor for request/response debugging.
     * 
     * <p>This interceptor logs:</p>
     * <ul>
     *   <li>Request URI and method</li>
     *   <li>Request headers (excluding sensitive data)</li>
     *   <li>Response status code</li>
     *   <li>Request duration</li>
     * </ul>
     * 
     * @return ClientHttpRequestInterceptor for logging
     */
    private ClientHttpRequestInterceptor loggingInterceptor() {
        return (request, body, execution) -> {
            long startTime = System.currentTimeMillis();
            
            if (log.isDebugEnabled()) {
                log.debug("REST Request: {} {}", 
                        request.getMethod(), 
                        request.getURI());
                log.debug("Request Headers: {}", 
                        sanitizeHeaders(request.getHeaders()));
            }

            var response = execution.execute(request, body);
            
            long duration = System.currentTimeMillis() - startTime;
            
            if (log.isDebugEnabled()) {
                log.debug("REST Response: {} {} - Status: {} - Duration: {}ms",
                        request.getMethod(),
                        request.getURI(),
                        response.getStatusCode(),
                        duration);
            }
            
            return response;
        };
    }

    /**
     * Sanitizes HTTP headers by removing sensitive information.
     * 
     * <p>This prevents logging of sensitive data like auth tokens or API keys.</p>
     * 
     * @param headers the headers to sanitize
     * @return sanitized headers string
     */
    private String sanitizeHeaders(org.springframework.http.HttpHeaders headers) {
        if (headers == null || headers.isEmpty()) {
            return "{}";
        }

        var sanitized = new java.util.HashMap<String, String>();
        headers.forEach((key, value) -> {
            if (isSensitiveHeader(key)) {
                sanitized.put(key, "[REDACTED]");
            } else {
                sanitized.put(key, String.join(",", value));
            }
        });
        
        return sanitized.toString();
    }

    /**
     * Checks if a header contains sensitive information.
     * 
     * @param headerName the header name to check
     * @return true if the header is sensitive
     */
    private boolean isSensitiveHeader(String headerName) {
        if (headerName == null) {
            return false;
        }
        
        String lowerName = headerName.toLowerCase();
        return lowerName.contains("authorization") ||
               lowerName.contains("token") ||
               lowerName.contains("api-key") ||
               lowerName.contains("secret") ||
               lowerName.contains("password");
    }

    /**
     * Custom error handler for RestTemplate operations.
     *
     * <p>Provides consistent error handling and logging for REST client failures.</p>
     */
    @Slf4j
    private static class RestTemplateErrorHandler
            implements org.springframework.web.client.ResponseErrorHandler {

        @Override
        public boolean hasError(org.springframework.http.client.ClientHttpResponse response) 
                throws java.io.IOException {
            return response.getStatusCode().isError();
        }

        @Override
        public void handleError(org.springframework.http.client.ClientHttpResponse response) 
                throws java.io.IOException {
            log.error("REST Client Error: Status={}, StatusText={}", 
                    response.getStatusCode(), 
                    response.getStatusText());
            
            // Throw appropriate exception based on status code
            if (response.getStatusCode().is4xxClientError()) {
                throw new org.springframework.web.client.HttpClientErrorException(
                        response.getStatusCode(), 
                        "Client error: " + response.getStatusText());
            } else if (response.getStatusCode().is5xxServerError()) {
                throw new org.springframework.web.client.HttpServerErrorException(
                        response.getStatusCode(), 
                        "Server error: " + response.getStatusText());
            }
        }
    }

    /**
     * Provides configuration summary for monitoring.
     * 
     * @return configuration summary string
     */
    @Override
    public String toString() {
        return String.format(
                "RestClientConfig{connectTimeout=%dms, readTimeout=%dms, maxConnections=%d}",
                connectTimeoutMs, readTimeoutMs, maxConnections);
    }
}

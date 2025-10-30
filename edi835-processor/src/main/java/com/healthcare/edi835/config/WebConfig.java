package com.healthcare.edi835.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.Arrays;
import java.util.List;

/**
 * Web MVC configuration including CORS settings for local development.
 */
@Slf4j
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Value("${cors.allowed-origins:http://localhost:3000,http://localhost:5173}")
    private String allowedOrigins;

    @Value("${cors.enabled:true}")
    private boolean corsEnabled;

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        if (!corsEnabled) {
            log.info("CORS is disabled");
            return;
        }

        String[] origins = allowedOrigins.split(",");
        log.info("Configuring CORS with allowed origins: {}", Arrays.toString(origins));

        // Use allowedOriginPatterns instead of allowedOrigins when allowCredentials is true
        // This supports both specific origins and patterns like "http://localhost:*"
        registry.addMapping("/**")
                .allowedOriginPatterns(origins)  // Changed from allowedOrigins to allowedOriginPatterns
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH")
                .allowedHeaders("*")
                .allowCredentials(true)
                .maxAge(3600);

        log.info("CORS configuration applied successfully");
    }

    /**
     * CORS configuration source for Spring Security (if used).
     * This ensures CORS settings work with security filters.
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();

        String[] origins = allowedOrigins.split(",");
        // Use setAllowedOriginPatterns instead of setAllowedOrigins when credentials are enabled
        configuration.setAllowedOriginPatterns(Arrays.asList(origins));  // Changed from setAllowedOrigins
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);

        log.info("CORS configuration source created for origins: {}", Arrays.asList(origins));
        return source;
    }
}

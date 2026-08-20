package com.ohdomi.backend.global;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class CorsConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        // Spring's CORS filter checks the Origin header on every request that carries one
        // (browsers send it for unsafe methods even when the request is same-origin from the
        // page's own perspective, e.g. through the Vite dev proxy) — so the team's cloudflare
        // tunnel domain has to be allow-listed here too, matching closure-risk-model's CORS setup.
        registry.addMapping("/api/**")
                .allowedOriginPatterns(
                        "http://localhost:*",
                        "http://127.0.0.1:*",
                        "https://*.trycloudflare.com",
                        "https://*.ts.net",
                        "https://ohdomi.duckdns.org",
                        "http://ohdomi-web-0723.s3-website-us-east-1.amazonaws.com"
                )
                .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true);
    }
}

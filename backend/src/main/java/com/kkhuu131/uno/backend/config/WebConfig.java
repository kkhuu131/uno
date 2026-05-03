package com.kkhuu131.uno.backend.config;

import java.util.List;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

	private final CorsProperties corsProperties;

	public WebConfig(CorsProperties corsProperties) {
		this.corsProperties = corsProperties;
	}

	@Override
	public void addCorsMappings(CorsRegistry registry) {
		List<String> origins = corsProperties.allowedOrigins();
		registry.addMapping("/api/**")
				.allowedOrigins(origins.toArray(String[]::new))
				.allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
				.allowedHeaders("*");
	}
}

package com.kkhuu131.uno.backend.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Optional list in {@code application.yaml} under {@code app.cors.allowed-origins}. If omitted or empty, sensible
 * local dev defaults apply (Vite / CRA-style ports).
 */
@ConfigurationProperties(prefix = "app.cors")
public record CorsProperties(List<String> allowedOrigins) {
	public CorsProperties {
		if (allowedOrigins == null || allowedOrigins.isEmpty()) {
			allowedOrigins =
					List.of(
							"http://localhost:5173",
							"http://127.0.0.1:5173",
							"http://localhost:3000",
							"http://127.0.0.1:3000");
		} else {
			allowedOrigins = List.copyOf(allowedOrigins);
		}
	}
}

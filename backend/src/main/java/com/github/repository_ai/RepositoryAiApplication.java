package com.github.repository_ai;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@SpringBootApplication
public class RepositoryAiApplication {

	public static void main(String[] args) {
		loadDotenv();
		SpringApplication.run(RepositoryAiApplication.class, args);
	}

	/**
	 * Loads variables from a .env file (if present) into System properties,
	 * making them available to Spring's ${...} property placeholders in
	 * application.properties.
	 * <p>
	 * This replaces the spring-dotenv library which is not compatible
	 * with Spring Boot 4.x.
	 */
	private static void loadDotenv() {
		Path dotenvPath = Path.of(".env");
		if (!Files.exists(dotenvPath)) {
			return;
		}
		try {
			Files.readAllLines(dotenvPath).stream()
					.map(String::trim)
					.filter(line -> !line.isEmpty() && !line.startsWith("#"))
					.map(line -> line.split("=", 2))
					.filter(parts -> parts.length == 2)
					.forEach(parts -> {
						String key = parts[0].trim();
						String value = parts[1].trim();
						// Only set if not already provided via OS environment
						if (System.getProperty(key) == null && System.getenv(key) == null) {
							System.setProperty(key, value);
						}
					});
		} catch (IOException e) {
			System.err.println("Warning: Could not read .env file: " + e.getMessage());
		}
	}
}

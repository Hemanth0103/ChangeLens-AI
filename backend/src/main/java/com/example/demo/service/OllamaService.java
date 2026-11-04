
package com.example.demo.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.Map;

@Service
public class OllamaService {

    private final RestClient restClient;
    private final String model;

    public OllamaService(
            @Value("${ollama.base-url:http://localhost:11434}") String baseUrl,
            @Value("${ollama.model:qwen2.5-coder:3b}") String model) {

        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .build();

        this.model = model;
    }

    public String generateExplanation(String prompt) {

        if (prompt == null || prompt.isBlank()) {
            throw new IllegalArgumentException(
                    "Prompt cannot be empty"
            );
        }

        Map<String, Object> request = Map.of(
                "model", model,
                "prompt", prompt,
                "stream", false
        );

        OllamaResponse response = restClient.post()
                .uri("/api/generate")
                .body(request)
                .retrieve()
                .body(OllamaResponse.class);

        if (response == null ||
                response.response() == null ||
                response.response().isBlank()) {

            throw new IllegalStateException(
                    "Ollama returned an empty response"
            );
        }

        return response.response();
    }

    private record OllamaResponse(String response) {
    }
}

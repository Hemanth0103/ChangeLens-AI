
package com.example.demo;

import com.example.demo.controller.AiImpactController;
import com.example.demo.service.ChangeImpactService;
import com.example.demo.service.OllamaService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class AiImpactControllerTest {

    private OllamaService ollamaService;
    private AiImpactController controller;
    private AiImpactController.ImpactRequest request;

    @BeforeEach
    void setUp() {
        ollamaService = mock(OllamaService.class);

        controller = new AiImpactController(
                new ChangeImpactService(),
                ollamaService
        );

        Map<String, List<String>> dependencies = Map.of(
                "demo.PaymentService", List.of(),
                "demo.OrderService",
                List.of("demo.PaymentService"),
                "demo.OrderController",
                List.of("demo.OrderService")
        );

        request = new AiImpactController.ImpactRequest(
                "demo.PaymentService",
                dependencies
        );
    }

    @Test
    void matchingRiskDescriptionReturnsNoWarning() {
        when(ollamaService.generateExplanation(anyString()))
                .thenReturn("The structural risk is moderate.");

        ResponseEntity<Map<String, Object>> response =
                controller.explainImpact(request);

        assertEquals(HttpStatus.OK, response.getStatusCode());

        Map<String, Object> body = response.getBody();
        assertNotNull(body);

        assertEquals(
                "The structural risk is moderate.",
                body.get("aiExplanation")
        );

        assertFalse(body.containsKey("riskWarning"));

        Map<?, ?> analysis =
                (Map<?, ?>) body.get("analysis");

        assertEquals("MEDIUM", analysis.get("riskLevel"));
        assertEquals(55, analysis.get("riskScore"));
    }

    @Test
    void contradictoryRiskDescriptionProducesWarning() {
        when(ollamaService.generateExplanation(anyString()))
                .thenReturn("The structural risk is high.");

        ResponseEntity<Map<String, Object>> response =
                controller.explainImpact(request);

        assertEquals(HttpStatus.OK, response.getStatusCode());

        Map<String, Object> body = response.getBody();
        assertNotNull(body);

        assertTrue(body.containsKey("riskWarning"));

        String warning =
                (String) body.get("riskWarning");

        assertTrue(warning.contains("MEDIUM"));

        Map<?, ?> analysis =
                (Map<?, ?>) body.get("analysis");

        assertEquals("MEDIUM", analysis.get("riskLevel"));
    }

    @Test
    void ollamaFailurePreservesVerifiedAnalysis() {
        when(ollamaService.generateExplanation(anyString()))
                .thenThrow(new RuntimeException("Ollama unavailable"));

        ResponseEntity<Map<String, Object>> response =
                controller.explainImpact(request);

        assertEquals(
                HttpStatus.SERVICE_UNAVAILABLE,
                response.getStatusCode()
        );

        Map<String, Object> body = response.getBody();
        assertNotNull(body);

        assertTrue(body.containsKey("analysis"));
        assertTrue(body.containsKey("error"));

        verify(ollamaService).generateExplanation(anyString());
    }
}

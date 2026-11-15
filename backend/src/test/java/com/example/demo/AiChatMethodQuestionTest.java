
package com.example.demo;

import com.example.demo.controller.AiChatController;
import com.example.demo.service.AiResponseValidator;
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

class AiChatMethodQuestionTest {

    private OllamaService ollamaService;
    private AiChatController controller;

    private Map<String, List<String>> dependencies;
    private Map<String, List<String>> classMethods;

    @BeforeEach
    void setUp() {
        ollamaService = mock(OllamaService.class);

        controller = new AiChatController(
                ollamaService,
                new ChangeImpactService(),
                new AiResponseValidator()
        );

        dependencies = Map.of(
                "demo.PaymentService", List.of(),
                "demo.OrderService", List.of("demo.PaymentService"),
                "demo.OrderController", List.of("demo.OrderService")
        );

        classMethods = Map.of(
                "demo.PaymentService", List.of("processPayment"),
                "demo.OrderService", List.of("placeOrder"),
                "demo.OrderController", List.of("checkout")
        );
    }

    @Test
    void behaviorQuestionDoesNotBecomeMethodExistenceAnswer() {
        when(ollamaService.generateExplanation(anyString()))
                .thenReturn(
                        "PaymentService.processPayment() connects "
                                + "to a payment gateway."
                );

        AiChatController.ChatRequest request =
                new AiChatController.ChatRequest(
                        "Does PaymentService.processPayment() "
                                + "connect to a payment gateway?",
                        dependencies,
                        classMethods
                );

        ResponseEntity<?> response = controller.chat(request);

        assertEquals(HttpStatus.OK, response.getStatusCode());

        String answer = extractAnswer(response);

        assertFalse(
                answer.contains("Verified Method Declaration Check"),
                "A behavior question must not be treated as "
                        + "a method-existence question"
        );

        assertTrue(
                answer.toLowerCase().contains("behavior")
                        || answer.toLowerCase().contains("verify"),
                "The response should explain the verification limit"
        );

        verify(ollamaService).generateExplanation(anyString());
    }

    @Test
    void combinedBehaviorAndImpactQuestionIncludesVerifiedImpact() {
        when(ollamaService.generateExplanation(anyString()))
                .thenReturn(
                        "PaymentService connects to a payment gateway."
                );

        AiChatController.ChatRequest request =
                new AiChatController.ChatRequest(
                        "Does PaymentService.processPayment() "
                                + "connect to a payment gateway, "
                                + "and what happens if PaymentService changes?",
                        dependencies,
                        classMethods
                );

        ResponseEntity<?> response = controller.chat(request);

        assertEquals(HttpStatus.OK, response.getStatusCode());

        String answer = extractAnswer(response);

        assertTrue(
                answer.contains("Verified Impact Analysis"),
                "The combined question should include "
                        + "verified impact analysis"
        );

        assertTrue(
                answer.contains("demo.OrderService"),
                "The response should include direct impact"
        );

        assertTrue(
                answer.contains("demo.OrderController"),
                "The response should include indirect impact"
        );

        assertFalse(
                answer.contains("connects to a payment gateway"),
                "Unsupported behavioral claims must remain hidden"
        );

        verify(ollamaService).generateExplanation(anyString());
    }

    @Test
    void directMethodExistenceQuestionStillUsesVerifiedData() {
        AiChatController.ChatRequest request =
                new AiChatController.ChatRequest(
                        "Does PaymentService have processPayment()?",
                        dependencies,
                        classMethods
                );

        ResponseEntity<?> response = controller.chat(request);

        assertEquals(HttpStatus.OK, response.getStatusCode());

        String answer = extractAnswer(response);

        assertTrue(
                answer.contains("Verified Method Declaration Check")
        );

        assertTrue(
                answer.contains("demo.PaymentService.processPayment()")
        );

        verifyNoInteractions(ollamaService);
    }

    private String extractAnswer(ResponseEntity<?> response) {
        assertInstanceOf(Map.class, response.getBody());

        Map<?, ?> body = (Map<?, ?>) response.getBody();
        Object answer = body.get("answer");

        assertInstanceOf(String.class, answer);

        return (String) answer;
    }
}


package com.example.demo;

import com.example.demo.controller.AiChatController;
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

class AiChatControllerTest {

    private OllamaService ollamaService;
    private AiChatController controller;

    private Map<String, List<String>> dependencies;
    private Map<String, List<String>> classMethods;

    @BeforeEach
    void setUp() {

        ollamaService = mock(OllamaService.class);

        ChangeImpactService changeImpactService =
                new ChangeImpactService();

        controller = new AiChatController(
                ollamaService,
                changeImpactService
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
    void emptyQuestionReturnsBadRequest() {

        AiChatController.ChatRequest request =
                new AiChatController.ChatRequest(
                        "   ",
                        dependencies,
                        classMethods
                );

        ResponseEntity<?> response = controller.chat(request);

        assertEquals(
                HttpStatus.BAD_REQUEST,
                response.getStatusCode()
        );

        verifyNoInteractions(ollamaService);
    }

    @Test
    void missingDependenciesReturnsBadRequest() {

        AiChatController.ChatRequest request =
                new AiChatController.ChatRequest(
                        "Analyze my project",
                        Map.of(),
                        classMethods
                );

        ResponseEntity<?> response = controller.chat(request);

        assertEquals(
                HttpStatus.BAD_REQUEST,
                response.getStatusCode()
        );

        verifyNoInteractions(ollamaService);
    }

    @Test
    void validQuestionReturnsSuccessfulAnswer() {

        when(ollamaService.generateExplanation(anyString()))
                .thenReturn("Test the dependent classes.");

        AiChatController.ChatRequest request =
                new AiChatController.ChatRequest(
                        "What happens if PaymentService changes?",
                        dependencies,
                        classMethods
                );

        ResponseEntity<?> response = controller.chat(request);

        assertEquals(HttpStatus.OK, response.getStatusCode());

        String answer = extractAnswer(response);

        assertTrue(answer.contains("Verified Impact Analysis"));
        assertTrue(answer.contains("Test the dependent classes."));
    }

    @Test
    void verifiedMethodsAreIncludedInAiPrompt() {

        when(ollamaService.generateExplanation(anyString()))
                .thenReturn("Use the verified methods.");

        AiChatController.ChatRequest request =
                new AiChatController.ChatRequest(
                        "Which methods should I test?",
                        dependencies,
                        classMethods
                );

        controller.chat(request);

        verify(ollamaService).generateExplanation(
                argThat(prompt ->
                        prompt.contains("processPayment")
                                && prompt.contains("placeOrder")
                                && prompt.contains("checkout")
                )
        );
    }

    @Test
    void directAndIndirectImpactAreCorrect() {

        when(ollamaService.generateExplanation(anyString()))
                .thenReturn("Review the impacted classes.");

        AiChatController.ChatRequest request =
                new AiChatController.ChatRequest(
                        "If PaymentService changes, what is impacted?",
                        dependencies,
                        classMethods
                );

        ResponseEntity<?> response = controller.chat(request);

        String answer = extractAnswer(response);

        assertTrue(
                answer.contains(
                        "Directly impacted classes: [demo.OrderService]"
                )
        );

        assertTrue(
                answer.contains(
                        "Indirectly impacted classes: [demo.OrderController]"
                )
        );

        assertTrue(answer.contains("Total impacted: 2"));
        assertTrue(answer.contains("Structural risk score: 55/100"));
    }

    @Test
    void ollamaFailureReturnsServiceUnavailable() {

        when(ollamaService.generateExplanation(anyString()))
                .thenThrow(new RuntimeException("Ollama unavailable"));

        AiChatController.ChatRequest request =
                new AiChatController.ChatRequest(
                        "Analyze PaymentService",
                        dependencies,
                        classMethods
                );

        ResponseEntity<?> response = controller.chat(request);

        assertEquals(
                HttpStatus.SERVICE_UNAVAILABLE,
                response.getStatusCode()
        );
    }

    private String extractAnswer(ResponseEntity<?> response) {

        assertInstanceOf(Map.class, response.getBody());

        Map<?, ?> body = (Map<?, ?>) response.getBody();

        Object answer = body.get("answer");

        assertInstanceOf(String.class, answer);

        return (String) answer;
    }
}


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

        AiResponseValidator aiResponseValidator =
                new AiResponseValidator();

        controller = new AiChatController(
                ollamaService,
                changeImpactService,
                aiResponseValidator
        );

        dependencies = Map.of(
                "demo.PaymentService", List.of(),
                "demo.OrderService",
                List.of("demo.PaymentService"),
                "demo.OrderController",
                List.of("demo.OrderService")
        );

        classMethods = Map.of(
                "demo.PaymentService",
                List.of("processPayment"),
                "demo.OrderService",
                List.of("placeOrder"),
                "demo.OrderController",
                List.of("checkout")
        );
    }

    // TEST 1: Empty question

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

    // TEST 2: Missing dependencies

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

    // TEST 3: Valid general question

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

        assertEquals(
                HttpStatus.OK,
                response.getStatusCode()
        );

        String answer = extractAnswer(response);

        assertTrue(
                answer.contains("Verified Impact Analysis")
        );

        assertTrue(
                answer.contains("Test the dependent classes.")
        );

        assertTrue(
                answer.contains("AI-Generated Explanation")
        );
    }

    // TEST 4: Method context in AI prompt

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

    // TEST 5: Direct and indirect impact

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

        assertTrue(
                answer.contains("Total impacted: 2")
        );

        assertTrue(
                answer.contains("Structural risk score: 55/100")
        );
    }

    // TEST 6: Ollama failure

    @Test
    void ollamaFailureReturnsServiceUnavailable() {

        when(ollamaService.generateExplanation(anyString()))
                .thenThrow(
                        new RuntimeException("Ollama unavailable")
                );

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

    // TEST 7: Invented method warning

    @Test
    void inventedMethodProducesVisibleWarning() {

        when(ollamaService.generateExplanation(anyString()))
                .thenReturn(
                        "Call PaymentService.refundPayment() to issue refunds."
                );

        AiChatController.ChatRequest request =
                new AiChatController.ChatRequest(
                        "Analyze PaymentService",
                        dependencies,
                        classMethods
                );

        ResponseEntity<?> response = controller.chat(request);

        assertEquals(
                HttpStatus.OK,
                response.getStatusCode()
        );

        String answer = extractAnswer(response);

        assertTrue(
                answer.contains("Unverified Method References")
        );

        assertTrue(
                answer.contains(
                        "demo.PaymentService.refundPayment()"
                )
        );
    }

    // TEST 8: Verified method

    @Test
    void verifiedMethodDoesNotProduceWarning() {

        when(ollamaService.generateExplanation(anyString()))
                .thenReturn(
                        "Consider testing PaymentService.processPayment()."
                );

        AiChatController.ChatRequest request =
                new AiChatController.ChatRequest(
                        "Analyze PaymentService",
                        dependencies,
                        classMethods
                );

        ResponseEntity<?> response = controller.chat(request);

        assertEquals(
                HttpStatus.OK,
                response.getStatusCode()
        );

        String answer = extractAnswer(response);

        assertFalse(
                answer.contains("Unverified Method References")
        );
    }

    // TEST 9: Multiple invented methods

    @Test
    void multipleInventedMethodsProduceWarnings() {

        when(ollamaService.generateExplanation(anyString()))
                .thenReturn("""
                        PaymentService.refundPayment() handles refunds.
                        OrderController.deleteOrder() deletes orders.
                        """);

        AiChatController.ChatRequest request =
                new AiChatController.ChatRequest(
                        "Analyze PaymentService",
                        dependencies,
                        classMethods
                );

        ResponseEntity<?> response = controller.chat(request);

        assertEquals(
                HttpStatus.OK,
                response.getStatusCode()
        );

        String answer = extractAnswer(response);

        assertTrue(
                answer.contains(
                        "demo.PaymentService.refundPayment()"
                )
        );

        assertTrue(
                answer.contains(
                        "demo.OrderController.deleteOrder()"
                )
        );
    }

    // TEST 10: Existing method question

    @Test
    void existingMethodQuestionUsesVerifiedData() {

        AiChatController.ChatRequest request =
                new AiChatController.ChatRequest(
                        "Does OrderController have checkout()?",
                        dependencies,
                        classMethods
                );

        ResponseEntity<?> response = controller.chat(request);

        String answer = extractAnswer(response);

        assertEquals(
                HttpStatus.OK,
                response.getStatusCode()
        );

        assertTrue(
                answer.contains("demo.OrderController")
        );

        assertTrue(
                answer.contains("checkout()")
        );

        assertTrue(
                answer.contains("declared")
        );

        verifyNoInteractions(ollamaService);
    }

    // TEST 11: Missing method question

    @Test
    void missingMethodQuestionUsesVerifiedData() {

        AiChatController.ChatRequest request =
                new AiChatController.ChatRequest(
                        "Does OrderController have deleteOrder()?",
                        dependencies,
                        classMethods
                );

        ResponseEntity<?> response = controller.chat(request);

        String answer = extractAnswer(response);

        assertEquals(
                HttpStatus.OK,
                response.getStatusCode()
        );

        assertTrue(
                answer.contains("demo.OrderController")
        );

        assertTrue(
                answer.contains("deleteOrder()")
        );

        assertTrue(
                answer.contains("not listed")
        );

        assertTrue(
                answer.contains("checkout()")
        );

        verifyNoInteractions(ollamaService);
    }

    // TEST 12: Method information unavailable

    @Test
    void missingMethodInformationIsNotTreatedAsAbsence() {

        Map<String, List<String>> incompleteMethods =
                Map.of(
                        "demo.PaymentService",
                        List.of("processPayment")
                );

        AiChatController.ChatRequest request =
                new AiChatController.ChatRequest(
                        "Does OrderController have deleteOrder()?",
                        dependencies,
                        incompleteMethods
                );

        ResponseEntity<?> response = controller.chat(request);

        String answer = extractAnswer(response);

        assertEquals(
                HttpStatus.OK,
                response.getStatusCode()
        );

        assertTrue(
                answer.contains("unavailable")
        );

        assertFalse(
                answer.contains("does not declare")
        );

        verifyNoInteractions(ollamaService);
    }

    // TEST 13: Multiple method questions

    @Test
    void multipleMethodQuestionUsesVerifiedData() {

        AiChatController.ChatRequest request =
                new AiChatController.ChatRequest(
                        "Does PaymentService have refundPayment(), "
                                + "and does OrderController have deleteOrder()?",
                        dependencies,
                        classMethods
                );

        ResponseEntity<?> response = controller.chat(request);

        String answer = extractAnswer(response);

        assertEquals(
                HttpStatus.OK,
                response.getStatusCode()
        );

        assertTrue(
                answer.contains("demo.PaymentService")
        );

        assertTrue(
                answer.contains("refundPayment()")
        );

        assertTrue(
                answer.contains("demo.OrderController")
        );

        assertTrue(
                answer.contains("deleteOrder()")
        );

        verifyNoInteractions(ollamaService);
    }

    // TEST 14: General impact question still uses Ollama

    @Test
    void generalImpactQuestionStillUsesOllama() {

        when(ollamaService.generateExplanation(anyString()))
                .thenReturn("Review dependent classes.");

        AiChatController.ChatRequest request =
                new AiChatController.ChatRequest(
                        "What happens if PaymentService changes?",
                        dependencies,
                        classMethods
                );

        ResponseEntity<?> response = controller.chat(request);

        String answer = extractAnswer(response);

        assertEquals(
                HttpStatus.OK,
                response.getStatusCode()
        );

        assertTrue(
                answer.contains("Review dependent classes.")
        );

        verify(ollamaService)
                .generateExplanation(anyString());
    }

    // Helper method

    private String extractAnswer(ResponseEntity<?> response) {

        assertInstanceOf(
                Map.class,
                response.getBody()
        );

        Map<?, ?> body =
                (Map<?, ?>) response.getBody();

        Object answer = body.get("answer");

        assertInstanceOf(
                String.class,
                answer
        );

        return (String) answer;
    }
}

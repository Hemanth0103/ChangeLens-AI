
package com.example.demo;

import com.example.demo.service.AiResponseValidator;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class AiResponseValidatorTest {

    private AiResponseValidator validator;

    private Map<String, List<String>> classMethods;

    @BeforeEach
    void setUp() {

        validator = new AiResponseValidator();

        classMethods = Map.of(
                "demo.PaymentService", List.of("processPayment"),
                "demo.OrderService", List.of("placeOrder"),
                "demo.OrderController", List.of("checkout")
        );
    }

    @Test
    void verifiedMethodProducesNoWarnings() {

        String answer =
                "Call PaymentService.processPayment() to test payment.";

        List<String> warnings =
                validator.findUnverifiedMethods(answer, classMethods);

        assertTrue(warnings.isEmpty());
    }

    @Test
    void inventedMethodIsDetected() {

        String answer =
                "Call PaymentService.refundPayment() to process refunds.";

        List<String> warnings =
                validator.findUnverifiedMethods(answer, classMethods);

        assertEquals(1, warnings.size());

        assertTrue(
                warnings.contains("demo.PaymentService.refundPayment()")
        );
    }

    @Test
    void fullyQualifiedClassNameIsSupported() {

        String answer =
                "Use demo.OrderService.cancelOrder() for cancellation.";

        List<String> warnings =
                validator.findUnverifiedMethods(answer, classMethods);

        assertEquals(
                List.of("demo.OrderService.cancelOrder()"),
                warnings
        );
    }

    @Test
    void multipleInventedMethodsAreDetected() {

        String answer = """
                PaymentService.refundPayment() handles refunds.
                OrderController.deleteOrder() removes orders.
                """;

        List<String> warnings =
                validator.findUnverifiedMethods(answer, classMethods);

        assertEquals(2, warnings.size());

        assertTrue(
                warnings.contains("demo.PaymentService.refundPayment()")
        );

        assertTrue(
                warnings.contains("demo.OrderController.deleteOrder()")
        );
    }

    @Test
    void duplicateReferencesProduceOneWarning() {

        String answer = """
                PaymentService.refundPayment() is available.
                Test PaymentService.refundPayment() again.
                """;

        List<String> warnings =
                validator.findUnverifiedMethods(answer, classMethods);

        assertEquals(1, warnings.size());
    }

    @Test
    void blankAnswerProducesNoWarnings() {

        List<String> warnings =
                validator.findUnverifiedMethods("   ", classMethods);

        assertTrue(warnings.isEmpty());
    }

    @Test
    void missingMethodInformationProducesNoWarnings() {

        List<String> warnings =
                validator.findUnverifiedMethods(
                        "PaymentService.refundPayment()",
                        Map.of()
                );

        assertTrue(warnings.isEmpty());
    }

    @Test
    void ordinaryTextDoesNotTriggerWarnings() {

        String answer =
                "PaymentService may affect OrderService.";

        List<String> warnings =
                validator.findUnverifiedMethods(answer, classMethods);

        assertTrue(warnings.isEmpty());
    }
}

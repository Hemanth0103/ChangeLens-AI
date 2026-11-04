
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
    void verifiedQualifiedMethodProducesNoWarning() {

        String answer =
                "Call PaymentService.processPayment() to process payments.";

        List<String> warnings =
                validator.findUnverifiedMethods(answer, classMethods);

        assertTrue(warnings.isEmpty());
    }

    @Test
    void inventedQualifiedMethodProducesWarning() {

        String answer =
                "Call PaymentService.refundPayment() to issue refunds.";

        List<String> warnings =
                validator.findUnverifiedMethods(answer, classMethods);

        assertEquals(
                List.of("demo.PaymentService.refundPayment()"),
                warnings
        );
    }

    @Test
    void fullyQualifiedClassNameIsSupported() {

        String answer =
                "Call demo.PaymentService.refundPayment() to issue refunds.";

        List<String> warnings =
                validator.findUnverifiedMethods(answer, classMethods);

        assertEquals(
                List.of("demo.PaymentService.refundPayment()"),
                warnings
        );
    }

    @Test
    void inventedUnqualifiedMethodWithClassContextProducesWarning() {

        String answer =
                "PaymentService has a refundPayment() method.";

        List<String> warnings =
                validator.findUnverifiedMethods(answer, classMethods);

        assertEquals(
                List.of("demo.PaymentService.refundPayment()"),
                warnings
        );
    }

    @Test
    void verifiedUnqualifiedMethodWithClassContextProducesNoWarning() {

        String answer =
                "OrderService uses placeOrder().";

        List<String> warnings =
                validator.findUnverifiedMethods(answer, classMethods);

        assertTrue(warnings.isEmpty());
    }

    @Test
    void unqualifiedMethodWithoutClassContextIsIgnored() {

        String answer =
                "Call refundPayment() to issue a refund.";

        List<String> warnings =
                validator.findUnverifiedMethods(answer, classMethods);

        assertTrue(warnings.isEmpty());
    }

    @Test
    void ambiguousClassContextIsIgnoredForUnqualifiedMethods() {

        String answer =
                "PaymentService and OrderService may call refundPayment().";

        List<String> warnings =
                validator.findUnverifiedMethods(answer, classMethods);

        assertTrue(warnings.isEmpty());
    }

    @Test
    void multipleInventedQualifiedMethodsProduceWarnings() {

        String answer = """
                PaymentService.refundPayment() handles refunds.
                OrderController.deleteOrder() deletes orders.
                """;

        List<String> warnings =
                validator.findUnverifiedMethods(answer, classMethods);

        assertEquals(
                List.of(
                        "demo.OrderController.deleteOrder()",
                        "demo.PaymentService.refundPayment()"
                ),
                warnings
        );
    }

    @Test
    void duplicateReferencesProduceOneWarning() {

        String answer = """
                PaymentService.refundPayment() handles refunds.
                PaymentService.refundPayment() should be tested.
                """;

        List<String> warnings =
                validator.findUnverifiedMethods(answer, classMethods);

        assertEquals(
                List.of("demo.PaymentService.refundPayment()"),
                warnings
        );
    }

    @Test
    void commonJavaMethodsAreIgnoredWhenUnqualified() {

        String answer =
                "PaymentService may use toString() or equals().";

        List<String> warnings =
                validator.findUnverifiedMethods(answer, classMethods);

        assertTrue(warnings.isEmpty());
    }

    @Test
    void constructorReferenceIsNotFlagged() {

        String answer =
                "PaymentService creates a new PaymentService().";

        List<String> warnings =
                validator.findUnverifiedMethods(answer, classMethods);

        assertTrue(warnings.isEmpty());
    }

    @Test
    void unrelatedQualifiedMethodIsIgnored() {

        String answer =
                "Call external.Logger.log() to record the result.";

        List<String> warnings =
                validator.findUnverifiedMethods(answer, classMethods);

        assertTrue(warnings.isEmpty());
    }

    @Test
    void blankAnswerProducesNoWarnings() {

        assertTrue(
                validator.findUnverifiedMethods(
                        "   ",
                        classMethods
                ).isEmpty()
        );
    }

    @Test
    void missingMethodContextProducesNoWarnings() {

        assertTrue(
                validator.findUnverifiedMethods(
                        "PaymentService.refundPayment()",
                        Map.of()
                ).isEmpty()
        );
    }

    @Test
    void ordinaryExplanationProducesNoWarnings() {

        String answer =
                "Changes to PaymentService may affect OrderService.";

        List<String> warnings =
                validator.findUnverifiedMethods(answer, classMethods);

        assertTrue(warnings.isEmpty());
    }
}

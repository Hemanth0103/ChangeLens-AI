
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
        assertNoWarnings(
                "Call PaymentService.processPayment() to process payments."
        );
    }

    @Test
    void inventedQualifiedMethodProducesWarning() {
        assertWarnings(
                "Call PaymentService.refundPayment() to issue refunds.",
                "demo.PaymentService.refundPayment()"
        );
    }

    @Test
    void fullyQualifiedClassNameIsSupported() {
        assertWarnings(
                "Call demo.PaymentService.refundPayment() to issue refunds.",
                "demo.PaymentService.refundPayment()"
        );
    }

    @Test
    void inventedUnqualifiedMethodWithClassContextProducesWarning() {
        assertWarnings(
                "PaymentService has a refundPayment() method.",
                "demo.PaymentService.refundPayment()"
        );
    }

    @Test
    void verifiedUnqualifiedMethodWithClassContextProducesNoWarning() {
        assertNoWarnings(
                "OrderService uses placeOrder()."
        );
    }

    @Test
    void unqualifiedMethodWithoutClassContextIsIgnored() {
        assertNoWarnings(
                "Call refundPayment() to issue a refund."
        );
    }

    @Test
    void ambiguousClassContextIsIgnoredForUnqualifiedMethods() {
        assertNoWarnings(
                "PaymentService and OrderService may call refundPayment()."
        );
    }

    @Test
    void multipleInventedQualifiedMethodsProduceWarnings() {
        assertWarnings(
                """
                PaymentService.refundPayment() handles refunds.
                OrderController.deleteOrder() deletes orders.
                """,
                "demo.OrderController.deleteOrder()",
                "demo.PaymentService.refundPayment()"
        );
    }

    @Test
    void duplicateReferencesProduceOneWarning() {
        assertWarnings(
                """
                PaymentService.refundPayment() handles refunds.
                PaymentService.refundPayment() should be tested.
                """,
                "demo.PaymentService.refundPayment()"
        );
    }

    @Test
    void commonJavaMethodsAreIgnoredWhenUnqualified() {
        assertNoWarnings(
                "PaymentService may use toString() or equals()."
        );
    }

    @Test
    void constructorReferenceIsNotFlagged() {
        assertNoWarnings(
                "PaymentService creates a new PaymentService()."
        );
    }

    @Test
    void unrelatedQualifiedMethodIsIgnored() {
        assertNoWarnings(
                "Call external.Logger.log() to record the result."
        );
    }

    @Test
    void blankAnswerProducesNoWarnings() {
        assertNoWarnings("   ");
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
        assertNoWarnings(
                "Changes to PaymentService may affect OrderService."
        );
    }

    // Negative statement tests

    @Test
    void doesNotHaveMethodProducesNoWarning() {
        assertNoWarnings(
                "No, PaymentService does not have a refundPayment() method."
        );
    }

    @Test
    void doesntDeclareMethodProducesNoWarning() {
        assertNoWarnings(
                "PaymentService doesn't declare refundPayment()."
        );
    }

    @Test
    void methodNotFoundProducesNoWarning() {
        assertNoWarnings(
                "PaymentService.refundPayment() was not found."
        );
    }

    @Test
    void cannotVerifyMethodProducesNoWarning() {
        assertNoWarnings(
                "I cannot verify PaymentService.refundPayment()."
        );
    }

    @Test
    void noSuchMethodProducesNoWarning() {
        assertNoWarnings(
                "There is no such method as PaymentService.refundPayment()."
        );
    }

    @Test
    void separatePositiveAndNegativeSentencesAreHandled() {
        assertWarnings(
                """
                PaymentService does not have refundPayment().
                OrderController.deleteOrder() deletes orders.
                """,
                "demo.OrderController.deleteOrder()"
        );
    }

    @Test
    void positiveClaimStillProducesWarning() {
        assertWarnings(
                "PaymentService definitely implements refundPayment().",
                "demo.PaymentService.refundPayment()"
        );
    }

    // New regression tests: mixed positive and negative claims

    @Test
    void negativeAndPositiveClaimsInSameSentence() {
        assertWarnings(
                "PaymentService does not have refundPayment(), "
                        + "but OrderController.deleteOrder() deletes orders.",
                "demo.OrderController.deleteOrder()"
        );
    }

    @Test
    void positiveClaimBeforeNegativeClaim() {
        assertWarnings(
                "OrderController.deleteOrder() deletes orders, "
                        + "but PaymentService does not have refundPayment().",
                "demo.OrderController.deleteOrder()"
        );
    }

    @Test
    void negativeAndPositiveUnqualifiedMethodsInSameSentence() {
        assertWarnings(
                "PaymentService does not have refundPayment(), "
                        + "but OrderService provides cancelOrder().",
                "demo.OrderService.cancelOrder()"
        );
    }

    @Test
    void twoNegativeClaimsInSameSentenceProduceNoWarnings() {
        assertNoWarnings(
                "PaymentService does not have refundPayment(), "
                        + "and OrderController does not declare deleteOrder()."
        );
    }

    // Assertion helpers

    private void assertNoWarnings(String answer) {
        assertTrue(
                validator.findUnverifiedMethods(
                        answer,
                        classMethods
                ).isEmpty(),
                "Unexpected warnings for: " + answer
        );
    }

    private void assertWarnings(
            String answer,
            String... expectedWarnings) {

        List<String> actual =
                validator.findUnverifiedMethods(
                        answer,
                        classMethods
                );

        assertEquals(
                List.of(expectedWarnings),
                actual,
                "Incorrect warnings for: " + answer
        );
    }
}


package com.example.demo;

import com.example.demo.service.ChangeImpactService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ChangeImpactServiceTest {

    private final ChangeImpactService service =
            new ChangeImpactService();

    @Test
    void shouldDetectDirectAndIndirectImpact() {

        Map<String, List<String>> dependencies = Map.of(
                "OrderController", List.of("OrderService"),
                "OrderService", List.of("PaymentService"),
                "PaymentService", List.of()
        );

        Map<String, Object> result =
                service.analyzeImpact(
                        "PaymentService", dependencies);

        assertEquals("PaymentService",
                result.get("changedClass"));

        assertEquals(2, result.get("totalImpacted"));
        assertEquals(1, result.get("directImpact"));
        assertEquals(1, result.get("indirectImpact"));
        assertEquals(2, result.get("maxDepth"));
        assertEquals(100.0,
                (Double) result.get("projectReach"), 0.001);

        assertEquals(55, result.get("riskScore"));
        assertEquals("MEDIUM", result.get("riskLevel"));

        @SuppressWarnings("unchecked")
        List<String> impacted =
                (List<String>) result.get("impactedClasses");

        assertTrue(impacted.contains("OrderService"));
        assertTrue(impacted.contains("OrderController"));
    }

    @Test
    void shouldReturnLowRiskWhenNoClassesAreImpacted() {

        Map<String, List<String>> dependencies = Map.of(
                "PaymentService", List.of(),
                "OrderService", List.of()
        );

        Map<String, Object> result =
                service.analyzeImpact(
                        "PaymentService", dependencies);

        assertEquals(0, result.get("totalImpacted"));
        assertEquals(0, result.get("directImpact"));
        assertEquals(0, result.get("indirectImpact"));
        assertEquals(0, result.get("maxDepth"));
        assertEquals(0, result.get("riskScore"));
        assertEquals("LOW", result.get("riskLevel"));
    }

    @Test
    void shouldReturnHighRiskForHighlyConnectedClass() {

        Map<String, List<String>> dependencies = Map.of(
                "CoreService", List.of(),
                "ServiceA", List.of("CoreService"),
                "ServiceB", List.of("CoreService"),
                "ServiceC", List.of("CoreService"),
                "ServiceD", List.of("CoreService"),
                "ServiceE", List.of("CoreService"),
                "ControllerA", List.of("ServiceA"),
                "ControllerB", List.of("ServiceB")
        );

        Map<String, Object> result =
                service.analyzeImpact(
                        "CoreService", dependencies);

        assertEquals(7, result.get("totalImpacted"));
        assertEquals(5, result.get("directImpact"));
        assertEquals(2, result.get("indirectImpact"));
        assertEquals(2, result.get("maxDepth"));
        assertEquals(100.0,
                (Double) result.get("projectReach"), 0.001);

        assertEquals(80, result.get("riskScore"));
        assertEquals("HIGH", result.get("riskLevel"));
    }

    @Test
    void shouldHandleCircularDependencies() {

        Map<String, List<String>> dependencies = Map.of(
                "ClassA", List.of("ClassB"),
                "ClassB", List.of("ClassA")
        );

        Map<String, Object> result =
                service.analyzeImpact("ClassA", dependencies);

        assertEquals(1, result.get("totalImpacted"));
        assertEquals(1, result.get("directImpact"));
        assertEquals(0, result.get("indirectImpact"));

        @SuppressWarnings("unchecked")
        List<String> impacted =
                (List<String>) result.get("impactedClasses");

        assertEquals(List.of("ClassB"), impacted);
    }

    @Test
    void shouldRejectBlankChangedClass() {

        assertThrows(
                IllegalArgumentException.class,
                () -> service.analyzeImpact(
                        " ", Map.of())
        );
    }

    @Test
    void shouldRejectNullDependencies() {

        assertThrows(
                IllegalArgumentException.class,
                () -> service.analyzeImpact(
                        "PaymentService", null)
        );
    }
}

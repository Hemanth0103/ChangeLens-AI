package com.example.demo;

import com.example.demo.service.ChangeImpactService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ChangeImpactServiceTest {

    @Test
    void shouldDetectDirectAndIndirectImpact() {

        ChangeImpactService service = new ChangeImpactService();

        Map<String, List<String>> dependencies = Map.of(
            "OrderController", List.of("OrderService"),
            "OrderService", List.of("PaymentService"),
            "PaymentService", List.of()
        );

        Map<String, Object> result =
                service.analyzeImpact("PaymentService", dependencies);

        assertEquals("PaymentService", result.get("changedClass"));
        assertEquals(2, result.get("totalImpacted"));

        @SuppressWarnings("unchecked")
        List<String> impacted =
                (List<String>) result.get("impactedClasses");

        assertTrue(impacted.contains("OrderService"));
        assertTrue(impacted.contains("OrderController"));
    }
}
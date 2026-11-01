package com.example.demo;

import com.example.demo.service.AutomaticImpactAnalysisService;
import com.example.demo.service.ChangeImpactService;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class AutomaticImpactAnalysisServiceTest {

    @Test
    void shouldAutomaticallyDetectDirectAndIndirectImpact() {

        ChangeImpactService changeImpactService =
                new ChangeImpactService();

        AutomaticImpactAnalysisService service =
                new AutomaticImpactAnalysisService(changeImpactService);

        Map<String, String> sourceFiles = Map.of(
                "PaymentService.java",
                """
                public class PaymentService {
                    public void processPayment() {}
                }
                """,

                "OrderService.java",
                """
                public class OrderService {
                    private PaymentService paymentService;
                }
                """,

                "OrderController.java",
                """
                public class OrderController {
                    private OrderService orderService;
                }
                """
        );

        Map<String, Object> result =
                service.analyze("PaymentService", sourceFiles);

        assertEquals("PaymentService", result.get("changedClass"));

        assertEquals(2, result.get("totalImpacted"));

        @SuppressWarnings("unchecked")
        List<String> impactedClasses =
                (List<String>) result.get("impactedClasses");

        assertTrue(impactedClasses.contains("OrderService"));

        assertTrue(impactedClasses.contains("OrderController"));

        assertEquals(2, impactedClasses.size());
    }
}
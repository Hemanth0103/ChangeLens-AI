
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

    @Test
    void shouldExcludeExternalTypesFromProjectDependencies() {

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
                import java.util.List;

                public class OrderService {
                    private PaymentService paymentService;
                    private List<String> orders;
                }
                """
        );

        Map<String, Object> result =
                service.analyze("PaymentService", sourceFiles);

        @SuppressWarnings("unchecked")
        Map<String, List<String>> dependencies =
                (Map<String, List<String>>) result.get("dependencies");

        assertEquals(
                List.of("PaymentService"),
                dependencies.get("OrderService")
        );

        assertEquals(1, result.get("totalImpacted"));
        assertEquals(1, result.get("directImpact"));
        assertEquals(0, result.get("indirectImpact"));

        assertEquals(
                100.0,
                (Double) result.get("projectReach"),
                0.001
        );
    }

    @Test
    void shouldDistinguishClassesWithSameNameInDifferentPackages() {

        ChangeImpactService changeImpactService =
                new ChangeImpactService();

        AutomaticImpactAnalysisService service =
                new AutomaticImpactAnalysisService(changeImpactService);

        Map<String, String> sourceFiles = Map.of(
                "payment/PaymentService.java",
                """
                package com.example.payment;

                public class PaymentService {
                    public void processPayment() {}
                }
                """,

                "shipping/PaymentService.java",
                """
                package com.example.shipping;

                public class PaymentService {
                    public void arrangeShipping() {}
                }
                """,

                "order/OrderService.java",
                """
                package com.example.order;

                import com.example.payment.PaymentService;

                public class OrderService {
                    private PaymentService paymentService;
                }
                """
        );

        Map<String, Object> result = service.analyze(
                "com.example.payment.PaymentService",
                sourceFiles
        );

        @SuppressWarnings("unchecked")
        Map<String, List<String>> dependencies =
                (Map<String, List<String>>) result.get("dependencies");

        assertEquals(
                List.of("com.example.payment.PaymentService"),
                dependencies.get("com.example.order.OrderService")
        );

        assertTrue(
                dependencies.containsKey(
                        "com.example.shipping.PaymentService"
                )
        );

        assertEquals(1, result.get("totalImpacted"));

        @SuppressWarnings("unchecked")
        List<String> impactedClasses =
                (List<String>) result.get("impactedClasses");

        assertEquals(
                List.of("com.example.order.OrderService"),
                impactedClasses
        );
    }


@Test
void shouldResolveWildcardImportsToCorrectPackage() {

    ChangeImpactService changeImpactService =
            new ChangeImpactService();

    AutomaticImpactAnalysisService service =
            new AutomaticImpactAnalysisService(changeImpactService);

    Map<String, String> sourceFiles = Map.of(
            "payment/PaymentService.java",
            """
            package com.example.payment;

            public class PaymentService {
                public void processPayment() {}
            }
            """,

            "shipping/PaymentService.java",
            """
            package com.example.shipping;

            public class PaymentService {
                public void arrangeShipping() {}
            }
            """,

            "order/OrderService.java",
            """
            package com.example.order;

            import com.example.payment.*;

            public class OrderService {
                private PaymentService paymentService;
            }
            """
    );

    Map<String, Object> result = service.analyze(
            "com.example.payment.PaymentService",
            sourceFiles
    );

    @SuppressWarnings("unchecked")
    Map<String, List<String>> dependencies =
            (Map<String, List<String>>) result.get("dependencies");

    assertEquals(
            List.of("com.example.payment.PaymentService"),
            dependencies.get("com.example.order.OrderService")
    );

    assertEquals(
            List.of(),
            dependencies.get("com.example.shipping.PaymentService")
    );

    assertEquals(1, result.get("totalImpacted"));

    @SuppressWarnings("unchecked")
    List<String> impactedClasses =
            (List<String>) result.get("impactedClasses");

    assertEquals(
            List.of("com.example.order.OrderService"),
            impactedClasses
    );
}

}

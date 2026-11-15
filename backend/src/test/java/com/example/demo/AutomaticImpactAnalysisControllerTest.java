
package com.example.demo;

import com.example.demo.controller.AutomaticImpactAnalysisController;
import com.example.demo.service.AutomaticImpactAnalysisService;
import com.example.demo.service.ChangeImpactService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class AutomaticImpactAnalysisControllerTest {

    private AutomaticImpactAnalysisController controller;

    @BeforeEach
    void setUp() {
        ChangeImpactService changeImpactService =
                new ChangeImpactService();

        AutomaticImpactAnalysisService service =
                new AutomaticImpactAnalysisService(changeImpactService);

        controller = new AutomaticImpactAnalysisController(service);
    }

    @Test
    void shouldReturnOkForValidSourceFiles() {
        Map<String, String> sourceFiles = Map.of(
                "PaymentService.java",
                "public class PaymentService {}",
                "OrderService.java",
                "public class OrderService { private PaymentService paymentService; }"
        );

        ResponseEntity<Map<String, Object>> response =
                controller.analyzeAutomaticImpact(
                        new AutomaticImpactAnalysisController.AutomaticImpactRequest(
                                "PaymentService", sourceFiles
                        )
                );

        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals(1, response.getBody().get("totalImpacted"));
    }

    @Test
    void shouldReturnBadRequestForInvalidJavaSyntax() {
        Map<String, String> sourceFiles = Map.of(
                "Broken.java", "public class Broken {"
        );

        assertBadRequest(
                "Broken",
                sourceFiles,
                "Unable to parse source file: Broken.java"
        );
    }

    @Test
    void shouldReturnBadRequestForEmptySourceFiles() {
        assertBadRequest(
                "PaymentService",
                Map.of(),
                "Source files must not be empty"
        );
    }

    @Test
    void shouldReturnBadRequestForBlankChangedClass() {
        assertBadRequest(
                " ",
                Map.of("PaymentService.java", "public class PaymentService {}"),
                "Changed class must not be empty"
        );
    }

    @Test
    void shouldReturnBadRequestForUnknownChangedClass() {
        assertBadRequest(
                "UnknownService",
                Map.of("PaymentService.java", "public class PaymentService {}"),
                "Changed class not found in project: UnknownService"
        );
    }

    @Test
    void shouldReturnBadRequestForAmbiguousChangedClass() {
        Map<String, String> sourceFiles = Map.of(
                "payment/PaymentService.java",
                "package com.example.payment; public class PaymentService {}",
                "shipping/PaymentService.java",
                "package com.example.shipping; public class PaymentService {}"
        );

        assertBadRequest(
                "PaymentService",
                sourceFiles,
                "Ambiguous changed class: PaymentService. Use a fully qualified name."
        );
    }

    private void assertBadRequest(
            String changedClass,
            Map<String, String> sourceFiles,
            String expectedError) {

        ResponseEntity<Map<String, Object>> response =
                controller.analyzeAutomaticImpact(
                        new AutomaticImpactAnalysisController.AutomaticImpactRequest(
                                changedClass, sourceFiles
                        )
                );

        assertEquals(400, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals(expectedError, response.getBody().get("error"));
    }
}

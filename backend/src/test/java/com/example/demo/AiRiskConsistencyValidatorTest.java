
package com.example.demo;

import com.example.demo.service.AiRiskConsistencyValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AiRiskConsistencyValidatorTest {

    private AiRiskConsistencyValidator validator;

    @BeforeEach
    void setUp() {
        validator = new AiRiskConsistencyValidator();
    }

    @Test
    void matchingLowRiskIsValid() {
        assertFalse(validator.hasContradiction(
                "The structural risk is low.", "LOW"));
    }

    @Test
    void matchingMediumRiskIsValid() {
        assertFalse(validator.hasContradiction(
                "The structural risk is moderate.", "MEDIUM"));
    }

    @Test
    void matchingHighRiskIsValid() {
        assertFalse(validator.hasContradiction(
                "This change has high structural risk.", "HIGH"));
    }

    @Test
    void moderateDescriptionContradictsLowRisk() {
        assertTrue(validator.hasContradiction(
                "This change presents moderate structural risk.", "LOW"));
    }

    @Test
    void highDescriptionContradictsMediumRisk() {
        assertTrue(validator.hasContradiction(
                "The structural risk is high.", "MEDIUM"));
    }

    @Test
    void lowDescriptionContradictsHighRisk() {
        assertTrue(validator.hasContradiction(
                "The overall structural risk is low.", "HIGH"));
    }

    @Test
    void numericRiskScoreIsNotAContradiction() {
        assertFalse(validator.hasContradiction(
                "The structural risk score is 30/100.", "LOW"));
    }

    @Test
    void UncertainExplanationIsNotAContradiction() {
        assertFalse(validator.hasContradiction(
                "The probability of a bug cannot be determined.", "LOW"));
    }

    @Test
    void blankExplanationIsNotAContradiction() {
        assertFalse(validator.hasContradiction(" ", "LOW"));
    }

    @Test
    void unrelatedHighPriorityDoesNotImplyHighRisk() {
        assertFalse(validator.hasContradiction(
                "High-priority tests are recommended.", "LOW"));
    }

    @Test
    void riskDescriptionsAreCaseInsensitive() {
        assertTrue(validator.hasContradiction(
                "The STRUCTURAL RISK is MODERATE.", "LOW"));
    }
}

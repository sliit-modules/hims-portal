package com.medisure.hims.model;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.List;

/**
 * The system's suggested risk score for an underwriting application, together with the points
 * each factor contributed, so the underwriter can see exactly why the number was suggested.
 */
@Getter
@AllArgsConstructor
public class RiskAssessment {

    private final int score;
    private final BigDecimal suggestedLoadingPercent;
    private final boolean highRisk;
    private final List<Factor> factors;

    @Getter
    @AllArgsConstructor
    public static class Factor {
        private final String label;
        private final int points;
    }
}

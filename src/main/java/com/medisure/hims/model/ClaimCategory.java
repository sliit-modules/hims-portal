package com.medisure.hims.model;

public enum ClaimCategory {
    ANNUAL_CHECKUP("Annual Checkup"),
    DENTAL("Dental"),
    HOSPITALIZATION("Hospitalization"),
    SURGERY("Surgery"),
    MATERNITY("Maternity"),
    CANCER_SCREENING("Cancer Screening"),
    EMERGENCY("Emergency"),
    OTHER("Other");

    private final String label;

    ClaimCategory(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}

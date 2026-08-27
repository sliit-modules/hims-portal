package com.medisure.hims.model;

public enum BloodGroup {
    A_POSITIVE("A+"),
    A_NEGATIVE("A-"),
    B_POSITIVE("B+"),
    B_NEGATIVE("B-"),
    AB_POSITIVE("AB+"),
    AB_NEGATIVE("AB-"),
    O_POSITIVE("O+"),
    O_NEGATIVE("O-"),
    UNKNOWN("Not known");

    private final String label;

    BloodGroup(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}

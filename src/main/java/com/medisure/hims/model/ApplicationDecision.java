package com.medisure.hims.model;

public enum ApplicationDecision {
    PENDING,
    APPROVED,
    REJECTED,
    /** The applicant took the application back before it was decided. */
    WITHDRAWN
}

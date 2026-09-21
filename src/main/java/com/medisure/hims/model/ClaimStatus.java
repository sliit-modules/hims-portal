package com.medisure.hims.model;

public enum ClaimStatus {
    SUBMITTED,
    APPROVED,
    REJECTED,
    WITHDRAWN,
    /** Closed by a claims officer as a duplicate of another claim; never paid. */
    VOIDED
}

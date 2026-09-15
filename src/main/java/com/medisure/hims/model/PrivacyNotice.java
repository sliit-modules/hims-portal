package com.medisure.hims.model;

/**
 * The privacy notice members must accept. Bump {@link #VERSION} whenever the notice text changes,
 * so every member is asked to accept the new version on their next sign-in.
 */
public final class PrivacyNotice {

    public static final String VERSION = "2026-09";

    private PrivacyNotice() {
    }
}

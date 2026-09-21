package com.medisure.hims.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * PayHere settings from application.yml ("payhere:"). Secrets belong only in the local
 * application.yml or in environment variables — never in Git.
 */
@Component
@ConfigurationProperties(prefix = "payhere")
@Getter
@Setter
public class PayHereProperties {

    /** Online payment is offered only when this is true and the merchant details are filled in. */
    private boolean enabled = false;

    /** Use PayHere's sandbox (test) site rather than the live one. */
    private boolean sandbox = true;

    private String merchantId;
    private String merchantSecret;

    /** App ID and App Secret of an API key with "Payment Retrieval API" permission. */
    private String appId;
    private String appSecret;

    /** Where this application is reached from the member's browser, for the return and cancel links. */
    private String appBaseUrl = "http://localhost:8080";

    /** A public URL for PayHere's server-to-server notification; blank uses appBaseUrl. */
    private String notifyUrl;

    public String baseUrl() {
        return sandbox ? "https://sandbox.payhere.lk" : "https://www.payhere.lk";
    }

    public String checkoutUrl() {
        return baseUrl() + "/pay/checkout";
    }

    public boolean isCheckoutReady() {
        return enabled && hasText(merchantId) && hasText(merchantSecret);
    }

    public boolean isRetrievalReady() {
        return hasText(appId) && hasText(appSecret);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}

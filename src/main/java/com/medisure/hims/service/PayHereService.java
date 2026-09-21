package com.medisure.hims.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.medisure.hims.config.PayHereProperties;
import com.medisure.hims.model.*;
import com.medisure.hims.repository.PayHereCheckoutRepository;
import com.medisure.hims.repository.PaymentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.springframework.http.HttpStatus.NOT_FOUND;

/**
 * Premium payments through the PayHere payment gateway (sandbox or live).
 *
 * <ul>
 *   <li>Checkout: the member is sent to PayHere with a signed request
 *       ({@code hash = UPPER(MD5(merchant_id + order_id + amount + currency + UPPER(MD5(secret))))}).</li>
 *   <li>Confirmation: PayHere posts a signed notification ({@code md5sig}) to notify_url, and — because a
 *       laptop cannot receive that — the app also asks PayHere's Retrieval API when the member returns.</li>
 *   <li>Either way the premium Payment is recorded exactly once, through {@link PaymentService}.</li>
 * </ul>
 */
@Service
public class PayHereService {

    public static final int STATUS_SUCCESS = 2;
    public static final int STATUS_CANCELLED = -1;
    public static final int STATUS_FAILED = -2;

    private static final Logger log = LoggerFactory.getLogger(PayHereService.class);

    private final PayHereProperties props;
    private final PayHereCheckoutRepository checkoutRepository;
    private final PaymentService paymentService;
    private final PaymentRepository paymentRepository;
    private final AuditService auditService;
    private final ObjectMapper json = new ObjectMapper();
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    public PayHereService(PayHereProperties props, PayHereCheckoutRepository checkoutRepository,
                          PaymentService paymentService, PaymentRepository paymentRepository,
                          AuditService auditService) {
        this.props = props;
        this.checkoutRepository = checkoutRepository;
        this.paymentService = paymentService;
        this.paymentRepository = paymentRepository;
        this.auditService = auditService;
    }

    /** True when online payment is switched on and the merchant details are filled in. */
    public boolean isAvailable() {
        return props.isCheckoutReady();
    }

    public String checkoutUrl() {
        return props.checkoutUrl();
    }

    // ------------------------------------------------------------------ checkout

    /** Starts paying the policy's premium online. The amount comes from the policy, never from the browser. */
    public PayHereCheckout start(Policy policy, User actor) {
        if (!isAvailable()) {
            throw new IllegalStateException("Online payment is not available at the moment");
        }
        if (policy.getStatus() != PolicyStatus.ACTIVE && policy.getStatus() != PolicyStatus.RENEWED) {
            throw new IllegalStateException("Premiums can only be paid on a policy that is in force");
        }
        PayHereCheckout checkout = new PayHereCheckout();
        checkout.setOrderId("HIMS-" + policy.getPolicyCode() + "-" + System.currentTimeMillis());
        checkout.setPolicy(policy);
        checkout.setRequestedBy(actor);
        checkout.setAmount(policy.getPremiumAmount().setScale(2, RoundingMode.HALF_UP));
        checkout.setCurrency("LKR");
        checkout.setStatus(PayHereCheckout.PENDING);
        checkout.setCreatedAt(LocalDateTime.now());
        PayHereCheckout saved = checkoutRepository.save(checkout);
        auditService.log("PayHereCheckout", saved.getId(), "STARTED", actor,
                saved.getOrderId() + " LKR " + formatAmount(saved.getAmount()));
        return saved;
    }

    /** The form fields PayHere's checkout page expects, including the signed hash. */
    public Map<String, String> checkoutFields(PayHereCheckout checkout) {
        User holder = checkout.getPolicy().getPolicyholder();
        String fullName = holder.getFullName() == null ? "Member" : holder.getFullName().trim();
        int space = fullName.indexOf(' ');
        String orderParam = URLEncoder.encode(checkout.getOrderId(), StandardCharsets.UTF_8);
        String base = props.getAppBaseUrl().replaceAll("/+$", "");
        String notify = props.getNotifyUrl() == null || props.getNotifyUrl().isBlank()
                ? base + "/payments/payhere/notify" : props.getNotifyUrl();

        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("merchant_id", props.getMerchantId());
        fields.put("return_url", base + "/payments/payhere/return?order_id=" + orderParam);
        fields.put("cancel_url", base + "/payments/payhere/cancel?order_id=" + orderParam);
        fields.put("notify_url", notify);
        fields.put("order_id", checkout.getOrderId());
        fields.put("items", "Premium for policy " + checkout.getPolicy().getPolicyCode());
        fields.put("currency", checkout.getCurrency());
        fields.put("amount", formatAmount(checkout.getAmount()));
        fields.put("first_name", space > 0 ? fullName.substring(0, space) : fullName);
        fields.put("last_name", space > 0 ? fullName.substring(space + 1) : "-");
        fields.put("email", holder.getEmail());
        fields.put("phone", holder.getPhoneNumber());
        fields.put("address", holder.getAddress());
        fields.put("city", cityOf(holder.getAddress()));
        fields.put("country", "Sri Lanka");
        fields.put("hash", checkoutHash(checkout.getOrderId(), checkout.getAmount(), checkout.getCurrency()));
        return fields;
    }

    /** hash = UPPER(MD5(merchant_id + order_id + amount(2 dp) + currency + UPPER(MD5(merchant_secret)))) */
    public String checkoutHash(String orderId, BigDecimal amount, String currency) {
        return md5Upper(props.getMerchantId() + orderId + formatAmount(amount) + currency
                + md5Upper(props.getMerchantSecret()));
    }

    // ------------------------------------------------------------------ confirmation

    /**
     * Checks a notification from PayHere: md5sig = UPPER(MD5(merchant_id + order_id + payhere_amount +
     * payhere_currency + status_code + UPPER(MD5(merchant_secret)))), and the merchant must be ours.
     */
    public boolean verifyNotification(Map<String, String> p) {
        if (p.get("md5sig") == null || !props.getMerchantId().equals(p.get("merchant_id"))) {
            return false;
        }
        String expected = md5Upper(props.getMerchantId() + p.get("order_id") + p.get("payhere_amount")
                + p.get("payhere_currency") + p.get("status_code") + md5Upper(props.getMerchantSecret()));
        return expected.equals(p.get("md5sig"));
    }

    /** PayHere's server-to-server notification. Returns false if it was rejected. */
    public boolean handleNotification(Map<String, String> p) {
        if (!verifyNotification(p)) {
            log.warn("Rejected a PayHere notification with an invalid signature for order {}", p.get("order_id"));
            return false;
        }
        PayHereCheckout checkout = checkoutRepository.findByOrderId(p.get("order_id")).orElse(null);
        if (checkout == null) {
            log.warn("PayHere notification for unknown order {}", p.get("order_id"));
            return false;
        }
        // The signature already covers the amount; this also checks it is the amount we asked for.
        if (!formatAmount(checkout.getAmount()).equals(p.get("payhere_amount"))
                || !checkout.getCurrency().equals(p.get("payhere_currency"))) {
            log.warn("PayHere notification amount does not match order {}", checkout.getOrderId());
            return false;
        }
        int statusCode = parseInt(p.get("status_code"));
        checkout.setGatewayStatus("status_code " + statusCode);
        if (statusCode == STATUS_SUCCESS) {
            complete(checkout.getId(), p.get("payment_id"), "status_code 2");
        } else if (statusCode == STATUS_CANCELLED || statusCode == STATUS_FAILED) {
            markClosed(checkout, statusCode == STATUS_CANCELLED ? PayHereCheckout.CANCELLED : PayHereCheckout.FAILED);
        } else {
            checkoutRepository.save(checkout);
        }
        return true;
    }

    /**
     * When the member comes back from PayHere, ask PayHere whether this order was paid. This works
     * even when PayHere cannot reach the notify URL (for example on a laptop).
     */
    public PayHereCheckout confirmOnReturn(String orderId, User user) {
        PayHereCheckout checkout = findOwned(orderId, user);
        if (!PayHereCheckout.PENDING.equals(checkout.getStatus()) || !props.isRetrievalReady()) {
            return checkout;
        }
        RetrievalResult result = retrieve(orderId);
        checkout.setGatewayStatus(result.status());
        if (result.paid()) {
            return complete(checkout.getId(), result.paymentId(), "retrieval " + result.status());
        }
        return checkoutRepository.save(checkout);
    }

    /** The member pressed Cancel on PayHere's page. */
    public PayHereCheckout cancel(String orderId, User user) {
        PayHereCheckout checkout = findOwned(orderId, user);
        if (PayHereCheckout.PENDING.equals(checkout.getStatus())) {
            markClosed(checkout, PayHereCheckout.CANCELLED);
        }
        return checkout;
    }

    /**
     * Records the premium payment once. Synchronised and re-read, so a notification and a return
     * arriving together cannot record the same payment twice.
     */
    public synchronized PayHereCheckout complete(Long checkoutId, String gatewayPaymentId, String how) {
        PayHereCheckout checkout = checkoutRepository.findById(checkoutId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Checkout not found"));
        if (PayHereCheckout.COMPLETED.equals(checkout.getStatus())) {
            return checkout;
        }
        Payment payment = paymentService.makePayment(checkout.getPolicy(), checkout.getAmount(),
                PaymentMethod.CARD, false, checkout.getRequestedBy());
        payment.setGatewayReference(gatewayPaymentId);
        paymentRepository.save(payment);

        checkout.setPayment(payment);
        checkout.setGatewayPaymentId(gatewayPaymentId);
        checkout.setStatus(PayHereCheckout.COMPLETED);
        checkout.setCompletedAt(LocalDateTime.now());
        PayHereCheckout saved = checkoutRepository.save(checkout);
        auditService.log("PayHereCheckout", saved.getId(), "COMPLETED", checkout.getRequestedBy(),
                saved.getOrderId() + " PayHere payment " + gatewayPaymentId + " (" + how + ")");
        return saved;
    }

    public PayHereCheckout findOwned(String orderId, User user) {
        PayHereCheckout checkout = checkoutRepository.findByOrderId(orderId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Payment not found"));
        boolean mine = checkout.getRequestedBy().getId().equals(user.getId())
                || checkout.getPolicy().getPolicyholder().getId().equals(user.getId());
        if (!mine && user.getRole() != Role.ADMIN) {
            throw new AccessDeniedException("This online payment belongs to someone else");
        }
        return checkout;
    }

    // ------------------------------------------------------------------ Retrieval API

    record RetrievalResult(boolean paid, String paymentId, String status) {
    }

    /** OAuth client-credentials token, then GET /merchant/v1/payment/search?order_id=... */
    RetrievalResult retrieve(String orderId) {
        try {
            String basic = Base64.getEncoder().encodeToString(
                    (props.getAppId() + ":" + props.getAppSecret()).getBytes(StandardCharsets.UTF_8));
            HttpResponse<String> tokenResponse = http.send(HttpRequest.newBuilder()
                            .uri(URI.create(props.baseUrl() + "/merchant/v1/oauth/token"))
                            .timeout(Duration.ofSeconds(15))
                            .header("Authorization", "Basic " + basic)
                            .header("Content-Type", "application/x-www-form-urlencoded")
                            .POST(HttpRequest.BodyPublishers.ofString("grant_type=client_credentials"))
                            .build(),
                    HttpResponse.BodyHandlers.ofString());
            String token = json.readTree(tokenResponse.body()).path("access_token").asText("");
            if (token.isEmpty()) {
                log.warn("PayHere token request failed (HTTP {})", tokenResponse.statusCode());
                return new RetrievalResult(false, null, "token-error");
            }
            HttpResponse<String> search = http.send(HttpRequest.newBuilder()
                            .uri(URI.create(props.baseUrl() + "/merchant/v1/payment/search?order_id="
                                    + URLEncoder.encode(orderId, StandardCharsets.UTF_8)))
                            .timeout(Duration.ofSeconds(15))
                            .header("Authorization", "Bearer " + token)
                            .header("Content-Type", "application/json")
                            .GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            // Logged so the response shape can be checked against a real sandbox payment.
            log.info("PayHere retrieval for {} (HTTP {}): {}", orderId, search.statusCode(), search.body());
            return interpret(json.readTree(search.body()));
        } catch (Exception ex) {
            log.warn("PayHere retrieval for {} failed: {}", orderId, ex.getMessage());
            return new RetrievalResult(false, null, "unavailable");
        }
    }

    /** A payment is confirmed when PayHere lists it with status RECEIVED. */
    RetrievalResult interpret(JsonNode body) {
        JsonNode data = body.path("data");
        if (data.isArray()) {
            for (JsonNode item : data) {
                String status = item.path("status").asText("");
                if ("RECEIVED".equalsIgnoreCase(status)) {
                    return new RetrievalResult(true, item.path("payment_id").asText(null), status);
                }
            }
            if (!data.isEmpty()) {
                return new RetrievalResult(false, null, data.get(0).path("status").asText("unknown"));
            }
        }
        return new RetrievalResult(false, null, "not-found");
    }

    // ------------------------------------------------------------------ helpers

    private void markClosed(PayHereCheckout checkout, String status) {
        checkout.setStatus(status);
        checkout.setCompletedAt(LocalDateTime.now());
        checkoutRepository.save(checkout);
        auditService.log("PayHereCheckout", checkout.getId(), status, checkout.getRequestedBy(), checkout.getOrderId());
    }

    static String formatAmount(BigDecimal amount) {
        return amount.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    static String md5Upper(String text) {
        try {
            byte[] digest = MessageDigest.getInstance("MD5").digest(text.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().withUpperCase().formatHex(digest);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("MD5 is not available", ex);
        }
    }

    private static String cityOf(String address) {
        if (address == null || address.isBlank()) {
            return "Colombo";
        }
        String[] parts = address.split(",");
        String last = parts[parts.length - 1].trim();
        return last.isEmpty() ? "Colombo" : last;
    }

    private static int parseInt(String value) {
        try {
            return Integer.parseInt(value == null ? "" : value.trim());
        } catch (NumberFormatException ex) {
            return Integer.MIN_VALUE;
        }
    }
}

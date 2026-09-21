package com.medisure.hims.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.medisure.hims.config.PayHereProperties;
import com.medisure.hims.model.*;
import com.medisure.hims.repository.PayHereCheckoutRepository;
import com.medisure.hims.repository.PaymentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * The expected hash and md5sig values below were computed independently with Python's hashlib,
 * following PayHere's published formulas, so these tests check the Java code against them.
 */
@ExtendWith(MockitoExtension.class)
class PayHereServiceTest {

    private static final String ORDER = "HIMS-POL-2025-000002-1";

    @Mock
    private PayHereCheckoutRepository checkoutRepository;

    @Mock
    private PaymentService paymentService;

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private AuditService auditService;

    private PayHereProperties props;
    private PayHereService service;
    private User member;
    private Policy policy;

    @BeforeEach
    void setUp() {
        props = new PayHereProperties();
        props.setEnabled(true);
        props.setMerchantId("1211149");
        props.setMerchantSecret("TestSecret123");
        service = new PayHereService(props, checkoutRepository, paymentService, paymentRepository, auditService);

        member = new User();
        member.setId(8L);
        member.setFullName("Kasun Perera");
        member.setRole(Role.POLICYHOLDER);
        member.setAddress("14 Lake Drive, Rajagiriya");

        policy = new Policy();
        policy.setId(2L);
        policy.setPolicyCode("POL-2025-000002");
        policy.setPolicyholder(member);
        policy.setPremiumAmount(new BigDecimal("3280.00"));
        policy.setStatus(PolicyStatus.ACTIVE);
    }

    private Map<String, String> notification(String amount, String merchantId) {
        Map<String, String> p = new HashMap<>();
        p.put("merchant_id", merchantId);
        p.put("order_id", ORDER);
        p.put("payment_id", "320025071234");
        p.put("payhere_amount", amount);
        p.put("payhere_currency", "LKR");
        p.put("status_code", "2");
        p.put("md5sig", "BFEC7D28127317B51AB404225668390B");
        return p;
    }

    private PayHereCheckout pendingCheckout() {
        PayHereCheckout checkout = new PayHereCheckout();
        checkout.setId(5L);
        checkout.setOrderId(ORDER);
        checkout.setPolicy(policy);
        checkout.setRequestedBy(member);
        checkout.setAmount(new BigDecimal("3280.00"));
        return checkout;
    }

    @Test
    @DisplayName("The checkout hash follows PayHere's formula")
    void checkoutHashMatchesFormula() {
        assertEquals("6FF526F0582628759EBA0E8D3A56B1F0",
                service.checkoutHash(ORDER, new BigDecimal("3280"), "LKR"));
    }

    @Test
    @DisplayName("A genuine PayHere notification is accepted")
    void acceptsGenuineNotification() {
        assertTrue(service.verifyNotification(notification("3280.00", "1211149")));
    }

    @Test
    @DisplayName("A notification with a changed amount or another merchant is rejected")
    void rejectsTamperedNotification() {
        assertFalse(service.verifyNotification(notification("1.00", "1211149")));
        assertFalse(service.verifyNotification(notification("3280.00", "9999999")));
    }

    @Test
    @DisplayName("A checkout always charges the policy's premium")
    void checkoutUsesThePolicyPremium() {
        when(checkoutRepository.save(any(PayHereCheckout.class))).thenAnswer(inv -> inv.getArgument(0));

        PayHereCheckout checkout = service.start(policy, member);

        assertEquals(0, new BigDecimal("3280.00").compareTo(checkout.getAmount()));
        assertTrue(checkout.getOrderId().startsWith("HIMS-POL-2025-000002-"));
        assertEquals(PayHereCheckout.PENDING, checkout.getStatus());
    }

    @Test
    @DisplayName("Online payment is refused when PayHere is not set up, or the policy is not in force")
    void refusesWhenUnavailableOrPolicyNotInForce() {
        policy.setStatus(PolicyStatus.CANCELLED);
        assertThrows(IllegalStateException.class, () -> service.start(policy, member));

        props.setEnabled(false);
        policy.setStatus(PolicyStatus.ACTIVE);
        assertThrows(IllegalStateException.class, () -> service.start(policy, member));
        verify(checkoutRepository, never()).save(any(PayHereCheckout.class));
    }

    @Test
    @DisplayName("A successful notification records the premium payment once, even if it arrives twice")
    void recordsPaymentExactlyOnce() {
        PayHereCheckout checkout = pendingCheckout();
        when(checkoutRepository.findByOrderId(ORDER)).thenReturn(Optional.of(checkout));
        when(checkoutRepository.findById(5L)).thenReturn(Optional.of(checkout));
        when(checkoutRepository.save(any(PayHereCheckout.class))).thenAnswer(inv -> inv.getArgument(0));
        when(paymentService.makePayment(policy, checkout.getAmount(), PaymentMethod.CARD, false, member))
                .thenReturn(new Payment());

        assertTrue(service.handleNotification(notification("3280.00", "1211149")));
        assertTrue(service.handleNotification(notification("3280.00", "1211149")));

        verify(paymentService, times(1)).makePayment(policy, checkout.getAmount(), PaymentMethod.CARD, false, member);
        assertEquals(PayHereCheckout.COMPLETED, checkout.getStatus());
        assertEquals("320025071234", checkout.getPayment().getGatewayReference());
    }

    @Test
    @DisplayName("A Retrieval API result is paid only when PayHere reports RECEIVED")
    void readsRetrievalResult() throws Exception {
        ObjectMapper json = new ObjectMapper();

        PayHereService.RetrievalResult paid = service.interpret(json.readTree(
                "{\"status\":1,\"data\":[{\"payment_id\":320025071234,\"order_id\":\"" + ORDER + "\",\"status\":\"RECEIVED\"}]}"));
        assertTrue(paid.paid());
        assertEquals("320025071234", paid.paymentId());

        assertFalse(service.interpret(json.readTree("{\"status\":-1,\"data\":[]}")).paid());
    }

    @Test
    @DisplayName("Nobody can confirm another member's online payment")
    void refusesAnotherMembersCheckout() {
        when(checkoutRepository.findByOrderId(ORDER)).thenReturn(Optional.of(pendingCheckout()));
        User someoneElse = new User();
        someoneElse.setId(99L);
        someoneElse.setRole(Role.POLICYHOLDER);

        assertThrows(AccessDeniedException.class, () -> service.confirmOnReturn(ORDER, someoneElse));
    }
}

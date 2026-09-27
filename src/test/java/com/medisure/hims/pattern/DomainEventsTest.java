package com.medisure.hims.pattern;

import com.medisure.hims.model.*;
import com.medisure.hims.pattern.event.*;
import com.medisure.hims.service.AuditService;
import com.medisure.hims.service.NotificationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Observer pattern: the subject tells every registered observer, and each one reacts in its own way. */
@ExtendWith(MockitoExtension.class)
class DomainEventsTest {

    @Mock
    private AuditService auditService;

    @Mock
    private NotificationService notificationService;

    private Claim claim;
    private User claimant;
    private User officer;

    private ClaimDecidedEvent approvedEvent() {
        claimant = new User();
        claimant.setId(5L);
        claimant.setFullName("Kasun Perera");
        officer = new User();
        officer.setId(9L);
        officer.setRole(Role.CLAIMS_OFFICER);
        claim = new Claim();
        claim.setId(1L);
        claim.setClaimCode("CLM-2026-000045");
        claim.setClaimant(claimant);
        return new ClaimDecidedEvent(claim, ClaimStatus.APPROVED, "Covered under surgical benefit", officer);
    }

    @Test
    @DisplayName("Publishing an event reaches every registered observer")
    void everyObserverIsNotified() {
        List<String> calls = new ArrayList<>();
        DomainEvents subject = new DomainEvents(List.of(
                event -> calls.add("first:" + event.auditAction()),
                event -> calls.add("second:" + event.entityType())));

        subject.publish(approvedEvent());

        assertEquals(List.of("first:DECIDED:APPROVED", "second:Claim"), calls);
    }

    @Test
    @DisplayName("Observers can be attached and detached at runtime")
    void observersCanBeAttachedAndDetached() {
        List<String> calls = new ArrayList<>();
        DomainEventObserver late = event -> calls.add("late");
        DomainEvents subject = new DomainEvents(new ArrayList<>());

        subject.publish(approvedEvent());
        assertTrue(calls.isEmpty());

        subject.register(late);
        subject.register(late);                       // registering twice must not double the reaction
        subject.publish(approvedEvent());
        assertEquals(List.of("late"), calls);
        assertEquals(1, subject.observers().size());

        subject.unregister(late);
        subject.publish(approvedEvent());
        assertEquals(List.of("late"), calls);
    }

    @Test
    @DisplayName("The audit observer writes the entry the event describes")
    void auditObserverWritesTheAuditEntry() {
        ClaimDecidedEvent event = approvedEvent();

        new AuditObserver(auditService).onEvent(event);

        verify(auditService).log("Claim", 1L, "DECIDED:APPROVED", officer, "Covered under surgical benefit");
    }

    @Test
    @DisplayName("The notification observer tells the person the event names")
    void notificationObserverTellsTheRecipient() {
        ClaimDecidedEvent event = approvedEvent();

        new NotificationObserver(notificationService).onEvent(event);

        verify(notificationService).notify(claimant, "Claim CLM-2026-000045 approved",
                "Covered under surgical benefit", "/claims/1");
    }

    @Test
    @DisplayName("An event with nobody to tell is skipped by the notification observer")
    void eventWithoutARecipientIsSkipped() {
        claim = new Claim();
        claim.setId(2L);
        claim.setClaimCode("CLM-2026-000046");
        claim.setClaimant(null);
        DomainEvent event = new ClaimDecidedEvent(claim, ClaimStatus.REJECTED, null, officer);

        new NotificationObserver(notificationService).onEvent(event);

        verifyNoInteractions(notificationService);
    }

    @Test
    @DisplayName("A voided claim event carries the original claim in both the audit entry and the message")
    void voidedEventNamesTheOriginalClaim() {
        approvedEvent();
        Claim original = new Claim();
        original.setId(3L);
        original.setClaimCode("CLM-2026-000002");
        ClaimVoidedEvent event = new ClaimVoidedEvent(claim, original, "Same bill twice", officer);

        assertEquals("VOIDED", event.auditAction());
        assertTrue(event.auditNotes().contains("CLM-2026-000002"));
        assertTrue(event.notificationTitle().contains("duplicate"));
        assertTrue(event.notificationBody().contains("CLM-2026-000002"));
        assertEquals("/claims/1", event.link());
    }

    @Test
    @DisplayName("A decision with no notes still gives the member something to read")
    void decisionWithoutNotesHasADefaultBody() {
        ClaimDecidedEvent event = new ClaimDecidedEvent(approvedEvent().claim(), ClaimStatus.REJECTED, "  ", officer);

        assertEquals("Open the claim for details.", event.notificationBody());
        assertTrue(event.notificationTitle().contains("rejected"));
    }
}

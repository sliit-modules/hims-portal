package com.medisure.hims.service;

import com.medisure.hims.model.*;
import com.medisure.hims.repository.SupportTicketRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TicketServiceTest {

    @Mock
    private SupportTicketRepository ticketRepository;

    @Mock
    private AuditService auditService;

    @InjectMocks
    private TicketService ticketService;

    private SupportTicket testTicket;
    private User member;
    private User testExecutive;

    @BeforeEach
    void setUp() {
        member = new User();
        member.setId(40L);
        member.setNic("199009098765");
        member.setFullName("Kasun Perera");
        member.setRole(Role.POLICYHOLDER);

        testTicket = new SupportTicket();
        testTicket.setId(1L);
        testTicket.setRaisedBy(member);
        testTicket.setSubject("Question about dental coverage");
        testTicket.setMessage("Is dental treatment covered under my plan?");
        testTicket.setStatus(TicketStatus.OPEN);

        testExecutive = new User();
        testExecutive.setId(60L);
        testExecutive.setNic("199606056789");
        testExecutive.setFullName("Kavisekara K.M.H.N.");
        testExecutive.setRole(Role.CRE);
    }

    @Test
    @DisplayName("A new inquiry is logged as OPEN against the member who raised it")
    void logsNewInquiryAsOpen() {
        when(ticketRepository.save(any(SupportTicket.class))).thenAnswer(inv -> inv.getArgument(0));

        SupportTicket created = ticketService.create(member, null, null,
                "Premium payment did not reflect", "I paid by card but it still shows as due.");

        assertEquals(TicketStatus.OPEN, created.getStatus());
        assertEquals(member, created.getRaisedBy());
        assertNotNull(created.getCreatedAt());
        verify(auditService).log(eq("SupportTicket"), any(), eq("LOGGED"), eq(member), any());
    }

    @Test
    @DisplayName("Customer Relations can reply and move a ticket to in progress")
    void respondsAndMovesTicketInProgress() {
        when(ticketRepository.findById(1L)).thenReturn(Optional.of(testTicket));
        when(ticketRepository.save(any(SupportTicket.class))).thenReturn(testTicket);

        SupportTicket responded = ticketService.respond(1L,
                "Dental treatment is covered up to your annual limit.",
                TicketStatus.IN_PROGRESS, testExecutive);

        assertEquals(TicketStatus.IN_PROGRESS, responded.getStatus());
        assertEquals("Dental treatment is covered up to your annual limit.", responded.getResponse());
        assertNotNull(responded.getUpdatedAt());
        verify(auditService).log(eq("SupportTicket"), eq(1L),
                eq("RESPONDED:IN_PROGRESS"), eq(testExecutive), any());
    }

    @Test
    @DisplayName("Closing a ticket marks it CLOSED and is audited")
    void closesTicket() {
        when(ticketRepository.findById(1L)).thenReturn(Optional.of(testTicket));

        ticketService.close(1L, testExecutive);

        assertEquals(TicketStatus.CLOSED, testTicket.getStatus());
        verify(ticketRepository).save(testTicket);
        verify(auditService).log(eq("SupportTicket"), eq(1L), eq("CLOSED"), eq(testExecutive), any());
    }

    @Test
    @DisplayName("A member cannot answer or close their own ticket")
    void refusesResponseFromNonSupportRole() {
        assertThrows(AccessDeniedException.class,
                () -> ticketService.respond(1L, "Closing this myself", TicketStatus.CLOSED, member));
        assertThrows(AccessDeniedException.class, () -> ticketService.close(1L, member));

        verify(ticketRepository, never()).save(any(SupportTicket.class));
    }

    @Test
    @DisplayName("A member may only view their own tickets")
    void restrictsTicketVisibilityToOwner() {
        User otherMember = new User();
        otherMember.setId(99L);
        otherMember.setFullName("Someone Else");
        otherMember.setRole(Role.POLICYHOLDER);

        assertThrows(AccessDeniedException.class, () -> ticketService.assertVisible(testTicket, otherMember));
        assertDoesNotThrow(() -> ticketService.assertVisible(testTicket, member));
        assertDoesNotThrow(() -> ticketService.assertVisible(testTicket, testExecutive));
    }
}

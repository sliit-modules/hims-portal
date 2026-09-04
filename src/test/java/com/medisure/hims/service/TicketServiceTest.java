package com.medisure.hims.service;

import com.medisure.hims.model.*;
import com.medisure.hims.repository.SupportTicketRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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
    private User testExecutive;

    @BeforeEach
    void setUp() {
        testTicket = new SupportTicket();
        testTicket.setId(1L);
        testTicket.setTicketNumber("TCK-2026-000001");
        testTicket.setSubject("Question about dental coverage");
        testTicket.setStatus(TicketStatus.OPEN);

        testExecutive = new User();
        testExecutive.setId(60L);
        testExecutive.setUsername("cre_executive");
    }

    @Test
    void testReplyTicket() {
        when(ticketRepository.findById(1L)).thenReturn(Optional.of(testTicket));
        when(ticketRepository.save(any(SupportTicket.class))).thenReturn(testTicket);

        ticketService.reply(1L, "Dental coverage is included in Silver and Gold plans.", testExecutive);
        assertEquals(TicketStatus.IN_PROGRESS, testTicket.getStatus());
        verify(auditService).log(eq("SupportTicket"), eq(1L), eq("REPLIED"), eq(testExecutive), any());
    }

    @Test
    void testCloseTicket() {
        when(ticketRepository.findById(1L)).thenReturn(Optional.of(testTicket));
        when(ticketRepository.save(any(SupportTicket.class))).thenReturn(testTicket);

        ticketService.close(1L, "Customer confirmed resolution", testExecutive);
        assertEquals(TicketStatus.CLOSED, testTicket.getStatus());
        verify(auditService).log(eq("SupportTicket"), eq(1L), eq("CLOSED"), eq(testExecutive), any());
    }
}

package com.medisure.hims.service;

import com.medisure.hims.model.AuditLog;
import com.medisure.hims.model.Role;
import com.medisure.hims.model.User;
import com.medisure.hims.repository.AuditLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuditServiceTest {

    @Mock
    private AuditLogRepository auditLogRepository;

    @InjectMocks
    private AuditService auditService;

    private User member;
    private User claimsOfficer;

    @BeforeEach
    void setUp() {
        member = new User();
        member.setId(7L);
        member.setFullName("Kasun Perera");
        member.setRole(Role.POLICYHOLDER);

        claimsOfficer = new User();
        claimsOfficer.setId(3L);
        claimsOfficer.setFullName("Gunasinghe N.M.");
        claimsOfficer.setRole(Role.CLAIMS_OFFICER);
    }

    @Test
    @DisplayName("Staff opening a member's claim is recorded against that member")
    void recordsAccessAgainstTheDataSubject() {
        auditService.logAccess(member, claimsOfficer, AuditService.VIEWED_CLAIM, "Claim CLM-2026-000042");

        verify(auditLogRepository).save(argThat(log ->
                "User".equals(log.getEntityType())
                        && Long.valueOf(7L).equals(log.getEntityId())
                        && AuditService.VIEWED_CLAIM.equals(log.getAction())
                        && log.getPerformedBy() == claimsOfficer
                        && "Claim CLM-2026-000042".equals(log.getNotes())));
    }

    @Test
    @DisplayName("Reading your own records is not logged")
    void skipsOwnData() {
        auditService.logAccess(member, member, AuditService.VIEWED_PROFILE, "Member record");

        verify(auditLogRepository, never()).save(any(AuditLog.class));
    }

    @Test
    @DisplayName("The changes view leaves out record views, and the access view shows only them")
    void separatesChangesFromViews() {
        when(auditLogRepository.findByActionNotLikeOrderByTimestampDesc("VIEWED_%")).thenReturn(List.of());
        when(auditLogRepository.findByActionLikeOrderByTimestampDesc("VIEWED_%")).thenReturn(List.of());

        auditService.changes();
        auditService.recordAccess();

        verify(auditLogRepository).findByActionNotLikeOrderByTimestampDesc("VIEWED_%");
        verify(auditLogRepository).findByActionLikeOrderByTimestampDesc("VIEWED_%");
    }

    @Test
    @DisplayName("A member's access history is looked up by their own id")
    void looksUpAccessHistoryForTheMember() {
        AuditLog entry = new AuditLog("User", 7L, AuditService.VIEWED_PROFILE, claimsOfficer, "Member record");
        when(auditLogRepository.findTop20ByEntityTypeAndEntityIdAndActionLikeOrderByTimestampDesc("User", 7L, "VIEWED_%"))
                .thenReturn(List.of(entry));

        assertEquals(List.of(entry), auditService.accessHistoryFor(member));
    }
}

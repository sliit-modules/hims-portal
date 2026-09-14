package com.medisure.hims.service;

import com.medisure.hims.repository.ClaimDocumentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class ClaimDocumentServiceTest {

    @Mock
    private ClaimDocumentRepository documentRepository;

    @Mock
    private AuditService auditService;

    @TempDir
    Path uploadDir;

    private ClaimDocumentService documentService;

    @BeforeEach
    void setUp() {
        documentService = new ClaimDocumentService(documentRepository, auditService, uploadDir.toString());
    }

    @Test
    @DisplayName("PDF and image attachments pass validation")
    void acceptsPdfAndImages() {
        MultipartFile[] files = {
                new MockMultipartFile("documents", "hospital-bill.pdf", "application/pdf", new byte[]{1, 2, 3}),
                new MockMultipartFile("documents", "x-ray.png", "image/png", new byte[]{1})
        };

        assertDoesNotThrow(() -> documentService.validateAll(files));
    }

    @Test
    @DisplayName("A file type outside the allowlist is rejected")
    void rejectsUnsupportedType() {
        MultipartFile[] files = {
                new MockMultipartFile("documents", "setup.exe", "application/octet-stream", new byte[]{1})
        };

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> documentService.validateAll(files));
        assertTrue(ex.getMessage().contains("Only PDF, JPG and PNG"));
    }

    @Test
    @DisplayName("A file over the 5 MB limit is rejected")
    void rejectsOversizedFile() {
        MultipartFile[] files = {
                new MockMultipartFile("documents", "scan.pdf", "application/pdf", new byte[5 * 1024 * 1024 + 1])
        };

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> documentService.validateAll(files));
        assertTrue(ex.getMessage().contains("5 MB"));
    }

    @Test
    @DisplayName("An empty file slot (no file chosen) is ignored")
    void ignoresEmptyFileSlot() {
        MultipartFile[] files = {
                new MockMultipartFile("documents", "", "application/octet-stream", new byte[0])
        };

        assertDoesNotThrow(() -> documentService.validateAll(files));
    }
}

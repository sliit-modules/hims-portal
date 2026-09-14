package com.medisure.hims.service;

import com.medisure.hims.model.Claim;
import com.medisure.hims.model.ClaimDocument;
import com.medisure.hims.model.User;
import com.medisure.hims.repository.ClaimDocumentRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import static org.springframework.http.HttpStatus.NOT_FOUND;

/**
 * Stores claim supporting documents on disk and keeps their metadata in the database.
 *
 * Uploads are never trusted: the client's filename is kept only for display, the file is written
 * under a generated UUID name, the type must be on the allowlist, and the resolved path is
 * checked to be inside the upload root so a crafted name cannot escape it.
 */
@Service
public class ClaimDocumentService {

    private static final Set<String> ALLOWED_TYPES = Set.of(
            "application/pdf", "image/jpeg", "image/png", "image/jpg");
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("pdf", "jpg", "jpeg", "png");
    private static final long MAX_BYTES = 5L * 1024 * 1024;

    private final ClaimDocumentRepository documentRepository;
    private final AuditService auditService;
    private final Path uploadRoot;

    public ClaimDocumentService(ClaimDocumentRepository documentRepository, AuditService auditService,
                                 @Value("${app.upload-dir:uploads}") String uploadDir) {
        this.documentRepository = documentRepository;
        this.auditService = auditService;
        this.uploadRoot = Paths.get(uploadDir).toAbsolutePath().normalize();
        try {
            Files.createDirectories(this.uploadRoot);
        } catch (IOException ex) {
            throw new UncheckedIOException("Could not create the upload directory " + this.uploadRoot, ex);
        }
    }

    public List<ClaimDocument> findForClaim(Claim claim) {
        return documentRepository.findByClaimOrderByUploadedAtAsc(claim);
    }

    /** Claim id -> number of attachments, for list views. */
    public java.util.Map<Long, Long> documentCounts() {
        java.util.Map<Long, Long> counts = new java.util.HashMap<>();
        for (Object[] row : documentRepository.countGroupedByClaim()) {
            counts.put((Long) row[0], (Long) row[1]);
        }
        return counts;
    }

    public ClaimDocument findById(Long id) {
        return documentRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Document not found"));
    }

    /** Saves every non-empty file against the claim. Returns how many were stored. */
    public int storeAll(Claim claim, MultipartFile[] files, User actor) {
        if (files == null) {
            return 0;
        }
        int stored = 0;
        for (MultipartFile file : files) {
            if (file != null && !file.isEmpty()) {
                store(claim, file, actor);
                stored++;
            }
        }
        return stored;
    }

    /**
     * Checks every non-empty file up front, so a caller can reject a bad attachment before saving
     * anything and a claim is never left half-filed.
     */
    public void validateAll(MultipartFile[] files) {
        if (files == null) {
            return;
        }
        for (MultipartFile file : files) {
            if (file != null && !file.isEmpty()) {
                validate(file);
            }
        }
    }

    public ClaimDocument store(Claim claim, MultipartFile file, User actor) {
        validate(file);
        String extension = extensionOf(file.getOriginalFilename());

        String storedName = UUID.randomUUID() + "." + extension;
        Path claimDir = uploadRoot.resolve("claims").resolve(String.valueOf(claim.getId())).normalize();
        if (!claimDir.startsWith(uploadRoot)) {
            throw new IllegalStateException("Invalid upload location");
        }

        try {
            Files.createDirectories(claimDir);
            Files.copy(file.getInputStream(), claimDir.resolve(storedName));
        } catch (IOException ex) {
            throw new UncheckedIOException("Could not store " + file.getOriginalFilename(), ex);
        }

        ClaimDocument document = new ClaimDocument();
        document.setClaim(claim);
        document.setOriginalFileName(sanitiseDisplayName(file.getOriginalFilename()));
        document.setStoredFileName(storedName);
        document.setContentType(file.getContentType());
        document.setSizeBytes(file.getSize());
        document.setUploadedBy(actor);
        document.setUploadedAt(LocalDateTime.now());

        ClaimDocument saved = documentRepository.save(document);
        auditService.log("ClaimDocument", saved.getId(), "UPLOADED", actor,
                saved.getOriginalFileName() + " for " + claim.getClaimCode());
        return saved;
    }

    /** Resolves the document's bytes, guarding against any path escaping the upload root. */
    public Resource loadAsResource(ClaimDocument document) {
        Path file = uploadRoot.resolve("claims")
                .resolve(String.valueOf(document.getClaim().getId()))
                .resolve(document.getStoredFileName())
                .normalize();
        if (!file.startsWith(uploadRoot) || !Files.exists(file)) {
            throw new ResponseStatusException(NOT_FOUND, "The stored file is no longer available");
        }
        return new FileSystemResource(file);
    }

    public void delete(ClaimDocument document, User actor) {
        Path file = uploadRoot.resolve("claims")
                .resolve(String.valueOf(document.getClaim().getId()))
                .resolve(document.getStoredFileName())
                .normalize();
        if (file.startsWith(uploadRoot)) {
            try {
                Files.deleteIfExists(file);
            } catch (IOException ex) {
                throw new UncheckedIOException("Could not delete " + document.getOriginalFileName(), ex);
            }
        }
        auditService.log("ClaimDocument", document.getId(), "DELETED", actor, document.getOriginalFileName());
        documentRepository.delete(document);
    }

    private void validate(MultipartFile file) {
        if (file.getSize() > MAX_BYTES) {
            throw new IllegalStateException(
                    "%s is larger than the 5 MB limit".formatted(file.getOriginalFilename()));
        }
        String extension = extensionOf(file.getOriginalFilename());
        if (!ALLOWED_EXTENSIONS.contains(extension)
                || file.getContentType() == null
                || !ALLOWED_TYPES.contains(file.getContentType().toLowerCase(Locale.ROOT))) {
            throw new IllegalStateException("Only PDF, JPG and PNG files can be attached to a claim");
        }
    }

    private String extensionOf(String filename) {
        if (filename == null) {
            return "";
        }
        int dot = filename.lastIndexOf('.');
        return dot < 0 ? "" : filename.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    /** Strips any directory parts a browser may have sent so the display name is just a name. */
    private String sanitiseDisplayName(String filename) {
        if (filename == null || filename.isBlank()) {
            return "document";
        }
        String name = Paths.get(filename).getFileName().toString();
        return name.length() > 180 ? name.substring(name.length() - 180) : name;
    }
}

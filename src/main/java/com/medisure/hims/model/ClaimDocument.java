package com.medisure.hims.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Metadata for a file a member uploaded in support of a claim (bill, discharge summary,
 * diagnostic report). The bytes live on disk under the configured upload directory; only the
 * metadata is stored here so listing claims never loads file content.
 */
@Entity
@Table(name = "claim_documents")
@Getter
@Setter
@NoArgsConstructor
public class ClaimDocument {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "claim_id", nullable = false)
    private Claim claim;

    /** The name the member's file had when they uploaded it — shown in the UI. */
    @Column(nullable = false)
    private String originalFileName;

    /** Randomised name actually used on disk, so uploads can never collide or traverse paths. */
    @Column(nullable = false)
    private String storedFileName;

    @Column(nullable = false)
    private String contentType;

    @Column(nullable = false)
    private long sizeBytes;

    @ManyToOne
    @JoinColumn(name = "uploaded_by_id")
    private User uploadedBy;

    @Column(nullable = false)
    private LocalDateTime uploadedAt = LocalDateTime.now();

    /** Human-friendly size for the UI, e.g. "1.4 MB". */
    public String getReadableSize() {
        if (sizeBytes < 1024) {
            return sizeBytes + " B";
        }
        if (sizeBytes < 1024 * 1024) {
            return "%.0f KB".formatted(sizeBytes / 1024.0);
        }
        return "%.1f MB".formatted(sizeBytes / (1024.0 * 1024.0));
    }

    public boolean isImage() {
        return contentType != null && contentType.startsWith("image/");
    }

    public boolean isPdf() {
        return "application/pdf".equals(contentType);
    }
}

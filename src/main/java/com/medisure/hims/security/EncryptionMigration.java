package com.medisure.hims.security;

import com.medisure.hims.model.ClaimDocument;
import com.medisure.hims.repository.ClaimDocumentRepository;
import com.medisure.hims.service.AuditService;
import com.medisure.hims.service.ClaimDocumentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Map;

/**
 * Encrypts medical information that was saved before encryption was switched on: the text columns
 * and the claim documents on disk. It runs at every start but only touches values that are still
 * plain, so after the first run it does nothing. It runs last, after the schema is widened and the
 * demo data is seeded.
 */
@Component
@Order(Ordered.LOWEST_PRECEDENCE)
public class EncryptionMigration implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(EncryptionMigration.class);

    /** table -> medical text columns, matching the fields marked with EncryptedStringConverter. */
    static final Map<String, List<String>> COLUMNS = Map.of(
            "users", List.of("allergies", "chronic_conditions", "current_medications"),
            "dependents", List.of("allergies", "chronic_conditions", "current_medications"),
            "underwriting_applications", List.of("conditions_notes"),
            "claims", List.of("diagnosis_summary"));

    private final JdbcTemplate jdbc;
    private final FieldCipher cipher;
    private final ClaimDocumentRepository documents;
    private final ClaimDocumentService documentService;
    private final AuditService auditService;

    public EncryptionMigration(JdbcTemplate jdbc, FieldCipher cipher, ClaimDocumentRepository documents,
                               ClaimDocumentService documentService, AuditService auditService) {
        this.jdbc = jdbc;
        this.cipher = cipher;
        this.documents = documents;
        this.documentService = documentService;
        this.auditService = auditService;
    }

    @Override
    public void run(ApplicationArguments args) {
        int values = encryptColumns();
        int files = encryptFiles();
        if (values > 0 || files > 0) {
            String summary = values + " medical values and " + files + " claim documents encrypted at rest";
            log.info(summary);
            auditService.log("System", 0L, "ENCRYPTED_AT_REST", null, summary);
        }
    }

    int encryptColumns() {
        int count = 0;
        for (Map.Entry<String, List<String>> table : COLUMNS.entrySet()) {
            for (String column : table.getValue()) {
                List<Map<String, Object>> rows = jdbc.queryForList(
                        "SELECT id, " + column + " AS v FROM " + table.getKey()
                                + " WHERE " + column + " IS NOT NULL AND " + column + " NOT LIKE ?",
                        FieldCipher.TEXT_PREFIX + "%");
                for (Map<String, Object> row : rows) {
                    jdbc.update("UPDATE " + table.getKey() + " SET " + column + " = ? WHERE id = ?",
                            cipher.encrypt((String) row.get("v")), row.get("id"));
                    count++;
                }
            }
        }
        return count;
    }

    int encryptFiles() {
        int count = 0;
        for (ClaimDocument document : documents.findAll()) {
            Path file = documentService.pathOf(document);
            try {
                if (!Files.exists(file)) {
                    continue;
                }
                byte[] content = Files.readAllBytes(file);
                if (cipher.isEncrypted(content)) {
                    continue;
                }
                // Write next to the file first, then swap, so a crash never leaves a half-written file.
                Path temp = file.resolveSibling(file.getFileName() + ".enc-tmp");
                Files.write(temp, cipher.encryptFile(content));
                Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                count++;
            } catch (IOException ex) {
                log.warn("Could not encrypt claim document {}: {}", document.getId(), ex.getMessage());
            }
        }
        return count;
    }
}

package com.medisure.hims.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Hibernate's "ddl-auto: update" adds new columns but never changes an existing column. When an
 * enum gains a value, or a column must hold longer (encrypted) text, this changes the column once,
 * so every team member's existing database keeps working without dropping it.
 */
@Component
@Order(0)
public class SchemaUpgrade implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SchemaUpgrade.class);

    private final JdbcTemplate jdbc;

    public SchemaUpgrade(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void run(ApplicationArguments args) {
        addEnumValue("underwriting_applications", "decision", "WITHDRAWN",
                "ENUM('APPROVED','PENDING','REJECTED','WITHDRAWN') NOT NULL");
        addEnumValue("claims", "status", "VOIDED",
                "ENUM('APPROVED','REJECTED','SUBMITTED','VOIDED','WITHDRAWN') NOT NULL");
        // Encrypted medical text is about a third longer than the plain text, so these columns are
        // widened before EncryptionMigration fills them with ciphertext.
        widen("users", "allergies", 1000, true);
        widen("users", "chronic_conditions", 2000, true);
        widen("users", "current_medications", 1000, true);
        widen("dependents", "allergies", 1000, true);
        widen("dependents", "chronic_conditions", 2000, true);
        widen("dependents", "current_medications", 1000, true);
        widen("underwriting_applications", "conditions_notes", 2000, true);
        widen("claims", "diagnosis_summary", 2000, false);
    }

    private void widen(String table, String column, int length, boolean nullable) {
        try {
            List<Long> lengths = jdbc.queryForList(
                    "SELECT CHARACTER_MAXIMUM_LENGTH FROM information_schema.COLUMNS"
                            + " WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND COLUMN_NAME = ?",
                    Long.class, table, column);
            if (lengths.isEmpty() || lengths.get(0) == null || lengths.get(0) >= length) {
                return;   // new database, or already wide enough
            }
            jdbc.execute("ALTER TABLE " + table + " MODIFY " + column + " VARCHAR(" + length + ")"
                    + (nullable ? " NULL" : " NOT NULL"));
            log.info("Schema upgrade: widened {}.{} to {} characters", table, column, length);
        } catch (DataAccessException ex) {
            log.warn("Schema upgrade for {}.{} skipped: {}", table, column, ex.getMessage());
        }
    }

    private void addEnumValue(String table, String column, String value, String newDefinition) {
        try {
            List<String> types = jdbc.queryForList(
                    "SELECT COLUMN_TYPE FROM information_schema.COLUMNS"
                            + " WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND COLUMN_NAME = ?",
                    String.class, table, column);
            if (types.isEmpty() || !types.get(0).startsWith("enum(") || types.get(0).contains("'" + value + "'")) {
                return;   // not an enum column, or already up to date
            }
            jdbc.execute("ALTER TABLE " + table + " MODIFY " + column + " " + newDefinition);
            log.info("Schema upgrade: added {} to {}.{}", value, table, column);
        } catch (DataAccessException ex) {
            log.warn("Schema upgrade for {}.{} skipped: {}", table, column, ex.getMessage());
        }
    }
}

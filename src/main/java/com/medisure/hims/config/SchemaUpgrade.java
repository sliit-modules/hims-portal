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
 * Hibernate's "ddl-auto: update" adds new columns but never changes an existing MySQL ENUM column.
 * When an enum gains a value, this adds it to the column once, so every team member's existing
 * database keeps working without dropping it.
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

package com.securebank.banking;

import java.util.Map;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.MigrateResult;

public final class DatabaseMigrationRunner {

    private DatabaseMigrationRunner() {
    }

    public static MigrateResult migrate(Map<String, String> environment) {
        String url = required(environment, "BANKING_DB_URL");
        String password = required(environment, "BANKING_MIGRATOR_PASSWORD");

        if (!url.startsWith("jdbc:postgresql:")) {
            throw new IllegalArgumentException(
                    "BANKING_DB_URL must be a PostgreSQL JDBC URL");
        }

        return Flyway.configure()
                .dataSource(url, "banking_migrator", password)
                .locations("classpath:db/migration")
                .defaultSchema("banking")
                .schemas("banking")
                .createSchemas(false)
                .cleanDisabled(true)
                .baselineOnMigrate(false)
                .validateOnMigrate(true)
                .load()
                .migrate();
    }

    private static String required(
            Map<String, String> environment, String name) {
        String value = environment.get(name);

        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    "Missing required environment variable: " + name);
        }

        return value;
    }
}

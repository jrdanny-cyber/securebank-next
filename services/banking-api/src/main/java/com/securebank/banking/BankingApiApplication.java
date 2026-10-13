package com.securebank.banking;

import java.util.Arrays;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class BankingApiApplication {

    public static void main(String[] args) {
        if (Arrays.asList(args).contains("--migrate-only")) {
            if (args.length != 1) {
                throw new IllegalArgumentException(
                        "--migrate-only must be used without other arguments");
            }

            var result = DatabaseMigrationRunner.migrate(System.getenv());
            System.out.printf(
                    "Database migration completed: %d migration(s) applied.%n",
                    result.migrationsExecuted);
            return;
        }

        SpringApplication.run(BankingApiApplication.class, args);
    }
}

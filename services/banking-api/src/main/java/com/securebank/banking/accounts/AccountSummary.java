package com.securebank.banking.accounts;

import java.util.UUID;

public record AccountSummary(
        UUID id,
        String accountReference,
        String accountName,
        String currency,
        String status
) {
}

package com.securebank.banking.accounts;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record AccountHistoryPage(
        List<Entry> items,
        int page,
        int size,
        boolean hasNext
) {
    public record Entry(
            UUID transactionId,
            String direction,
            String amount,
            String currency,
            String description,
            Instant recordedAt
    ) {
    }
}

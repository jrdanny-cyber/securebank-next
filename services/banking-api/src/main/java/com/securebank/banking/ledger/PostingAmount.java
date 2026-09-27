package com.securebank.banking.ledger;

import java.math.BigDecimal;
import java.math.RoundingMode;

public record PostingAmount(BigDecimal amount, String currency) {

    public PostingAmount {
        if (amount == null) {
            throw new IllegalArgumentException("Amount is required");
        }

        if (!"USD".equals(currency)) {
            throw new IllegalArgumentException(
                    "Only USD postings are currently supported");
        }

        if (amount.signum() <= 0) {
            throw new IllegalArgumentException(
                    "Posting amount must be greater than zero");
        }

        try {
            amount = amount.setScale(2, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException(
                    "USD amount must be representable in whole cents",
                    exception);
        }

        if (amount.precision() > 19) {
            throw new IllegalArgumentException(
                    "Amount exceeds the supported storage precision");
        }
    }
}

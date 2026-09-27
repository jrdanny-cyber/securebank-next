package com.securebank.banking.ledger;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PostingAmountTest {

    @Test
    void normalizesWholeDollarsToTwoDecimalPlaces() {
        PostingAmount posting =
                new PostingAmount(new BigDecimal("25"), "USD");

        assertEquals(new BigDecimal("25.00"), posting.amount());
        assertEquals("USD", posting.currency());
    }

    @Test
    void removesExtraZeroesWithoutChangingTheValue() {
        assertEquals(
                new PostingAmount(new BigDecimal("25.50"), "USD"),
                new PostingAmount(new BigDecimal("25.500"), "USD"));
    }

    @Test
    void rejectsFractionalCents() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new PostingAmount(new BigDecimal("25.501"), "USD"));
    }

    @Test
    void rejectsZeroAndNegativeAmounts() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new PostingAmount(BigDecimal.ZERO, "USD"));

        assertThrows(
                IllegalArgumentException.class,
                () -> new PostingAmount(new BigDecimal("-1.00"), "USD"));
    }

    @Test
    void rejectsUnsupportedOrMissingCurrency() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new PostingAmount(new BigDecimal("1.00"), "EUR"));

        assertThrows(
                IllegalArgumentException.class,
                () -> new PostingAmount(new BigDecimal("1.00"), null));
    }

    @Test
    void rejectsMissingAmount() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new PostingAmount(null, "USD"));
    }

    @Test
    void enforcesStorageBoundary() {
        PostingAmount maximum = new PostingAmount(
                new BigDecimal("99999999999999999.99"), "USD");

        assertEquals(
                new BigDecimal("99999999999999999.99"),
                maximum.amount());

        assertThrows(
                IllegalArgumentException.class,
                () -> new PostingAmount(
                        new BigDecimal("100000000000000000.00"), "USD"));
    }
}

package com.securebank.banking.transfers;

import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record TransferRequest(
        @NotNull UUID sourceAccountId,
        @NotNull UUID destinationAccountId,

        @NotBlank
        @Pattern(regexp = "[0-9]{1,17}(\\.[0-9]{1,2})?")
        String amount,

        @NotBlank
        @Pattern(regexp = "USD")
        String currency,

        @NotBlank
        @Size(max = 255)
        String description
) {
}

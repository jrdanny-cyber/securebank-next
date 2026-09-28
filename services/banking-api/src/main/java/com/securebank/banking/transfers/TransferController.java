package com.securebank.banking.transfers;

import java.math.BigDecimal;
import java.util.UUID;

import com.securebank.banking.ledger.PostingAmount;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/transfers")
public class TransferController {

    private final TransferService transfers;

    public TransferController(TransferService transfers) {
        this.transfers = transfers;
    }

    @PostMapping
    public TransferService.Result transfer(
            @AuthenticationPrincipal Jwt jwt,
            @RequestHeader("Idempotency-Key") UUID idempotencyKey,
            @Valid @RequestBody TransferRequest request
    ) {
        if (jwt.getSubject() == null || jwt.getSubject().isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }

        return transfers.transfer(
                jwt.getSubject(),
                idempotencyKey,
                request.sourceAccountId(),
                request.destinationAccountId(),
                new PostingAmount(
                        new BigDecimal(request.amount()),
                        request.currency()),
                request.description());
    }
}

package com.securebank.banking.accounts;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/accounts")
public class AccountHistoryController {

    private final AccountHistoryRepository history;

    public AccountHistoryController(AccountHistoryRepository history) {
        this.history = history;
    }

    @GetMapping("/{accountId}/transactions")
    public AccountHistoryPage transactions(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable("accountId") UUID accountId,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size
    ) {
        if (jwt.getSubject() == null || jwt.getSubject().isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }

        return history.find(jwt.getSubject(), accountId, page, size);
    }
}

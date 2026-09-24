package com.securebank.banking.accounts;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/accounts")
public class AccountController {

    private final AccountQueryRepository accountQueries;

    public AccountController(AccountQueryRepository accountQueries) {
        this.accountQueries = accountQueries;
    }

    @GetMapping
    public List<AccountSummary> accounts(@AuthenticationPrincipal Jwt jwt) {
        String subject = jwt.getSubject();

        if (subject == null || subject.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }

        return accountQueries.findByIdentitySubject(subject);
    }
}

package com.wallet.web;

import com.wallet.dto.Dtos.*;
import com.wallet.exception.ApiException;
import com.wallet.service.WalletService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/wallet")
@RequiredArgsConstructor
public class WalletController {
    private final WalletService service;

    @GetMapping
    public WalletResponse wallet(@AuthenticationPrincipal Jwt jwt) {
        return service.getWallet(uid(jwt));
    }

    @GetMapping("/transactions")
    public PageResponse<TransactionResponse> history(@AuthenticationPrincipal Jwt jwt,
                                                     @RequestParam(defaultValue = "0") int page,
                                                     @RequestParam(defaultValue = "10") int size) {
        return service.history(uid(jwt), page, size);
    }

    @PostMapping("/deposit")
    @ResponseStatus(HttpStatus.CREATED)
    public TransactionResponse deposit(@AuthenticationPrincipal Jwt jwt,
                                       @RequestHeader("Idempotency-Key") String key,
                                       @Valid @RequestBody AmountRequest req) {
        return service.deposit(uid(jwt), req.amount(), checkKey(key));
    }

    @PostMapping("/withdraw")
    @ResponseStatus(HttpStatus.CREATED)
    public TransactionResponse withdraw(@AuthenticationPrincipal Jwt jwt,
                                        @RequestHeader("Idempotency-Key") String key,
                                        @Valid @RequestBody AmountRequest req) {
        return service.withdraw(uid(jwt), req.amount(), checkKey(key));
    }

    @PostMapping("/transfer")
    @ResponseStatus(HttpStatus.CREATED)
    public TransactionResponse transfer(@AuthenticationPrincipal Jwt jwt,
                                        @RequestHeader("Idempotency-Key") String key,
                                        @Valid @RequestBody TransferRequest req) {
        return service.transfer(uid(jwt), req.recipientEmail(), req.amount(), req.description(), checkKey(key));
    }

    private Long uid(Jwt jwt) { return Long.valueOf(jwt.getSubject()); }

    private String checkKey(String key) {
        if (key.isBlank() || key.length() > 64) throw new ApiException(HttpStatus.BAD_REQUEST, "Idempotency-Key must be 1-64 characters");
        return key;
    }
}

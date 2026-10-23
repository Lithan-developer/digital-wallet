package com.wallet.dto;

import com.wallet.domain.TxType;
import com.wallet.domain.WalletTransaction;
import jakarta.validation.constraints.*;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public final class Dtos {
    private Dtos() {}

    public record RegisterRequest(@NotBlank @Email String email,
                                  @NotBlank @Size(min = 8, max = 72) String password,
                                  @NotBlank @Size(max = 100) String fullName) {}

    public record LoginRequest(@NotBlank @Email String email, @NotBlank String password) {}

    public record AuthResponse(String token, String tokenType, long expiresInSeconds, String email, String fullName) {}

    public record AmountRequest(@NotNull @DecimalMin("0.01") @Digits(integer = 12, fraction = 2) BigDecimal amount) {}

    public record TransferRequest(@NotBlank @Email String recipientEmail,
                                  @NotNull @DecimalMin("0.01") @Digits(integer = 12, fraction = 2) BigDecimal amount,
                                  @Size(max = 140) String description) {}

    public record WalletResponse(Long walletId, BigDecimal balance, String currency, String ownerName, String ownerEmail)
            implements Serializable {}

    public record TransactionResponse(Long id, String reference, TxType type, BigDecimal amount,
                                      Long fromWalletId, Long toWalletId, String description, Instant createdAt) {
        public static TransactionResponse from(WalletTransaction t) {
            return new TransactionResponse(t.getId(), t.getReference(), t.getType(), t.getAmount(),
                    t.getFromWalletId(), t.getToWalletId(), t.getDescription(), t.getCreatedAt());
        }
    }

    public record PageResponse<T>(List<T> content, int page, int totalPages, long totalElements) {}

    public record ErrorResponse(Instant timestamp, int status, String message) {}
}

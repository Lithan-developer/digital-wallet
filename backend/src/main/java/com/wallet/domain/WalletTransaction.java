package com.wallet.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

/** Append-only ledger row. Never updated or deleted by the application. */
@Entity
@Table(name = "wallet_transactions",
       uniqueConstraints = @UniqueConstraint(columnNames = {"initiator_wallet_id", "idempotency_key"}),
       indexes = {@Index(columnList = "from_wallet_id"), @Index(columnList = "to_wallet_id")})
@Getter @Setter @NoArgsConstructor
public class WalletTransaction {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 36)
    private String reference;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TxType type;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    private Long fromWalletId;   // null for deposits
    private Long toWalletId;     // null for withdrawals

    @Column(nullable = false)
    private Long initiatorWalletId;

    @Column(nullable = false, length = 64)
    private String idempotencyKey;

    private String description;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();
}

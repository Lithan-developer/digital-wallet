package com.wallet.service;

import com.wallet.domain.*;
import com.wallet.dto.Dtos.*;
import com.wallet.exception.ApiException;
import com.wallet.repo.TransactionRepository;
import com.wallet.repo.UserRepository;
import com.wallet.repo.WalletRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class WalletService {

    private final WalletRepository wallets;
    private final UserRepository users;
    private final TransactionRepository txs;
    private final AuditLogger audit;

    @Cacheable(value = "wallet", key = "#userId")
    @Transactional(readOnly = true)
    public WalletResponse getWallet(Long userId) {
        Wallet w = wallets.findByUserId(userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Wallet not found"));
        return new WalletResponse(w.getId(), w.getBalance(), w.getCurrency(),
                w.getUser().getFullName(), w.getUser().getEmail());
    }

    @Transactional(readOnly = true)
    public PageResponse<TransactionResponse> history(Long userId, int page, int size) {
        Long walletId = walletIdOf(userId);
        Page<WalletTransaction> p = txs.findHistory(walletId,
                PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 50), Sort.by(Sort.Direction.DESC, "createdAt", "id")));
        return new PageResponse<>(p.map(TransactionResponse::from).getContent(), p.getNumber(), p.getTotalPages(), p.getTotalElements());
    }

    /** Demo top-up. In production this is called by a payment-gateway webhook, not directly by the user. */
    @Transactional
    @CacheEvict(value = "wallet", allEntries = true)
    public TransactionResponse deposit(Long userId, BigDecimal amount, String key) {
        Long id = walletIdOf(userId);
        Optional<WalletTransaction> replay = txs.findByInitiatorWalletIdAndIdempotencyKey(id, key);
        if (replay.isPresent()) return TransactionResponse.from(replay.get());

        Wallet w = lock(id).get(id);
        w.credit(amount);
        WalletTransaction t = save(TxType.DEPOSIT, amount, null, id, id, key, "Wallet top-up");
        audit.log("DEPOSIT", userId, "SUCCESS", "ref=" + t.getReference() + " amount=" + amount);
        return TransactionResponse.from(t);
    }

    @Transactional
    @CacheEvict(value = "wallet", allEntries = true)
    public TransactionResponse withdraw(Long userId, BigDecimal amount, String key) {
        Long id = walletIdOf(userId);
        Optional<WalletTransaction> replay = txs.findByInitiatorWalletIdAndIdempotencyKey(id, key);
        if (replay.isPresent()) return TransactionResponse.from(replay.get());

        Wallet w = lock(id).get(id);
        requireFunds(w, amount, userId, "WITHDRAWAL");
        w.debit(amount);
        WalletTransaction t = save(TxType.WITHDRAWAL, amount, id, null, id, key, "Withdrawal to bank");
        audit.log("WITHDRAWAL", userId, "SUCCESS", "ref=" + t.getReference() + " amount=" + amount);
        return TransactionResponse.from(t);
    }

    /**
     * Atomic debit + credit + ledger row in ONE database transaction.
     * Both wallet rows are locked (ascending id order) so concurrent transfers serialize and cannot overdraw or deadlock.
     */
    @Transactional
    @CacheEvict(value = "wallet", allEntries = true)
    public TransactionResponse transfer(Long userId, String recipientEmail, BigDecimal amount, String description, String key) {
        Long fromId = walletIdOf(userId);
        User recipient = users.findByEmail(recipientEmail.trim().toLowerCase())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "No wallet found for that email"));
        Long toId = walletIdOf(recipient.getId());
        if (fromId.equals(toId)) throw new ApiException(HttpStatus.BAD_REQUEST, "You can't transfer to your own wallet");

        Optional<WalletTransaction> replay = txs.findByInitiatorWalletIdAndIdempotencyKey(fromId, key);
        if (replay.isPresent()) return TransactionResponse.from(replay.get());

        Map<Long, Wallet> locked = lock(fromId, toId);
        Wallet from = locked.get(fromId), to = locked.get(toId);
        requireFunds(from, amount, userId, "TRANSFER");
        from.debit(amount);
        to.credit(amount);

        WalletTransaction t = save(TxType.TRANSFER, amount, fromId, toId, fromId, key, description);
        audit.log("TRANSFER", userId, "SUCCESS", "ref=" + t.getReference() + " amount=" + amount + " from=" + fromId + " to=" + toId);
        return TransactionResponse.from(t);
    }

    // ---------- helpers ----------

    private Long walletIdOf(Long userId) {
        return wallets.findIdByUserId(userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Wallet not found"));
    }

    private Map<Long, Wallet> lock(Long... ids) {
        return wallets.lockAllByIds(Arrays.asList(ids)).stream()
                .collect(Collectors.toMap(Wallet::getId, w -> w));
    }

    private void requireFunds(Wallet w, BigDecimal amount, Long userId, String action) {
        if (w.getBalance().compareTo(amount) < 0) {
            audit.log(action, userId, "REJECTED", "reason=insufficient_funds amount=" + amount);
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "Insufficient balance");
        }
    }

    private WalletTransaction save(TxType type, BigDecimal amount, Long from, Long to, Long initiator, String key, String desc) {
        WalletTransaction t = new WalletTransaction();
        t.setReference(UUID.randomUUID().toString());
        t.setType(type);
        t.setAmount(amount);
        t.setFromWalletId(from);
        t.setToWalletId(to);
        t.setInitiatorWalletId(initiator);
        t.setIdempotencyKey(key);
        t.setDescription(desc);
        try {
            return txs.saveAndFlush(t);
        } catch (DataIntegrityViolationException e) {   // same key raced in on another thread: roll everything back
            throw new ApiException(HttpStatus.CONFLICT, "This request is already being processed");
        }
    }
}

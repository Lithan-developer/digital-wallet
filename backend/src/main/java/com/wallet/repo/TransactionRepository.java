package com.wallet.repo;

import com.wallet.domain.WalletTransaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface TransactionRepository extends JpaRepository<WalletTransaction, Long> {

    Optional<WalletTransaction> findByInitiatorWalletIdAndIdempotencyKey(Long walletId, String key);

    @Query("select t from WalletTransaction t where t.fromWalletId = :id or t.toWalletId = :id")
    Page<WalletTransaction> findHistory(@Param("id") Long walletId, Pageable pageable);
}

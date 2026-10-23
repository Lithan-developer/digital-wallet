package com.wallet.repo;

import com.wallet.domain.Wallet;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface WalletRepository extends JpaRepository<Wallet, Long> {

    Optional<Wallet> findByUserId(Long userId);

    /** Id only, so no stale Wallet entity lands in the persistence context before we take the lock. */
    @Query("select w.id from Wallet w where w.user.id = :userId")
    Optional<Long> findIdByUserId(@Param("userId") Long userId);

    /** SELECT ... FOR UPDATE, always in ascending id order so two opposite transfers can never deadlock. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select w from Wallet w where w.id in :ids order by w.id")
    List<Wallet> lockAllByIds(@Param("ids") List<Long> ids);
}

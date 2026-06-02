package com.wallet.gojo.ledger.repository;

import com.wallet.gojo.ledger.domain.entities.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, UUID> {
    boolean existsByIdempotencyKey(String idempotencyKey);

    @Query("SELECT t FROM Transaction t LEFT JOIN FETCH t.entries WHERE t.id = :id")
    Optional<Transaction> findByIdWithEntries(@Param("id") UUID id);
}

package com.wallet.gojo.ledger.repository;

import com.wallet.gojo.ledger.domain.entities.LedgerEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, UUID> {

    // Calculate the balance for a specific account by summing credits and debits history
    @Query("""
        SELECT COALESCE( SUM(CASE WHEN e.entryType = 'CREDIT' THEN e.amount ELSE -e.amount END), 0 )
        FROM LedgerEntry e
        WHERE e.account.id = :accountId
    """)
    long getBalanceByAccountId(@Param("accountId") UUID accountId);


    // For the global system audit check
    @Query("""
        SELECT COALESCE( SUM(CASE WHEN e.entryType = 'DEBIT' THEN e.amount ELSE -e.amount END), 0 )
        FROM LedgerEntry e
    """)
    long getGlobalSystemDelta();

}

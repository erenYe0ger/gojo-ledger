package com.wallet.gojo.ledger.service;

import com.wallet.gojo.ledger.domain.entities.LedgerEntry;
import com.wallet.gojo.ledger.domain.entities.Transaction;
import com.wallet.gojo.ledger.domain.enums.EntryType;
import com.wallet.gojo.ledger.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class LedgerService {

    private final TransactionRepository transactionRepository;

    @Transactional
    public Transaction executeTransaction(String idempotencyKey, String description, List<LedgerEntry> entries) {

        if(transactionRepository.existsByIdempotencyKey(idempotencyKey)) {
            throw new IllegalArgumentException("Transaction with the same idempotency key already exists");
        }

        Transaction transaction = Transaction.builder()
                .idempotencyKey(idempotencyKey)
                .description(description)
                .build();

        long totalDebits = 0;
        long totalCredits = 0;

        for (LedgerEntry entry : entries) {
            transaction.addEntry(entry);

            if (entry.getEntryType() == EntryType.DEBIT) {
                totalDebits += entry.getAmount();
            } else if (entry.getEntryType() == EntryType.CREDIT) {
                totalCredits += entry.getAmount();
            }
        }

        if (totalDebits != totalCredits) {
            throw new IllegalArgumentException("Total debits must equal total credits");
        }

        return transactionRepository.save(transaction);
    }

}

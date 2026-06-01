package com.wallet.gojo.ledger.service;

import com.wallet.gojo.ledger.domain.entities.LedgerEntry;
import com.wallet.gojo.ledger.domain.entities.Transaction;
import com.wallet.gojo.ledger.domain.enums.AccountType;
import com.wallet.gojo.ledger.domain.enums.EntryType;
import com.wallet.gojo.ledger.repository.LedgerEntryRepository;
import com.wallet.gojo.ledger.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class LedgerService {

    private final TransactionRepository transactionRepository;
    private final LedgerEntryRepository ledgerEntryRepository;

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

            if(entry.getAccount().getAccountType() == AccountType.LIABILITY &&
                entry.getEntryType() == EntryType.DEBIT) {
                long currentBalance = ledgerEntryRepository.getBalanceByAccountId(entry.getAccount().getId());
                if (currentBalance - entry.getAmount() < 0) {
                    throw new IllegalArgumentException("Transaction rejected: Insufficient funds for liability account: "
                            + entry.getAccount().getId());
                }
            }

        }

        if (totalDebits != totalCredits) {
            throw new IllegalArgumentException("Total debits must equal total credits");
        }

        return transactionRepository.save(transaction);
    }

}

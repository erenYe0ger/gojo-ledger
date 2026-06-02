package com.wallet.gojo.ledger.service;

import com.wallet.gojo.ledger.domain.entities.Account;
import com.wallet.gojo.ledger.domain.entities.LedgerEntry;
import com.wallet.gojo.ledger.domain.entities.Transaction;
import com.wallet.gojo.ledger.domain.enums.AccountType;
import com.wallet.gojo.ledger.domain.enums.EntryType;
import com.wallet.gojo.ledger.repository.AccountRepository;
import com.wallet.gojo.ledger.repository.LedgerEntryRepository;
import com.wallet.gojo.ledger.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class LedgerService {

    private final TransactionRepository transactionRepository;
    private final LedgerEntryRepository ledgerEntryRepository;
    private final AccountRepository accountRepository;

    @Transactional
    public Transaction executeTransaction(String idempotencyKey, String description, List<LedgerEntry> entries) {

        if(transactionRepository.existsByIdempotencyKey(idempotencyKey)) {
            throw new IllegalArgumentException("Transaction with the same idempotency key already exists");
        }


        // Concurrency and Deadlock Protection Shield
        // extract account IDs and sort them to ensure consistent locking order
        List<UUID> accountIdsToLock = entries.stream()
                .map(entry -> entry.getAccount().getId())
                .distinct()
                .sorted()
                .toList();

        // lock accounts in a consistent order to prevent deadlocks
        List<Account> lockedAccounts = accountRepository.findAllByIdsForUpdate(accountIdsToLock);

        // map locked accounts back to their IDs for quick access
        Map<UUID, Account> lockedAccountMap = lockedAccounts.stream()
                .collect(Collectors.toMap(Account::getId, account -> account));



        Transaction transaction = Transaction.builder()
                .idempotencyKey(idempotencyKey)
                .description(description)
                .build();

        long totalDebits = 0;
        long totalCredits = 0;


        // fetch initial balances for all involved accounts in a single query to minimize database calls
        List<Object[]> allBalances = ledgerEntryRepository.getBalancesForAccountIds(accountIdsToLock);
        Map<UUID, Long> accountBalances = new HashMap<>();
        for (Object[] balanceData : allBalances) {
            UUID accountId = (UUID) balanceData[0];
            Long balance = (Long) balanceData[1];
            accountBalances.put(accountId, balance);
        }

        // accounts with zero historical ledgers should be initialized with a balance of 0
        for (UUID accountId : accountIdsToLock) {
            accountBalances.putIfAbsent(accountId, 0L);
        }


        for (LedgerEntry entry : entries) {

            // reassign the account reference to the locked account to ensure we are working with the locked version
            Account lockedAccount = lockedAccountMap.get(entry.getAccount().getId());
            if (lockedAccount == null) {
                throw new IllegalArgumentException("Account not found for entry: " + entry.getAccount().getId());
            }
            entry.setAccount(lockedAccount);


            transaction.addEntry(entry);

            long currentBalance = accountBalances.get(lockedAccount.getId());

            if (entry.getEntryType() == EntryType.DEBIT) {
                totalDebits += entry.getAmount();

                if(lockedAccount.getAccountType() == AccountType.LIABILITY) {
                    currentBalance -= entry.getAmount();
                } else if (lockedAccount.getAccountType() == AccountType.ASSET) {
                    currentBalance += entry.getAmount();
                }

            } else if (entry.getEntryType() == EntryType.CREDIT) {
                totalCredits += entry.getAmount();

                if(lockedAccount.getAccountType() == AccountType.LIABILITY) {
                    currentBalance += entry.getAmount();
                } else if (lockedAccount.getAccountType() == AccountType.ASSET) {
                    currentBalance -= entry.getAmount();
                }

            }

            accountBalances.put(lockedAccount.getId(), currentBalance);


            if(lockedAccount.getAccountType() == AccountType.LIABILITY &&
                    currentBalance < 0) {
                throw new IllegalArgumentException("Transaction Rejected: Liability account cannot have negative balance. Account ID: "
                        + lockedAccount.getId());
            }

        }



        if (totalDebits != totalCredits) {
            throw new IllegalArgumentException("Total debits must equal total credits");
        }

        return transactionRepository.save(transaction);
    }

}

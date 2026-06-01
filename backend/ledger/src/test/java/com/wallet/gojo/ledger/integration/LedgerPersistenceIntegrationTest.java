package com.wallet.gojo.ledger.integration;

import com.wallet.gojo.ledger.domain.entities.Account;
import com.wallet.gojo.ledger.domain.entities.LedgerEntry;
import com.wallet.gojo.ledger.domain.entities.Transaction;
import com.wallet.gojo.ledger.domain.entities.User;
import com.wallet.gojo.ledger.domain.enums.AccountType;
import com.wallet.gojo.ledger.domain.enums.EntryType;
import com.wallet.gojo.ledger.repository.AccountRepository;
import com.wallet.gojo.ledger.repository.TransactionRepository;
import com.wallet.gojo.ledger.repository.UserRepository;
import com.wallet.gojo.ledger.service.LedgerService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
public class LedgerPersistenceIntegrationTest {

    @Autowired
    private TransactionRepository transactionRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private AccountRepository accountRepository;
    @Autowired
    private LedgerService ledgerService;

    private Account aliceAccount;
    private Account bobAccount;

    @BeforeEach
    void setUpTestData() {
        // clear db before each test
        transactionRepository.deleteAll();
        accountRepository.deleteAll();
        userRepository.deleteAll();

        // persist a real user for testing
        User masterUser = userRepository.save(
                User.builder()
                    .email("master-user@gojo.com")
                    .legalName("Master User")
                    .build()
        );

        // persist real accounts for alice and bob
        aliceAccount = accountRepository.save(
                Account.builder()
                    .user(masterUser)
                    .accountType(AccountType.LIABILITY)
                    .currency("USD")
                    .build()
        );
        bobAccount = accountRepository.save(
                Account.builder()
                    .user(masterUser)
                    .accountType(AccountType.LIABILITY)
                    .currency("USD")
                    .build()
        );
    }


    @Test
    @DisplayName("Integration: Should write records to disk when transaction balances perfectly")
    void executeTransaction_PersistsToDisk_WhenBalanced() {

        // Arrange
        String idempotencyKey = UUID.randomUUID().toString();

        LedgerEntry aliceDebit = LedgerEntry.builder()
                .account(aliceAccount)
                .entryType(EntryType.DEBIT)
                .amount(5000L) // $50.00
                .build();

        LedgerEntry bobCredit = LedgerEntry.builder()
                .account(bobAccount)
                .entryType(EntryType.CREDIT)
                .amount(5000L) // $50.00
                .build();

        // Act
        Transaction savedTransaction =  ledgerService.executeTransaction(idempotencyKey,
                "Balanced Transaction",
                List.of(aliceDebit, bobCredit));

        // Assert - verify transaction is persisted and can be retrieved
        Transaction retrievedTransaction = transactionRepository.findById(savedTransaction.getId())
                .orElse(null);

        assertNotNull(retrievedTransaction,
                "Transaction should be persisted and retrievable from the database");
        assertEquals(idempotencyKey, retrievedTransaction.getIdempotencyKey(),
                "Idempotency key should match");
        assertEquals("Balanced Transaction", retrievedTransaction.getDescription(),
                "Description should match");
        assertEquals(2, retrievedTransaction.getEntries().size(),
                "Should have 2 ledger entries");

    }

    @Test
    @DisplayName("Integration: Should roll back entire transaction when entries are unbalanced")
    void executeTransaction_RollsBackEverything_WhenUnbalanced() {

        // Arrange
        String idempotencyKey = UUID.randomUUID().toString();

        long initialTransactionCount = transactionRepository.count();

        LedgerEntry aliceDebit = LedgerEntry.builder()
                .account(aliceAccount)
                .entryType(EntryType.DEBIT)
                .amount(5000L) // $50.00
                .build();

        LedgerEntry bobCredit = LedgerEntry.builder()
                .account(bobAccount)
                .entryType(EntryType.CREDIT)
                .amount(3000L) // $30.00 - unbalanced!
                .build();


        // Act & Assert
        // verify that an exception is thrown due to unbalanced entries in service layer
        assertThrows(IllegalArgumentException.class, () -> {
            ledgerService.executeTransaction(idempotencyKey,
                    "Unbalanced Transaction",
                    List.of(aliceDebit, bobCredit));
        }, "Should throw exception for unbalanced transaction");

        // verify that no transaction was persisted to the database
        long finalTransactionCount = transactionRepository.count();
        assertEquals(initialTransactionCount, finalTransactionCount,
                "No new transaction should be persisted due to rollback");

        // verify idempotency key is not persisted
        assertFalse(transactionRepository.existsByIdempotencyKey(idempotencyKey),
                "Idempotency key should not be persisted for failed transaction");

    }

}

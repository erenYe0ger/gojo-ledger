package com.wallet.gojo.ledger.service;

import com.wallet.gojo.ledger.domain.entities.LedgerEntry;
import com.wallet.gojo.ledger.domain.entities.Transaction;
import com.wallet.gojo.ledger.domain.enums.EntryType;
import com.wallet.gojo.ledger.repository.LedgerEntryRepository;
import com.wallet.gojo.ledger.repository.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

public class LedgerServiceTest {

    private LedgerService ledgerService;
    private TransactionRepository transactionRepository;
    private LedgerEntryRepository ledgerEntryRepository;

    // This runs BEFORE every single test to reset our environment clean
    @BeforeEach
    void setUp() {
        transactionRepository = Mockito.mock(TransactionRepository.class);
        ledgerService = new LedgerService(transactionRepository, ledgerEntryRepository);
    }

    @Test
    @DisplayName("Save transaction successfully when debit and credit entries are balanced")
    void saveTransaction_WhenEntriesBalanced() {
        // Arrange (set up the test data and mock behavior)
        String description = "Alice pays Bob $50";
        String idempotencyKey = "unique-key-123";

        LedgerEntry debitEntry = LedgerEntry.builder()
                .entryType(EntryType.DEBIT)
                .amount(5000) // $50 in cents
                .build();

        LedgerEntry creditEntry = LedgerEntry.builder()
                .entryType(EntryType.CREDIT)
                .amount(5000) // $50 in cents
                .build();

        List<LedgerEntry> entries = List.of(debitEntry, creditEntry);

        when(transactionRepository.save(any(Transaction.class))).
                thenAnswer(invocation -> invocation.getArgument(0)); // Mock save to return the transaction


        // ACT (call the method under test)
        Transaction result = ledgerService.executeTransaction(idempotencyKey, description, entries);


        // Assert (verify the results)
        assertNotNull(result);
        assertEquals(description, result.getDescription());
        assertEquals(idempotencyKey, result.getIdempotencyKey());
        assertEquals(2, result.getEntries().size());

    }


    @Test
    @DisplayName("Throw exception when debit and credit entries are not balanced")
    void throwException_WhenEntriesNotBalanced() {
        // Arrange
        String description = "Invalid Transaction";
        String idempotencyKey = "unique-key-123";

        LedgerEntry debitEntry = LedgerEntry.builder()
                .entryType(EntryType.DEBIT)
                .amount(5000) // $50 in cents
                .build();

        LedgerEntry creditEntry = LedgerEntry.builder()
                .entryType(EntryType.CREDIT)
                .amount(3000) // $30 in cents (not balanced)
                .build();

        List<LedgerEntry> entries = List.of(debitEntry, creditEntry);

        // Act & Assert
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> ledgerService.executeTransaction(idempotencyKey, description, entries)
        );

        assertEquals("Total debits must equal total credits", exception.getMessage());
    }


}

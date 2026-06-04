package com.wallet.gojo.ledger.service;

import com.wallet.gojo.ledger.domain.entities.Account;
import com.wallet.gojo.ledger.domain.entities.LedgerEntry;
import com.wallet.gojo.ledger.domain.entities.User;
import com.wallet.gojo.ledger.domain.enums.AccountType;
import com.wallet.gojo.ledger.domain.enums.EntryType;
import com.wallet.gojo.ledger.repository.AccountRepository;
import com.wallet.gojo.ledger.repository.LedgerEntryRepository;
import com.wallet.gojo.ledger.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
@Slf4j
@ActiveProfiles("test")
public class LedgerServiceConcurrencyTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private LedgerService ledgerService;

    @Autowired
    private LedgerEntryRepository ledgerEntryRepository;

    private Account accountA, accountB;

    @BeforeEach
    void setUp() {

        userRepository.deleteAll();
        accountRepository.deleteAll();
        ledgerEntryRepository.deleteAll();

        // setup dummy user for concurrency test
        User user = userRepository.save(
                User.builder()
                    .email("stress.test@gojo.com")
                    .legalName("Stress Test User")
                    .build()
        );
        userRepository.save(user);


        // setup liability account A with $1000 balance
        accountA = accountRepository.save(
                Account.builder()
                    .user(user)
                    .accountType(AccountType.LIABILITY)
                    .currency("USD")
                    .build()
        );

        Account vaultAccount = accountRepository.save(
                Account.builder()
                    .user(user)
                    .accountType(AccountType.ASSET)
                    .currency("USD")
                    .build()
        );

        List<LedgerEntry> initialEntries = List.of(
                LedgerEntry.builder()
                    .account(accountA)
                    .entryType(EntryType.CREDIT)
                    .amount(100000L)
                    .build(),
                LedgerEntry.builder()
                    .account(vaultAccount)
                    .entryType(EntryType.DEBIT)
                    .amount(100000L)
                    .build()
        );

        ledgerService.executeTransaction(
                UUID.randomUUID().toString(),
                "Initial funding for concurrency test",
                initialEntries);

        // setup liability account B with $0 balance
        accountB = accountRepository.save(
                Account.builder()
                    .user(user)
                    .accountType(AccountType.LIABILITY)
                    .currency("USD")
                    .build()
        );

    }


    @Test
    @DisplayName("Test concurrent transfers between two accounts with potential for deadlock and overdraft")
    void testConcurrentTransfers_WithDeadlockAndOverdraftProtection() throws InterruptedException {

        int numberOfThreads = 50;

        // we will try to execute 50 concurrent transfers of $30 from accountA to accountB,
        // total requested transfer amount is $1500, which exceeds accountA's balance of $1000,
        // after 33 successful transfers of $30 (totaling $990), accountA will have only $10 left,
        // so the next 17 transfers should fail due to overdraft protection

        long transferAmount = 3000L; // $30

        // create a thread pool to execute concurrent transfers
        ExecutorService executorService = Executors.newFixedThreadPool(numberOfThreads);

        // latches to coordinate start and end of all threads
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(numberOfThreads);


        // counters to track successful and failed transfers
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);


        for(int i=0; i<numberOfThreads; ++i) {

            executorService.submit(
                    () -> {
                        try {

                            // wait for the signal to start
                            startLatch.await();

                            List<LedgerEntry> entries = List.of(
                                    LedgerEntry.builder()
                                            .account(accountA)
                                            .entryType(EntryType.DEBIT)
                                            .amount(transferAmount)
                                            .build(),
                                    LedgerEntry.builder()
                                            .account(accountB)
                                            .entryType(EntryType.CREDIT)
                                            .amount(transferAmount)
                                            .build()
                            );

                            ledgerService.executeTransaction(
                                    UUID.randomUUID().toString(),
                                    "Concurrent transfer from A to B",
                                    entries
                            );

                            successCount.incrementAndGet();

                        } catch (IllegalArgumentException e) {

                            // we expect some transactions to fail due to overdraft protection
                            failureCount.incrementAndGet();

                        } catch (Exception e) {

                            // any other exceptions would indicate a problem with concurrency handling (e.g. deadlock)
                            log.error("Unexpected exception during concurrent transfer: ", e);

                        } finally {

                            doneLatch.countDown();

                        }
                    }
            );

        }


        // fire the starting gun for all threads to attempt their transfers concurrently
        startLatch.countDown();

        doneLatch.await(); // wait for all threads to finish
        executorService.shutdown();


        // Assertions
        assertEquals(33, successCount.get(),
                "Exactly 33 transfers should succeed before overdraft protection kicks in");
        assertEquals(17, failureCount.get(),
                "Exactly 17 transfers should fail due to overdraft protection");


        long finalBalanceA = ledgerEntryRepository.getBalanceByAccountId(accountA.getId());
        long finalBalanceB = ledgerEntryRepository.getBalanceByAccountId(accountB.getId());

        assertEquals(100000L - 33 * transferAmount, finalBalanceA,
                "Final balance of account A should reflect 33 successful debits of $30");
        assertEquals(33 * transferAmount, finalBalanceB,
                "Final balance of account B should reflect 33 successful credits of $30");


    }

}

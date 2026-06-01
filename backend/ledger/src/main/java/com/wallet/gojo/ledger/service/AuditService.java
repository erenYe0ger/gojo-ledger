package com.wallet.gojo.ledger.service;

import com.wallet.gojo.ledger.repository.LedgerEntryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class AuditService {

    private final LedgerEntryRepository ledgerEntryRepository;

    @Scheduled(fixedRate = 10000) // Schedule to run every 10 seconds
    public void performGlobalLedgerAudit() {
        log.info("Starting global ledger integrity audit scan...");

        long globalDelta = ledgerEntryRepository.getGlobalSystemDelta();

        if(globalDelta != 0) {
            log.error("Global ledger integrity issue detected! System delta: {} cents", globalDelta);
        } else {
            log.info("Global ledger integrity audit passed. System delta is zero.");
        }
    }

}

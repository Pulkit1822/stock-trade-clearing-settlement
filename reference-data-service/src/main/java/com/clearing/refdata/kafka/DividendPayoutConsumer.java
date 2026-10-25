package com.clearing.refdata.kafka;

import com.clearing.refdata.model.Account;
import com.clearing.refdata.repository.AccountRepository;
import com.clearing.shared.events.DividendPayoutEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class DividendPayoutConsumer {

    private static final Logger log = LoggerFactory.getLogger(DividendPayoutConsumer.class);

    private final AccountRepository accountRepository;

    public DividendPayoutConsumer(AccountRepository accountRepository) {
        this.accountRepository = accountRepository;
    }

    @KafkaListener(
            topics = "dividend-payout-events",
            groupId = "reference-data-service-dividend",
            containerFactory = "kafkaListenerContainerFactory"
    )
    @Transactional
    public void consume(DividendPayoutEvent event) {
        log.info("Received DividendPayoutEvent for Account [{}] Amount [{}]", event.accountId(), event.amount());

        accountRepository.findByAccountId(event.accountId()).ifPresentOrElse(
                account -> {
                    account.setBalance(account.getBalance().add(event.amount()));
                    accountRepository.save(account);
                    log.info("Account [{}] balance successfully credited with dividend. New Balance: {}", 
                            account.getAccountId(), account.getBalance());
                },
                () -> log.warn("Could not process dividend; Account [{}] not found", event.accountId())
        );
    }
}

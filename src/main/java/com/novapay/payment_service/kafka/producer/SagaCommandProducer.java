package com.novapay.payment_service.kafka.producer;

import com.novapay.payment_service.kafka.constants.KafkaTopics;
import com.novapay.payment_service.saga.event.CreateTransactionCommand;
import com.novapay.payment_service.saga.event.DebitWalletCommand;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class SagaCommandProducer {

    private final KafkaTemplate<String, DebitWalletCommand> walletKafkaTemplate;
    private final KafkaTemplate<String, CreateTransactionCommand> transactionKafkaTemplate;

    public void debitWallet(DebitWalletCommand command) {

        log.info(
                "Sending DEBIT_WALLET command. Payment Reference: {}",
                command.paymentReference());

        walletKafkaTemplate.send(
                KafkaTopics.WALLET_COMMANDS,
                command.paymentReference(),
                command);
    }

    public void createTransaction(CreateTransactionCommand command) {

        log.info(
                "Sending CREATE_TRANSACTION command. Payment Reference: {}",
                command.paymentReference());

        transactionKafkaTemplate.send(
                KafkaTopics.TRANSACTION_COMMANDS,
                command.paymentReference(),
                command);
    }
}

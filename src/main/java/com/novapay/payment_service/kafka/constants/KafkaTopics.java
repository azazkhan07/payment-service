package com.novapay.payment_service.kafka.constants;

public final class KafkaTopics {

    public static final String PAYMENT_EVENTS = "payment-events";

    public static final String WALLET_COMMANDS = "wallet-commands";

    public static final String WALLET_EVENTS = "wallet-events";

    public static final String TRANSACTION_COMMANDS = "transaction-commands";

    public static final String TRANSACTION_EVENTS = "transaction-events";

    private KafkaTopics() {
    }
}

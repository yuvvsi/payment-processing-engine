package com.yuvvsi.ppe.kafka;

import com.yuvvsi.ppe.config.KafkaConfig;
import com.yuvvsi.ppe.event.PaymentEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentEventProducer {

    private final KafkaTemplate<String, PaymentEvent> kafkaTemplate;

    public void publishPaymentEvent(PaymentEvent event) {
        CompletableFuture<SendResult<String, PaymentEvent>> future =
                kafkaTemplate.send(
                        KafkaConfig.PAYMENT_EVENTS_TOPIC,
                        event.getTransactionId(),
                        event
                );

        future.whenComplete((result, ex) -> {
            if (ex != null) {
                log.error("Failed to publish payment event for transactionId: {} — {}",
                        event.getTransactionId(), ex.getMessage());
            } else {
                log.info("Published payment event for transactionId: {} to partition: {} offset: {}",
                        event.getTransactionId(),
                        result.getRecordMetadata().partition(),
                        result.getRecordMetadata().offset());
            }
        });
    }
}
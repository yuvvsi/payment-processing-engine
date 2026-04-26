package com.yuvvsi.ppe.kafka;

import com.yuvvsi.ppe.config.KafkaConfig;
import com.yuvvsi.ppe.event.PaymentEvent;
import com.yuvvsi.ppe.model.Transaction;
import com.yuvvsi.ppe.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class DLQConsumer {

    private final TransactionRepository transactionRepository;

    @KafkaListener(
            topics = KafkaConfig.PAYMENT_DLQ_TOPIC,
            groupId = "ppe-dlq-group",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void consumeDLQEvent(PaymentEvent event) {
        log.error("DLQ: Processing failed event for transactionId: {} after all retries exhausted",
                event.getTransactionId());

        transactionRepository.findById(event.getTransactionId()).ifPresent(transaction -> {
            if (transaction.getStatus() != Transaction.TransactionStatus.SUCCESS) {
                transaction.setStatus(Transaction.TransactionStatus.FAILED);
                transaction.setFailureReason("Failed after max retries — sent to DLQ");
                transaction.setRetryCount(3);
                transaction.setUpdatedAt(LocalDateTime.now());
                transactionRepository.save(transaction);
                log.error("DLQ: Transaction {} marked as FAILED after exhausting retries",
                        event.getTransactionId());
            }
        });
    }
}
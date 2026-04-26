package com.yuvvsi.ppe.kafka;

import com.yuvvsi.ppe.config.KafkaConfig;
import com.yuvvsi.ppe.event.PaymentEvent;
import com.yuvvsi.ppe.model.Transaction;
import com.yuvvsi.ppe.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentEventConsumer {

    private final TransactionRepository transactionRepository;
    private final KafkaTemplate<String, PaymentEvent> kafkaTemplate;

    @KafkaListener(
            topics = KafkaConfig.PAYMENT_EVENTS_TOPIC,
            groupId = "ppe-group",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void consumePaymentEvent(PaymentEvent event) {
        log.info("Consumed payment event for transactionId: {} status: {}",
                event.getTransactionId(), event.getStatus());

        try {
            processPaymentEvent(event);
        } catch (Exception e) {
            log.error("Error processing payment event for transactionId: {} — {}",
                    event.getTransactionId(), e.getMessage());
            throw e; // rethrow so Kafka retry/DLQ kicks in (Phase 5)
        }
    }

    private void processPaymentEvent(PaymentEvent event) {
        Transaction transaction = transactionRepository.findById(event.getTransactionId())
                .orElseThrow(() -> new RuntimeException(
                        "Transaction not found: " + event.getTransactionId()));

        // Update to PROCESSING
        transaction.setStatus(Transaction.TransactionStatus.PROCESSING);
        transaction.setUpdatedAt(LocalDateTime.now());
        transactionRepository.save(transaction);
        log.info("Transaction {} moved to PROCESSING", event.getTransactionId());

        // Simulate payment processing logic
        // In production this would call a payment gateway (Razorpay, Stripe etc)
        boolean paymentSuccessful = simulatePaymentProcessing(event);

        if (paymentSuccessful) {
            transaction.setStatus(Transaction.TransactionStatus.SUCCESS);
            transaction.setUpdatedAt(LocalDateTime.now());
            transactionRepository.save(transaction);
            log.info("Transaction {} SUCCESS", event.getTransactionId());

            // Publish success notification
            PaymentEvent successEvent = PaymentEvent.builder()
                    .transactionId(event.getTransactionId())
                    .senderId(event.getSenderId())
                    .receiverId(event.getReceiverId())
                    .amount(event.getAmount())
                    .currency(event.getCurrency())
                    .status(Transaction.TransactionStatus.SUCCESS)
                    .timestamp(LocalDateTime.now())
                    .build();

            kafkaTemplate.send(KafkaConfig.NOTIFICATION_TOPIC,
                    event.getTransactionId(), successEvent);
            log.info("Published SUCCESS notification for transactionId: {}",
                    event.getTransactionId());

        } else {
            transaction.setStatus(Transaction.TransactionStatus.FAILED);
            transaction.setFailureReason("Payment gateway declined");
            transaction.setUpdatedAt(LocalDateTime.now());
            transactionRepository.save(transaction);
            log.warn("Transaction {} FAILED", event.getTransactionId());

            // Publish failure notification
            PaymentEvent failedEvent = PaymentEvent.builder()
                    .transactionId(event.getTransactionId())
                    .senderId(event.getSenderId())
                    .receiverId(event.getReceiverId())
                    .amount(event.getAmount())
                    .currency(event.getCurrency())
                    .status(Transaction.TransactionStatus.FAILED)
                    .failureReason("Payment gateway declined")
                    .timestamp(LocalDateTime.now())
                    .build();

            kafkaTemplate.send(KafkaConfig.NOTIFICATION_TOPIC,
                    event.getTransactionId(), failedEvent);

            // Throw exception to trigger DLQ (Phase 5)
            throw new RuntimeException("Payment gateway declined for transactionId: "
                    + event.getTransactionId());
        }
    }

    private boolean simulatePaymentProcessing(PaymentEvent event) {
        return false;
    }
}
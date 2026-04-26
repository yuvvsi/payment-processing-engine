package com.yuvvsi.ppe.service;

import com.yuvvsi.ppe.dto.PaymentRequest;
import com.yuvvsi.ppe.dto.PaymentResponse;
import com.yuvvsi.ppe.event.PaymentEvent;
import com.yuvvsi.ppe.kafka.PaymentEventProducer;
import com.yuvvsi.ppe.model.Transaction;
import com.yuvvsi.ppe.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {

    private final TransactionRepository transactionRepository;
    private final IdempotencyService idempotencyService;
    private final PaymentEventProducer paymentEventProducer;

    public PaymentResponse processPayment(PaymentRequest request) {

        // Redis idempotency check
        if (idempotencyService.isDuplicate(request.getIdempotencyKey())) {
            String existingTransactionId = idempotencyService
                    .getTransactionId(request.getIdempotencyKey());

            log.warn("Duplicate payment detected for idempotency key: {}",
                    request.getIdempotencyKey());

            return transactionRepository.findById(existingTransactionId)
                    .map(t -> PaymentResponse.from(t, "Duplicate request - returning existing transaction"))
                    .orElse(PaymentResponse.builder()
                            .idempotencyKey(request.getIdempotencyKey())
                            .status(Transaction.TransactionStatus.DUPLICATE)
                            .message("Duplicate request detected")
                            .build());
        }

        // Create transaction in PENDING state
        Transaction transaction = Transaction.builder()
                .idempotencyKey(request.getIdempotencyKey())
                .senderId(request.getSenderId())
                .receiverId(request.getReceiverId())
                .amount(request.getAmount())
                .currency(request.getCurrency())
                .status(Transaction.TransactionStatus.PENDING)
                .retryCount(0)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        Transaction saved = transactionRepository.save(transaction);

        // Store in Redis to prevent duplicates
        idempotencyService.store(request.getIdempotencyKey(), saved.getId());

        // Publish to Kafka asynchronously
        PaymentEvent event = PaymentEvent.from(saved);
        paymentEventProducer.publishPaymentEvent(event);

        log.info("Payment created and event published for transactionId: {}", saved.getId());

        return PaymentResponse.from(saved, "Payment accepted for processing");
    }

    public PaymentResponse getTransaction(String transactionId) {
        return transactionRepository.findById(transactionId)
                .map(t -> PaymentResponse.from(t, "Transaction found"))
                .orElseThrow(() -> new RuntimeException("Transaction not found: " + transactionId));
    }
}
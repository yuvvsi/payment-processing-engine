package com.yuvvsi.ppe.event;

import com.yuvvsi.ppe.model.Transaction;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentEvent {

    private String transactionId;
    private String idempotencyKey;
    private String senderId;
    private String receiverId;
    private BigDecimal amount;
    private String currency;
    private Transaction.TransactionStatus status;
    private String failureReason;
    private int retryCount;
    private LocalDateTime timestamp;

    public static PaymentEvent from(Transaction transaction) {
        return PaymentEvent.builder()
                .transactionId(transaction.getId())
                .idempotencyKey(transaction.getIdempotencyKey())
                .senderId(transaction.getSenderId())
                .receiverId(transaction.getReceiverId())
                .amount(transaction.getAmount())
                .currency(transaction.getCurrency())
                .status(transaction.getStatus())
                .failureReason(transaction.getFailureReason())
                .retryCount(transaction.getRetryCount())
                .timestamp(LocalDateTime.now())
                .build();
    }
}
package com.yuvvsi.ppe.dto;

import com.yuvvsi.ppe.model.Transaction;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
public class PaymentResponse {

    private String transactionId;
    private String idempotencyKey;
    private String senderId;
    private String receiverId;
    private BigDecimal amount;
    private String currency;
    private Transaction.TransactionStatus status;
    private String message;
    private LocalDateTime createdAt;

    public static PaymentResponse from(Transaction transaction, String message) {
        return PaymentResponse.builder()
                .transactionId(transaction.getId())
                .idempotencyKey(transaction.getIdempotencyKey())
                .senderId(transaction.getSenderId())
                .receiverId(transaction.getReceiverId())
                .amount(transaction.getAmount())
                .currency(transaction.getCurrency())
                .status(transaction.getStatus())
                .message(message)
                .createdAt(transaction.getCreatedAt())
                .build();
    }
}
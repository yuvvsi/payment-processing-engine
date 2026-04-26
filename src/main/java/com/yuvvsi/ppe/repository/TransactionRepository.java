package com.yuvvsi.ppe.repository;

import com.yuvvsi.ppe.model.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, String> {

    Optional<Transaction> findByIdempotencyKey(String idempotencyKey);

    List<Transaction> findByStatus(Transaction.TransactionStatus status);

    List<Transaction> findBySenderId(String senderId);

    @Query("SELECT t FROM Transaction t WHERE t.createdAt BETWEEN :start AND :end")
    List<Transaction> findByCreatedAtBetween(LocalDateTime start, LocalDateTime end);

    long countByStatus(Transaction.TransactionStatus status);
}
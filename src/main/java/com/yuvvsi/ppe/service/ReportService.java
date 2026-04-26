package com.yuvvsi.ppe.service;

import com.yuvvsi.ppe.model.Transaction;
import com.yuvvsi.ppe.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReportService {

    private final TransactionRepository transactionRepository;
    private final S3Client s3Client;

    @Value("${aws.s3.bucket}")
    private String bucketName;

    // Runs at midnight on the 1st of every month
    @Scheduled(cron = "0 0 0 1 * *")
    public void generateAndUploadMonthlyReport() {
        log.info("Starting monthly transaction report generation");

        LocalDateTime end = LocalDateTime.now();
        LocalDateTime start = end.minusMonths(1);

        List<Transaction> transactions = transactionRepository
                .findByCreatedAtBetween(start, end);

        if (transactions.isEmpty()) {
            log.info("No transactions found for period {} to {}", start, end);
            return;
        }

        String csvContent = generateCSV(transactions);
        String fileName = generateFileName(start);

        uploadToS3(fileName, csvContent);
    }

    // Can also be triggered manually via REST endpoint
    public String generateReportForMonth(LocalDateTime start, LocalDateTime end) {
        log.info("Generating report for period {} to {}", start, end);

        List<Transaction> transactions = transactionRepository
                .findByCreatedAtBetween(start, end);

        String csvContent = generateCSV(transactions);
        String fileName = generateFileName(start);

        uploadToS3(fileName, csvContent);

        return String.format("s3://%s/%s", bucketName, fileName);
    }

    private String generateCSV(List<Transaction> transactions) {
        StringBuilder csv = new StringBuilder();

        // Header
        csv.append("Transaction ID,Idempotency Key,Sender ID,Receiver ID,")
                .append("Amount,Currency,Status,Failure Reason,Retry Count,")
                .append("Created At,Updated At\n");

        // Rows
        for (Transaction t : transactions) {
            csv.append(t.getId()).append(",")
                    .append(t.getIdempotencyKey()).append(",")
                    .append(t.getSenderId()).append(",")
                    .append(t.getReceiverId()).append(",")
                    .append(t.getAmount()).append(",")
                    .append(t.getCurrency()).append(",")
                    .append(t.getStatus()).append(",")
                    .append(t.getFailureReason() != null ? t.getFailureReason() : "").append(",")
                    .append(t.getRetryCount()).append(",")
                    .append(t.getCreatedAt()).append(",")
                    .append(t.getUpdatedAt()).append("\n");
        }

        // Summary section
        csv.append("\n--- SUMMARY ---\n");
        csv.append("Total Transactions,").append(transactions.size()).append("\n");

        Map<Transaction.TransactionStatus, Long> statusCounts = transactions.stream()
                .collect(Collectors.groupingBy(Transaction::getStatus, Collectors.counting()));

        statusCounts.forEach((status, count) ->
                csv.append(status).append(",").append(count).append("\n"));

        double totalAmount = transactions.stream()
                .filter(t -> t.getStatus() == Transaction.TransactionStatus.SUCCESS)
                .mapToDouble(t -> t.getAmount().doubleValue())
                .sum();

        csv.append("Total Successful Amount,").append(totalAmount).append("\n");

        long successCount = statusCounts.getOrDefault(
                Transaction.TransactionStatus.SUCCESS, 0L);
        long totalCount = transactions.size();
        double successRate = totalCount > 0
                ? (double) successCount / totalCount * 100 : 0;

        csv.append("Success Rate,").append(String.format("%.2f%%", successRate)).append("\n");

        return csv.toString();
    }

    private String generateFileName(LocalDateTime date) {
        String month = date.format(DateTimeFormatter.ofPattern("yyyy-MM"));
        return String.format("reports/%s/transaction-report-%s.csv", month, month);
    }

    private void uploadToS3(String fileName, String content) {
        try {
            PutObjectRequest request = PutObjectRequest.builder()
                    .bucket(bucketName)
                    .key(fileName)
                    .contentType("text/csv")
                    .build();

            s3Client.putObject(request,
                    RequestBody.fromBytes(content.getBytes()));

            log.info("Report uploaded to s3://{}/{}", bucketName, fileName);

        } catch (Exception e) {
            log.error("Failed to upload report to S3: {}", e.getMessage());
            throw new RuntimeException("S3 upload failed", e);
        }
    }
}
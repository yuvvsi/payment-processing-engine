package com.yuvvsi.ppe.controller;

import com.yuvvsi.ppe.dto.PaymentRequest;
import com.yuvvsi.ppe.dto.PaymentResponse;
import com.yuvvsi.ppe.service.PaymentService;
import com.yuvvsi.ppe.service.ReportService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

@Slf4j
@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    @PostMapping
    public ResponseEntity<PaymentResponse> processPayment(
            @Valid @RequestBody PaymentRequest request) {
        log.info("Received payment request with idempotencyKey: {}",
                request.getIdempotencyKey());
        PaymentResponse response = paymentService.processPayment(request);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{transactionId}")
    public ResponseEntity<PaymentResponse> getTransaction(
            @PathVariable String transactionId) {
        return ResponseEntity.ok(paymentService.getTransaction(transactionId));
    }

    @Autowired
    private ReportService reportService;

    @PostMapping("/reports/generate")
    public ResponseEntity<String> generateReport(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            LocalDateTime start,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            LocalDateTime end) {
        String s3Url = reportService.generateReportForMonth(start, end);
        return ResponseEntity.ok("Report generated: " + s3Url);
    }
}
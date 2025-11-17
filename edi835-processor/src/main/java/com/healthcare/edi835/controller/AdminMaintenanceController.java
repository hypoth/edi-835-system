package com.healthcare.edi835.controller;

import com.healthcare.edi835.entity.EdiFileBucket;
import com.healthcare.edi835.entity.Payer;
import com.healthcare.edi835.entity.Payee;
import com.healthcare.edi835.repository.EdiFileBucketRepository;
import com.healthcare.edi835.repository.PayerRepository;
import com.healthcare.edi835.repository.PayeeRepository;
import com.healthcare.edi835.service.EncryptionService;
import com.healthcare.edi835.util.EdiIdentifierNormalizer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Administrative maintenance controller for data cleanup and normalization.
 *
 * <p>Base Path: /api/v1/admin/maintenance</p>
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/admin/maintenance")
public class AdminMaintenanceController {

    private final PayerRepository payerRepository;
    private final PayeeRepository payeeRepository;
    private final EdiFileBucketRepository bucketRepository;
    private final EncryptionService encryptionService;

    public AdminMaintenanceController(
            PayerRepository payerRepository,
            PayeeRepository payeeRepository,
            EdiFileBucketRepository bucketRepository,
            EncryptionService encryptionService) {
        this.payerRepository = payerRepository;
        this.payeeRepository = payeeRepository;
        this.bucketRepository = bucketRepository;
        this.encryptionService = encryptionService;
    }

    /**
     * Normalizes all payer IDs and ISA Sender IDs to conform to EDI validation rules.
     *
     * POST /api/v1/admin/maintenance/normalize-payers
     */
    @PostMapping("/normalize-payers")
    public ResponseEntity<Map<String, Object>> normalizePayers(
            @RequestParam(defaultValue = "false") boolean dryRun) {
        log.info("POST /api/v1/admin/maintenance/normalize-payers (dryRun={})", dryRun);

        List<Map<String, Object>> updates = new ArrayList<>();
        List<Map<String, Object>> errors = new ArrayList<>();
        int updatedCount = 0;

        try {
            List<Payer> allPayers = payerRepository.findAll();

            for (Payer payer : allPayers) {
                String originalPayerId = payer.getPayerId();
                String originalIsaSenderId = payer.getIsaSenderId();
                String originalGsSenderId = payer.getGsApplicationSenderId();

                // Check if payer ID needs normalization
                boolean needsUpdate = false;
                String normalizedPayerId = EdiIdentifierNormalizer.normalizePayerId(originalPayerId);

                Map<String, Object> update = new HashMap<>();
                update.put("id", payer.getId());
                update.put("originalPayerId", originalPayerId);

                if (!normalizedPayerId.equals(originalPayerId)) {
                    needsUpdate = true;
                    update.put("normalizedPayerId", normalizedPayerId);

                    // Check if normalized ID already exists
                    Optional<Payer> existingPayer = payerRepository.findByPayerId(normalizedPayerId);
                    if (existingPayer.isPresent() && !existingPayer.get().getId().equals(payer.getId())) {
                        errors.add(Map.of(
                                "payerId", originalPayerId,
                                "error", "Normalized ID '" + normalizedPayerId + "' already exists"
                        ));
                        continue;
                    }
                } else {
                    update.put("normalizedPayerId", originalPayerId + " (no change)");
                }

                // Check if ISA Sender ID needs normalization
                String normalizedIsaSenderId = originalIsaSenderId;
                if (originalIsaSenderId == null || originalIsaSenderId.isEmpty() ||
                    !EdiIdentifierNormalizer.isValidIsaSenderId(originalIsaSenderId)) {
                    normalizedIsaSenderId = EdiIdentifierNormalizer.generateIsaSenderId(normalizedPayerId);
                    needsUpdate = true;
                    update.put("originalIsaSenderId", originalIsaSenderId != null ? originalIsaSenderId : "(null)");
                    update.put("normalizedIsaSenderId", normalizedIsaSenderId);
                } else {
                    update.put("originalIsaSenderId", originalIsaSenderId);
                    update.put("normalizedIsaSenderId", originalIsaSenderId + " (no change)");
                }

                // Check if GS Application Sender ID needs normalization
                String normalizedGsSenderId = originalGsSenderId;
                if (originalGsSenderId == null || originalGsSenderId.isEmpty() ||
                    !EdiIdentifierNormalizer.isValidIsaSenderId(originalGsSenderId)) {
                    normalizedGsSenderId = EdiIdentifierNormalizer.generateGsApplicationSenderId(normalizedPayerId);
                    needsUpdate = true;
                    update.put("originalGsSenderId", originalGsSenderId != null ? originalGsSenderId : "(null)");
                    update.put("normalizedGsSenderId", normalizedGsSenderId);
                } else {
                    update.put("originalGsSenderId", originalGsSenderId);
                    update.put("normalizedGsSenderId", originalGsSenderId + " (no change)");
                }

                if (needsUpdate) {
                    update.put("action", dryRun ? "would update" : "updated");
                    updates.add(update);

                    if (!dryRun) {
                        payer.setPayerId(normalizedPayerId);
                        payer.setIsaSenderId(normalizedIsaSenderId);
                        payer.setGsApplicationSenderId(normalizedGsSenderId);
                        payerRepository.save(payer);
                        updatedCount++;

                        // Update related buckets
                        List<EdiFileBucket> buckets = bucketRepository.findByPayerId(originalPayerId);
                        for (EdiFileBucket bucket : buckets) {
                            bucket.setPayerId(normalizedPayerId);
                            bucketRepository.save(bucket);
                        }
                        log.info("Normalized payer: '{}' -> '{}', ISA: '{}'",
                                originalPayerId, normalizedPayerId, normalizedIsaSenderId);
                    }
                }
            }

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "dryRun", dryRun,
                    "totalPayers", allPayers.size(),
                    "payersNeedingUpdate", updates.size(),
                    "payersUpdated", updatedCount,
                    "updates", updates,
                    "errors", errors
            ));

        } catch (Exception e) {
            log.error("Failed to normalize payers", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of(
                            "success", false,
                            "error", e.getMessage(),
                            "updates", updates,
                            "errors", errors
                    ));
        }
    }

    /**
     * Normalizes all payee IDs to conform to EDI validation rules.
     *
     * POST /api/v1/admin/maintenance/normalize-payees
     */
    @PostMapping("/normalize-payees")
    public ResponseEntity<Map<String, Object>> normalizePayees(
            @RequestParam(defaultValue = "false") boolean dryRun) {
        log.info("POST /api/v1/admin/maintenance/normalize-payees (dryRun={})", dryRun);

        List<Map<String, Object>> updates = new ArrayList<>();
        List<Map<String, Object>> errors = new ArrayList<>();
        int updatedCount = 0;

        try {
            List<Payee> allPayees = payeeRepository.findAll();

            for (Payee payee : allPayees) {
                String originalPayeeId = payee.getPayeeId();
                String normalizedPayeeId = EdiIdentifierNormalizer.normalizePayeeId(originalPayeeId);

                if (!normalizedPayeeId.equals(originalPayeeId)) {
                    // Check if normalized ID already exists
                    Optional<Payee> existingPayee = payeeRepository.findByPayeeId(normalizedPayeeId);
                    if (existingPayee.isPresent() && !existingPayee.get().getId().equals(payee.getId())) {
                        errors.add(Map.of(
                                "payeeId", originalPayeeId,
                                "error", "Normalized ID '" + normalizedPayeeId + "' already exists"
                        ));
                        continue;
                    }

                    Map<String, Object> update = Map.of(
                            "id", payee.getId(),
                            "originalPayeeId", originalPayeeId,
                            "normalizedPayeeId", normalizedPayeeId,
                            "action", dryRun ? "would update" : "updated"
                    );
                    updates.add(update);

                    if (!dryRun) {
                        payee.setPayeeId(normalizedPayeeId);
                        payeeRepository.save(payee);
                        updatedCount++;

                        // Update related buckets
                        List<EdiFileBucket> buckets = bucketRepository.findByPayeeId(originalPayeeId);
                        for (EdiFileBucket bucket : buckets) {
                            bucket.setPayeeId(normalizedPayeeId);
                            bucketRepository.save(bucket);
                        }
                        log.info("Normalized payee: '{}' -> '{}'", originalPayeeId, normalizedPayeeId);
                    }
                }
            }

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "dryRun", dryRun,
                    "totalPayees", allPayees.size(),
                    "payeesNeedingUpdate", updates.size(),
                    "payeesUpdated", updatedCount,
                    "updates", updates,
                    "errors", errors
            ));

        } catch (Exception e) {
            log.error("Failed to normalize payees", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of(
                            "success", false,
                            "error", e.getMessage(),
                            "updates", updates,
                            "errors", errors
                    ));
        }
    }

    /**
     * Get summary of payers/payees that need normalization.
     *
     * GET /api/v1/admin/maintenance/normalization-report
     */
    @GetMapping("/normalization-report")
    public ResponseEntity<Map<String, Object>> getNormalizationReport() {
        log.debug("GET /api/v1/admin/maintenance/normalization-report");

        try {
            List<Payer> allPayers = payerRepository.findAll();
            List<Payee> allPayees = payeeRepository.findAll();

            List<String> invalidPayers = allPayers.stream()
                    .filter(p -> !EdiIdentifierNormalizer.isValidPayerPayeeId(p.getPayerId()) ||
                                (p.getIsaSenderId() != null && !EdiIdentifierNormalizer.isValidIsaSenderId(p.getIsaSenderId())))
                    .map(Payer::getPayerId)
                    .collect(Collectors.toList());

            List<String> invalidPayees = allPayees.stream()
                    .filter(p -> !EdiIdentifierNormalizer.isValidPayerPayeeId(p.getPayeeId()))
                    .map(Payee::getPayeeId)
                    .collect(Collectors.toList());

            return ResponseEntity.ok(Map.of(
                    "totalPayers", allPayers.size(),
                    "invalidPayerCount", invalidPayers.size(),
                    "invalidPayers", invalidPayers,
                    "totalPayees", allPayees.size(),
                    "invalidPayeeCount", invalidPayees.size(),
                    "invalidPayees", invalidPayees,
                    "recommendation", invalidPayers.isEmpty() && invalidPayees.isEmpty()
                            ? "All payer and payee IDs are valid"
                            : "Run POST /api/v1/admin/maintenance/normalize-payers and /normalize-payees to fix"
            ));

        } catch (Exception e) {
            log.error("Failed to generate normalization report", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Encrypts all plain-text SFTP passwords in the database.
     * This is a one-time migration endpoint to encrypt existing passwords.
     *
     * POST /api/v1/admin/maintenance/encrypt-passwords
     */
    @PostMapping("/encrypt-passwords")
    public ResponseEntity<Map<String, Object>> encryptPasswords(
            @RequestParam(defaultValue = "false") boolean dryRun) {
        log.info("POST /api/v1/admin/maintenance/encrypt-passwords (dryRun={})", dryRun);

        if (!encryptionService.isEncryptionEnabled()) {
            return ResponseEntity.badRequest()
                    .body(Map.of(
                            "success", false,
                            "error", "Encryption is not enabled. Please configure encryption.key and encryption.salt in application.yml"
                    ));
        }

        List<Map<String, Object>> updates = new ArrayList<>();
        List<Map<String, Object>> errors = new ArrayList<>();
        int encryptedCount = 0;

        try {
            List<Payer> allPayers = payerRepository.findAll();

            for (Payer payer : allPayers) {
                String password = payer.getSftpPassword();

                // Skip if no password
                if (password == null || password.isEmpty()) {
                    continue;
                }

                // Try to detect if password is already encrypted
                // Encrypted passwords are typically hex-encoded and longer
                boolean looksEncrypted = false;
                try {
                    // Try to decrypt - if successful, it's already encrypted
                    String decrypted = encryptionService.decrypt(password);
                    looksEncrypted = true;
                    log.debug("Password for payer {} is already encrypted", payer.getPayerId());
                } catch (Exception e) {
                    // Decryption failed - likely plain text
                    looksEncrypted = false;
                }

                if (!looksEncrypted) {
                    Map<String, Object> update = new HashMap<>();
                    update.put("payerId", payer.getPayerId());
                    update.put("payerName", payer.getPayerName());
                    update.put("action", dryRun ? "would encrypt" : "encrypted");
                    update.put("passwordLength", password.length());
                    updates.add(update);

                    if (!dryRun) {
                        try {
                            // Encrypt the plain-text password
                            String encryptedPassword = encryptionService.encrypt(password);
                            payer.setSftpPassword(encryptedPassword);
                            payerRepository.save(payer);
                            encryptedCount++;
                            log.info("Encrypted SFTP password for payer: {}", payer.getPayerId());
                        } catch (Exception e) {
                            errors.add(Map.of(
                                    "payerId", payer.getPayerId(),
                                    "error", "Failed to encrypt password: " + e.getMessage()
                            ));
                            log.error("Failed to encrypt password for payer: {}", payer.getPayerId(), e);
                        }
                    }
                }
            }

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "dryRun", dryRun,
                    "totalPayers", allPayers.size(),
                    "passwordsNeedingEncryption", updates.size(),
                    "passwordsEncrypted", encryptedCount,
                    "updates", updates,
                    "errors", errors,
                    "message", dryRun
                            ? "Dry run complete. Set dryRun=false to apply changes."
                            : "Password encryption complete."
            ));

        } catch (Exception e) {
            log.error("Failed to encrypt passwords", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of(
                            "success", false,
                            "error", e.getMessage(),
                            "updates", updates,
                            "errors", errors
                    ));
        }
    }
}

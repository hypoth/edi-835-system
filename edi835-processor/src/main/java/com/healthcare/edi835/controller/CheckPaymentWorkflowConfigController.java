package com.healthcare.edi835.controller;

import com.healthcare.edi835.model.dto.CheckPaymentWorkflowConfigDTO;
import com.healthcare.edi835.model.dto.CheckPaymentWorkflowConfigRequest;
import com.healthcare.edi835.service.CheckPaymentWorkflowConfigService;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * REST Controller for Check Payment Workflow Configuration.
 * Provides endpoints for managing workflow configurations linked to thresholds.
 *
 * <p>Note: CORS is configured globally in WebConfig.java to allow specific origins
 * (localhost:3000, localhost:5173 for dev). Do not use @CrossOrigin(origins = "*")
 * as it conflicts with allowCredentials=true.</p>
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/config/check-payment-workflow")
public class CheckPaymentWorkflowConfigController {

    private final CheckPaymentWorkflowConfigService workflowConfigService;

    public CheckPaymentWorkflowConfigController(CheckPaymentWorkflowConfigService workflowConfigService) {
        this.workflowConfigService = workflowConfigService;
    }

    /**
     * Creates a new workflow configuration.
     *
     * POST /api/v1/config/check-payment-workflow
     */
    @PostMapping
    public ResponseEntity<?> createWorkflowConfig(@Valid @RequestBody CheckPaymentWorkflowConfigRequest request) {
        try {
            log.info("Creating workflow configuration: {}", request.getConfigName());
            CheckPaymentWorkflowConfigDTO config = workflowConfigService.createWorkflowConfig(request);
            return ResponseEntity.status(HttpStatus.CREATED).body(config);
        } catch (IllegalArgumentException | IllegalStateException e) {
            log.error("Failed to create workflow configuration: {}", e.getMessage());
            return ResponseEntity.badRequest().body(new ErrorResponse(e.getMessage()));
        } catch (Exception e) {
            log.error("Unexpected error creating workflow configuration", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse("Failed to create workflow configuration"));
        }
    }

    /**
     * Updates an existing workflow configuration.
     *
     * PUT /api/v1/config/check-payment-workflow/{id}
     */
    @PutMapping("/{id}")
    public ResponseEntity<?> updateWorkflowConfig(
            @PathVariable UUID id,
            @Valid @RequestBody CheckPaymentWorkflowConfigRequest request) {
        try {
            log.info("Updating workflow configuration: {}", id);
            CheckPaymentWorkflowConfigDTO config = workflowConfigService.updateWorkflowConfig(id, request);
            return ResponseEntity.ok(config);
        } catch (IllegalArgumentException e) {
            log.error("Failed to update workflow configuration: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErrorResponse(e.getMessage()));
        } catch (Exception e) {
            log.error("Unexpected error updating workflow configuration", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse("Failed to update workflow configuration"));
        }
    }

    /**
     * Gets workflow configuration by ID.
     *
     * GET /api/v1/config/check-payment-workflow/{id}
     */
    @GetMapping("/{id}")
    public ResponseEntity<?> getWorkflowConfig(@PathVariable UUID id) {
        try {
            CheckPaymentWorkflowConfigDTO config = workflowConfigService.getWorkflowConfig(id);
            return ResponseEntity.ok(config);
        } catch (IllegalArgumentException e) {
            log.error("Workflow configuration not found: {}", id);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErrorResponse(e.getMessage()));
        } catch (Exception e) {
            log.error("Unexpected error retrieving workflow configuration", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse("Failed to retrieve workflow configuration"));
        }
    }

    /**
     * Gets workflow configuration by threshold ID.
     *
     * GET /api/v1/config/check-payment-workflow/threshold/{thresholdId}
     */
    @GetMapping("/threshold/{thresholdId}")
    public ResponseEntity<?> getWorkflowConfigByThreshold(@PathVariable UUID thresholdId) {
        try {
            return workflowConfigService.getWorkflowConfigByThreshold(thresholdId)
                    .map(ResponseEntity::ok)
                    .orElse(ResponseEntity.notFound().build());
        } catch (Exception e) {
            log.error("Unexpected error retrieving workflow configuration for threshold", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse("Failed to retrieve workflow configuration"));
        }
    }

    /**
     * Gets all active workflow configurations.
     *
     * GET /api/v1/config/check-payment-workflow/active
     */
    @GetMapping("/active")
    public ResponseEntity<?> getAllActiveConfigurations() {
        try {
            List<CheckPaymentWorkflowConfigDTO> configs = workflowConfigService.getAllActiveConfigurations();
            return ResponseEntity.ok(configs);
        } catch (Exception e) {
            log.error("Unexpected error retrieving active configurations", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse("Failed to retrieve configurations"));
        }
    }

    /**
     * Gets all workflow configurations.
     *
     * GET /api/v1/config/check-payment-workflow
     */
    @GetMapping
    public ResponseEntity<?> getAllConfigurations() {
        try {
            List<CheckPaymentWorkflowConfigDTO> configs = workflowConfigService.getAllConfigurations();
            return ResponseEntity.ok(configs);
        } catch (Exception e) {
            log.error("Unexpected error retrieving configurations", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse("Failed to retrieve configurations"));
        }
    }

    /**
     * Deletes a workflow configuration.
     *
     * DELETE /api/v1/config/check-payment-workflow/{id}
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteWorkflowConfig(@PathVariable UUID id) {
        try {
            log.info("Deleting workflow configuration: {}", id);
            workflowConfigService.deleteWorkflowConfig(id);
            return ResponseEntity.noContent().build();
        } catch (IllegalArgumentException e) {
            log.error("Workflow configuration not found: {}", id);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErrorResponse(e.getMessage()));
        } catch (Exception e) {
            log.error("Unexpected error deleting workflow configuration", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse("Failed to delete workflow configuration"));
        }
    }

    /**
     * Error response model.
     */
    private record ErrorResponse(String error) {}
}

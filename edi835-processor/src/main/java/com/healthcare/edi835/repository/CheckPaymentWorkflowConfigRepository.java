package com.healthcare.edi835.repository;

import com.healthcare.edi835.entity.CheckPaymentWorkflowConfig;
import com.healthcare.edi835.entity.EdiGenerationThreshold;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for Check Payment Workflow Configuration operations.
 */
@Repository
public interface CheckPaymentWorkflowConfigRepository extends JpaRepository<CheckPaymentWorkflowConfig, UUID> {

    /**
     * Finds workflow configuration by linked threshold ID.
     *
     * @param threshold the threshold entity
     * @return Optional workflow configuration
     */
    Optional<CheckPaymentWorkflowConfig> findByLinkedThreshold(EdiGenerationThreshold threshold);

    /**
     * Finds workflow configuration by threshold ID.
     *
     * @param thresholdId the threshold ID
     * @return Optional workflow configuration
     */
    @Query("SELECT w FROM CheckPaymentWorkflowConfig w WHERE w.linkedThreshold.id = :thresholdId")
    Optional<CheckPaymentWorkflowConfig> findByThresholdId(@Param("thresholdId") UUID thresholdId);

    /**
     * Finds all active workflow configurations.
     *
     * @return List of active configurations
     */
    List<CheckPaymentWorkflowConfig> findByIsActiveTrue();

    /**
     * Finds workflow configurations by workflow mode.
     *
     * @param workflowMode the workflow mode
     * @return List of configurations with the specified workflow mode
     */
    List<CheckPaymentWorkflowConfig> findByWorkflowMode(CheckPaymentWorkflowConfig.WorkflowMode workflowMode);

    /**
     * Finds workflow configurations by assignment mode.
     *
     * @param assignmentMode the assignment mode
     * @return List of configurations with the specified assignment mode
     */
    List<CheckPaymentWorkflowConfig> findByAssignmentMode(CheckPaymentWorkflowConfig.AssignmentMode assignmentMode);

    /**
     * Finds all configurations requiring check payment.
     *
     * @return List of configurations where workflow mode is not NONE
     */
    @Query("SELECT w FROM CheckPaymentWorkflowConfig w WHERE w.workflowMode <> 'NONE' AND w.isActive = true")
    List<CheckPaymentWorkflowConfig> findAllRequiringCheckPayment();

    /**
     * Finds all configurations using combined workflow.
     *
     * @return List of configurations where workflow mode is COMBINED
     */
    @Query("SELECT w FROM CheckPaymentWorkflowConfig w WHERE w.workflowMode = 'COMBINED' AND w.isActive = true")
    List<CheckPaymentWorkflowConfig> findAllCombinedWorkflow();

    /**
     * Finds all configurations using separate workflow.
     *
     * @return List of configurations where workflow mode is SEPARATE
     */
    @Query("SELECT w FROM CheckPaymentWorkflowConfig w WHERE w.workflowMode = 'SEPARATE' AND w.isActive = true")
    List<CheckPaymentWorkflowConfig> findAllSeparateWorkflow();

    /**
     * Checks if a threshold has workflow configuration.
     *
     * @param thresholdId the threshold ID
     * @return true if configuration exists
     */
    @Query("SELECT COUNT(w) > 0 FROM CheckPaymentWorkflowConfig w WHERE w.linkedThreshold.id = :thresholdId")
    boolean existsByThresholdId(@Param("thresholdId") UUID thresholdId);

    /**
     * Deletes workflow configuration by threshold ID.
     *
     * @param thresholdId the threshold ID
     */
    void deleteByLinkedThresholdId(UUID thresholdId);
}

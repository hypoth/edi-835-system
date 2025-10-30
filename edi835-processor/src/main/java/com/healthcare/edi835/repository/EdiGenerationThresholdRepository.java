package com.healthcare.edi835.repository;

import com.healthcare.edi835.entity.EdiGenerationThreshold;
import com.healthcare.edi835.entity.EdiBucketingRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for EdiGenerationThreshold entity.
 */
@Repository
public interface EdiGenerationThresholdRepository extends JpaRepository<EdiGenerationThreshold, UUID> {

    /**
     * Finds thresholds for a specific bucketing rule.
     */
    List<EdiGenerationThreshold> findByLinkedBucketingRuleAndIsActiveTrue(EdiBucketingRule rule);

    /**
     * Finds thresholds for a specific bucketing rule by rule ID.
     * This method queries by ID rather than entity reference, avoiding entity identity comparison issues.
     *
     * @param ruleId the ID of the bucketing rule
     * @return list of active thresholds for the rule
     */
    @Query("SELECT t FROM EdiGenerationThreshold t " +
           "WHERE t.linkedBucketingRule.id = :ruleId AND t.isActive = true")
    List<EdiGenerationThreshold> findByLinkedBucketingRuleIdAndIsActiveTrue(@Param("ruleId") UUID ruleId);

    /**
     * Finds active thresholds by type.
     */
    List<EdiGenerationThreshold> findByThresholdTypeAndIsActiveTrue(EdiGenerationThreshold.ThresholdType type);

    /**
     * Finds all active thresholds.
     */
    List<EdiGenerationThreshold> findByIsActiveTrue();

    /**
     * Finds threshold by name.
     */
    Optional<EdiGenerationThreshold> findByThresholdName(String thresholdName);

    /**
     * Checks if threshold name already exists.
     */
    boolean existsByThresholdName(String thresholdName);
}

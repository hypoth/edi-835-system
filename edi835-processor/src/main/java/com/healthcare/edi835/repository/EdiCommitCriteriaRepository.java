package com.healthcare.edi835.repository;

import com.healthcare.edi835.entity.EdiCommitCriteria;
import com.healthcare.edi835.entity.EdiBucketingRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for EdiCommitCriteria entity.
 */
@Repository
public interface EdiCommitCriteriaRepository extends JpaRepository<EdiCommitCriteria, UUID> {

    /**
     * Finds commit criteria for a specific bucketing rule.
     * Returns a list to handle cases where multiple active criteria exist (configuration error).
     * Should return at most one result under normal circumstances.
     */
    List<EdiCommitCriteria> findByLinkedBucketingRuleAndIsActiveTrue(EdiBucketingRule rule);

    /**
     * Finds criteria by commit mode.
     */
    List<EdiCommitCriteria> findByCommitModeAndIsActiveTrue(EdiCommitCriteria.CommitMode commitMode);

    /**
     * Finds all active criteria.
     */
    List<EdiCommitCriteria> findByIsActiveTrue();

    /**
     * Finds criteria by name.
     */
    Optional<EdiCommitCriteria> findByCriteriaName(String criteriaName);

    /**
     * Checks if criteria name already exists.
     */
    boolean existsByCriteriaName(String criteriaName);

    /**
     * Finds commit criteria by bucketing rule ID.
     * Returns first active criteria found.
     */
    @Query("SELECT c FROM EdiCommitCriteria c " +
           "WHERE c.linkedBucketingRule.id = :ruleId AND c.isActive = true " +
           "ORDER BY c.createdAt ASC")
    Optional<EdiCommitCriteria> findByLinkedBucketingRuleId(@Param("ruleId") UUID ruleId);
}

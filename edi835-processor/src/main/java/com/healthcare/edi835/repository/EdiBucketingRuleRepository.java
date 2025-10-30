package com.healthcare.edi835.repository;

import com.healthcare.edi835.entity.EdiBucketingRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for EdiBucketingRule entity.
 */
@Repository
public interface EdiBucketingRuleRepository extends JpaRepository<EdiBucketingRule, UUID> {

    /**
     * Finds rule by name.
     */
    Optional<EdiBucketingRule> findByRuleName(String ruleName);

    /**
     * Finds all active rules ordered by priority.
     */
    @Query("SELECT r FROM EdiBucketingRule r WHERE r.isActive = true ORDER BY r.priority DESC")
    List<EdiBucketingRule> findActiveRulesByPriority();

    /**
     * Finds rules by type and active status.
     */
    List<EdiBucketingRule> findByRuleTypeAndIsActiveTrue(EdiBucketingRule.RuleType ruleType);

    /**
     * Finds the highest priority active rule.
     */
    @Query("SELECT r FROM EdiBucketingRule r WHERE r.isActive = true ORDER BY r.priority DESC LIMIT 1")
    Optional<EdiBucketingRule> findHighestPriorityRule();

    /**
     * Checks if rule name already exists.
     */
    boolean existsByRuleName(String ruleName);
}

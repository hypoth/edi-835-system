package com.healthcare.edi835.repository;

import com.healthcare.edi835.entity.EdiFileNamingTemplate;
import com.healthcare.edi835.entity.EdiBucketingRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for EdiFileNamingTemplate entity.
 */
@Repository
public interface EdiFileNamingTemplateRepository extends JpaRepository<EdiFileNamingTemplate, UUID> {

    /**
     * Finds template by name.
     */
    Optional<EdiFileNamingTemplate> findByTemplateName(String templateName);

    /**
     * Finds the default template.
     */
    @Query("SELECT t FROM EdiFileNamingTemplate t WHERE t.isDefault = true")
    Optional<EdiFileNamingTemplate> findDefaultTemplate();

    /**
     * Finds templates for a specific bucketing rule.
     */
    List<EdiFileNamingTemplate> findByLinkedBucketingRule(EdiBucketingRule rule);

    /**
     * Finds all default templates (should ideally be one).
     */
    List<EdiFileNamingTemplate> findByIsDefaultTrue();

    /**
     * Checks if template name already exists.
     */
    boolean existsByTemplateName(String templateName);
}

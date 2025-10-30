package com.healthcare.edi835.repository;

import com.healthcare.edi835.entity.AdjustmentCodeMapping;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for AdjustmentCodeMapping entity.
 */
@Repository
public interface AdjustmentCodeMappingRepository extends JpaRepository<AdjustmentCodeMapping, UUID> {

    /**
     * Finds adjustment code by type and code.
     */
    Optional<AdjustmentCodeMapping> findByCodeTypeAndCode(AdjustmentCodeMapping.CodeType codeType, String code);

    /**
     * Finds all codes by type.
     */
    List<AdjustmentCodeMapping> findByCodeTypeAndIsActiveTrue(AdjustmentCodeMapping.CodeType codeType);

    /**
     * Finds all CARC codes.
     */
    default List<AdjustmentCodeMapping> findActiveCarcCodes() {
        return findByCodeTypeAndIsActiveTrue(AdjustmentCodeMapping.CodeType.CARC);
    }

    /**
     * Finds all RARC codes.
     */
    default List<AdjustmentCodeMapping> findActiveRarcCodes() {
        return findByCodeTypeAndIsActiveTrue(AdjustmentCodeMapping.CodeType.RARC);
    }

    /**
     * Finds all active adjustment codes.
     */
    List<AdjustmentCodeMapping> findByIsActiveTrue();
}

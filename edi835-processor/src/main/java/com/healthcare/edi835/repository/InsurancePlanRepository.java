package com.healthcare.edi835.repository;

import com.healthcare.edi835.entity.InsurancePlan;
import com.healthcare.edi835.entity.Payer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for InsurancePlan entity.
 */
@Repository
public interface InsurancePlanRepository extends JpaRepository<InsurancePlan, UUID> {

    /**
     * Finds insurance plan by BIN and PCN.
     */
    Optional<InsurancePlan> findByBinNumberAndPcnNumber(String binNumber, String pcnNumber);

    /**
     * Finds insurance plans by BIN number.
     */
    List<InsurancePlan> findByBinNumber(String binNumber);

    /**
     * Finds insurance plans by payer.
     */
    List<InsurancePlan> findByPayerAndIsActiveTrue(Payer payer);

    /**
     * Finds all active insurance plans.
     */
    List<InsurancePlan> findByIsActiveTrue();

    /**
     * Searches plans by name.
     */
    @Query("SELECT i FROM InsurancePlan i WHERE LOWER(i.planName) LIKE LOWER(CONCAT('%', :name, '%')) AND i.isActive = true")
    List<InsurancePlan> searchByPlanName(@Param("name") String name);

    /**
     * Finds insurance plan by BIN and PCN (alias for findByBinNumberAndPcnNumber).
     */
    default Optional<InsurancePlan> findByBinAndPcn(String binNumber, String pcnNumber) {
        return findByBinNumberAndPcnNumber(binNumber, pcnNumber);
    }
}

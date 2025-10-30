package com.healthcare.edi835.repository;

import com.healthcare.edi835.entity.Payee;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for Payee entity.
 */
@Repository
public interface PayeeRepository extends JpaRepository<Payee, UUID> {

    /**
     * Finds payee by payee ID.
     */
    Optional<Payee> findByPayeeId(String payeeId);

    /**
     * Finds all active payees.
     */
    List<Payee> findByIsActiveTrue();

    /**
     * Finds payee by NPI.
     */
    Optional<Payee> findByNpi(String npi);

    /**
     * Checks if payee exists by payee ID.
     */
    boolean existsByPayeeId(String payeeId);

    /**
     * Finds payees by name (case-insensitive partial match).
     */
    @Query("SELECT p FROM Payee p WHERE LOWER(p.payeeName) LIKE LOWER(CONCAT('%', :name, '%')) AND p.isActive = true")
    List<Payee> searchByName(String name);

    /**
     * Finds all active payees (alias for findByIsActiveTrue).
     */
    default List<Payee> findActivePayees() {
        return findByIsActiveTrue();
    }
}

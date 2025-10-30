package com.healthcare.edi835.repository;

import com.healthcare.edi835.entity.Payer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for Payer entity.
 */
@Repository
public interface PayerRepository extends JpaRepository<Payer, UUID> {

    /**
     * Finds payer by payer ID.
     */
    Optional<Payer> findByPayerId(String payerId);

    /**
     * Finds all active payers.
     */
    List<Payer> findByIsActiveTrue();

    /**
     * Checks if payer exists by payer ID.
     */
    boolean existsByPayerId(String payerId);

    /**
     * Finds payers by name (case-insensitive partial match).
     */
    @Query("SELECT p FROM Payer p WHERE LOWER(p.payerName) LIKE LOWER(CONCAT('%', :name, '%')) AND p.isActive = true")
    List<Payer> searchByName(String name);

    /**
     * Searches payers by query string (searches payer ID and name).
     */
    @Query("SELECT p FROM Payer p WHERE " +
           "LOWER(p.payerId) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
           "LOWER(p.payerName) LIKE LOWER(CONCAT('%', :query, '%'))")
    List<Payer> searchPayers(String query);

    /**
     * Finds active payers with SFTP configuration.
     */
    @Query("SELECT p FROM Payer p WHERE p.isActive = true AND p.sftpHost IS NOT NULL")
    List<Payer> findActivePayersWithSftpConfig();
}

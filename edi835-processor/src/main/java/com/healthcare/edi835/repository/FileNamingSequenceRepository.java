package com.healthcare.edi835.repository;

import com.healthcare.edi835.entity.FileNamingSequence;
import com.healthcare.edi835.entity.EdiFileNamingTemplate;
import com.healthcare.edi835.entity.Payer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for FileNamingSequence entity.
 * Uses pessimistic locking for thread-safe sequence management.
 */
@Repository
public interface FileNamingSequenceRepository extends JpaRepository<FileNamingSequence, UUID> {

    /**
     * Finds sequence for template and payer with pessimistic write lock.
     * This ensures thread-safe sequence increment.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM FileNamingSequence s WHERE s.template = :template AND s.payer = :payer")
    Optional<FileNamingSequence> findByTemplateAndPayerForUpdate(@Param("template") EdiFileNamingTemplate template,
                                                                   @Param("payer") Payer payer);

    /**
     * Finds sequence for template and payer without locking (for read-only operations).
     */
    Optional<FileNamingSequence> findByTemplateAndPayer(EdiFileNamingTemplate template, Payer payer);
}

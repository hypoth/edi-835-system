package com.healthcare.edi835.repository.cosmos;

import com.azure.spring.data.cosmos.repository.CosmosRepository;
import com.azure.spring.data.cosmos.repository.Query;
import com.healthcare.edi835.model.Claim;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Cosmos DB repository for Claim entity.
 */
@Repository
public interface ClaimRepository extends CosmosRepository<Claim, String> {

    /**
     * Finds claims by payer ID.
     */
    List<Claim> findByPayerId(String payerId);

    /**
     * Finds claims by payee ID.
     */
    List<Claim> findByPayeeId(String payeeId);

    /**
     * Finds claims by status.
     */
    List<Claim> findByStatus(Claim.ClaimStatus status);

    /**
     * Finds processed claims by payer and payee.
     */
    @Query("SELECT * FROM c WHERE c.payerId = @payerId AND c.payeeId = @payeeId AND (c.status = 'PROCESSED' OR c.status = 'PAID')")
    List<Claim> findProcessedClaimsByPayerAndPayee(String payerId, String payeeId);

    /**
     * Finds claims by BIN and PCN.
     */
    @Query("SELECT * FROM c WHERE c.binNumber = @binNumber AND c.pcnNumber = @pcnNumber AND (c.status = 'PROCESSED' OR c.status = 'PAID')")
    List<Claim> findProcessedClaimsByBinPcn(String binNumber, String pcnNumber);
}

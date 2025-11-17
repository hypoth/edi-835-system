-- Migration: Create NCPDP Raw Claims Table
-- Purpose: Store raw NCPDP D.0 pharmacy claims before processing
-- This table is monitored by NcpdpChangeFeedProcessor for automatic parsing and processing

-- =============================================================================
-- NCPDP RAW CLAIMS TABLE
-- =============================================================================

CREATE TABLE ncpdp_raw_claims (
    id VARCHAR(50) PRIMARY KEY,

    -- Indexing fields (extracted for quick lookups)
    payer_id VARCHAR(50) NOT NULL,
    pharmacy_id VARCHAR(50),
    transaction_id VARCHAR(50),

    -- Raw NCPDP D.0 transaction content (STX -> SE block)
    raw_content TEXT NOT NULL,

    -- Transaction metadata
    transaction_type VARCHAR(10),      -- B1, B2, B3, etc.
    service_date DATE,
    patient_id VARCHAR(50),
    prescription_number VARCHAR(50),

    -- Processing status
    status VARCHAR(20) DEFAULT 'PENDING' NOT NULL,

    -- Timestamps
    created_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    processing_started_date TIMESTAMP,
    processed_date TIMESTAMP,

    -- Link to processed claim
    claim_id VARCHAR(50),  -- References processed Claim.id

    -- Error tracking
    error_message TEXT,
    retry_count INTEGER DEFAULT 0,

    -- Audit fields
    created_by VARCHAR(100),

    -- Status constraint
    CONSTRAINT chk_ncpdp_status CHECK (status IN ('PENDING', 'PROCESSING', 'PROCESSED', 'FAILED'))
);

-- Indexes for performance
CREATE INDEX idx_ncpdp_status ON ncpdp_raw_claims(status);
CREATE INDEX idx_ncpdp_payer ON ncpdp_raw_claims(payer_id);
CREATE INDEX idx_ncpdp_pharmacy ON ncpdp_raw_claims(pharmacy_id);
CREATE INDEX idx_ncpdp_created ON ncpdp_raw_claims(created_date);
CREATE INDEX idx_ncpdp_claim_id ON ncpdp_raw_claims(claim_id);
CREATE INDEX idx_ncpdp_service_date ON ncpdp_raw_claims(service_date);
CREATE INDEX idx_ncpdp_prescription ON ncpdp_raw_claims(prescription_number);

-- Composite index for pending claims query (most common)
CREATE INDEX idx_ncpdp_pending_created ON ncpdp_raw_claims(status, created_date)
    WHERE status = 'PENDING';

-- Index for finding stuck processing claims
CREATE INDEX idx_ncpdp_stuck_processing ON ncpdp_raw_claims(status, processing_started_date)
    WHERE status = 'PROCESSING';

-- =============================================================================
-- NCPDP PROCESSING LOG TABLE
-- =============================================================================

CREATE TABLE ncpdp_processing_log (
    id BIGSERIAL PRIMARY KEY,
    ncpdp_claim_id VARCHAR(50) NOT NULL,
    processing_stage VARCHAR(50),      -- PARSE, MAP, PROCESS, COMPLETE
    status VARCHAR(20),                -- SUCCESS, ERROR, WARNING
    message TEXT,
    details JSONB,                     -- Additional structured data
    created_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,

    FOREIGN KEY (ncpdp_claim_id) REFERENCES ncpdp_raw_claims(id) ON DELETE CASCADE
);

CREATE INDEX idx_ncpdp_log_claim ON ncpdp_processing_log(ncpdp_claim_id);
CREATE INDEX idx_ncpdp_log_status ON ncpdp_processing_log(status);
CREATE INDEX idx_ncpdp_log_created ON ncpdp_processing_log(created_date DESC);

-- =============================================================================
-- COMMENTS FOR DOCUMENTATION
-- =============================================================================

COMMENT ON TABLE ncpdp_raw_claims IS
'Stores raw NCPDP D.0 pharmacy prescription claims. Change feed processor monitors for PENDING status.';

COMMENT ON COLUMN ncpdp_raw_claims.raw_content IS
'Complete NCPDP D.0 transaction text from STX to SE segment';

COMMENT ON COLUMN ncpdp_raw_claims.status IS
'Processing status: PENDING (awaiting processing), PROCESSING (currently being processed), PROCESSED (successfully converted to Claim), FAILED (processing error)';

COMMENT ON COLUMN ncpdp_raw_claims.processing_started_date IS
'Timestamp when status changed to PROCESSING. Used to detect stuck claims that remain in processing too long.';

COMMENT ON COLUMN ncpdp_raw_claims.claim_id IS
'Links to the processed Claim.id after successful conversion';

COMMENT ON TABLE ncpdp_processing_log IS
'Audit log of NCPDP claim processing stages for debugging and monitoring';

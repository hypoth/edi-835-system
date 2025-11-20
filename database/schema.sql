-- PostgreSQL Database Schema for EDI 835 Configuration and Audit
--
-- SCHEMA MIGRATION HISTORY:
-- - Base schema: Core tables for EDI 835 processing
-- - Migration 001: SFTP fields added to payers table (INCORPORATED)
-- - Migration 002: NCPDP raw claims tables added (INCORPORATED)
-- - 2025-11-20: Added MISSING_CONFIGURATION status to edi_file_buckets
--
-- NOTE: This schema is kept in sync with SQLite schema at:
--       edi835-processor/src/main/resources/db/sqlite/admin-schema.sql
--

-- =============================================================================
-- CONFIGURATION TABLES
-- =============================================================================

-- Payers table (with SFTP configuration fields)
CREATE TABLE payers (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    payer_id VARCHAR(50) UNIQUE NOT NULL,
    payer_name VARCHAR(255) NOT NULL,
    isa_qualifier VARCHAR(2) DEFAULT 'ZZ',
    isa_sender_id VARCHAR(15) NOT NULL,
    gs_application_sender_id VARCHAR(15),
    address_street VARCHAR(255),
    address_city VARCHAR(100),
    address_state VARCHAR(2),
    address_zip VARCHAR(10),

    -- SFTP configuration fields
    sftp_host VARCHAR(255),
    sftp_port INTEGER DEFAULT 22,
    sftp_username VARCHAR(100),
    sftp_password TEXT,  -- Should be encrypted at application layer
    sftp_path VARCHAR(500),
    requires_special_handling BOOLEAN DEFAULT false,

    is_active BOOLEAN DEFAULT true,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(100),
    updated_by VARCHAR(100)
);

CREATE INDEX idx_payers_payer_id ON payers(payer_id);
CREATE INDEX idx_payers_active ON payers(is_active);

-- Payees table
CREATE TABLE payees (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    payee_id VARCHAR(50) UNIQUE NOT NULL,
    payee_name VARCHAR(255) NOT NULL,
    npi VARCHAR(10),
    tax_id VARCHAR(20),
    address_street VARCHAR(255),
    address_city VARCHAR(100),
    address_state VARCHAR(2),
    address_zip VARCHAR(10),
    is_active BOOLEAN DEFAULT true,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(100),
    updated_by VARCHAR(100)
);

CREATE INDEX idx_payees_payee_id ON payees(payee_id);
CREATE INDEX idx_payees_active ON payees(is_active);

-- Insurance plans (for BIN/PCN reference)
CREATE TABLE insurance_plans (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    bin_number VARCHAR(20) NOT NULL,
    pcn_number VARCHAR(20),
    plan_name VARCHAR(255),
    payer_id UUID REFERENCES payers(id),
    is_active BOOLEAN DEFAULT true,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_insurance_bin_pcn ON insurance_plans(bin_number, pcn_number);

-- EDI bucketing rules
CREATE TABLE edi_bucketing_rules (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    rule_name VARCHAR(255) UNIQUE NOT NULL,
    rule_type VARCHAR(50) NOT NULL CHECK (rule_type IN ('PAYER_PAYEE', 'BIN_PCN', 'CUSTOM')),
    grouping_expression TEXT,
    priority INTEGER DEFAULT 0,
    linked_payer_id UUID REFERENCES payers(id),
    linked_payee_id UUID REFERENCES payees(id),
    description TEXT,
    is_active BOOLEAN DEFAULT true,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(100),
    updated_by VARCHAR(100)
);

CREATE INDEX idx_bucketing_rules_active ON edi_bucketing_rules(is_active);
CREATE INDEX idx_bucketing_rules_priority ON edi_bucketing_rules(priority DESC);

-- EDI generation thresholds
CREATE TABLE edi_generation_thresholds (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    threshold_name VARCHAR(255) NOT NULL,
    threshold_type VARCHAR(50) NOT NULL CHECK (threshold_type IN ('CLAIM_COUNT', 'AMOUNT', 'TIME', 'HYBRID')),
    max_claims INTEGER,
    max_amount DECIMAL(15,2),
    time_duration VARCHAR(20) CHECK (time_duration IN ('DAILY', 'WEEKLY', 'BIWEEKLY', 'MONTHLY')),
    generation_schedule VARCHAR(100), -- Cron expression or time specification
    linked_bucketing_rule_id UUID REFERENCES edi_bucketing_rules(id) ON DELETE CASCADE,
    is_active BOOLEAN DEFAULT true,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(100),
    updated_by VARCHAR(100)
);

CREATE INDEX idx_gen_thresholds_rule ON edi_generation_thresholds(linked_bucketing_rule_id);

-- EDI commit criteria
CREATE TABLE edi_commit_criteria (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    criteria_name VARCHAR(255) NOT NULL,
    commit_mode VARCHAR(20) NOT NULL CHECK (commit_mode IN ('AUTO', 'MANUAL', 'HYBRID')),
    auto_commit_threshold INTEGER,
    manual_approval_threshold INTEGER,
    approval_required_roles TEXT[], -- Array of role names
    override_permissions TEXT[],
    linked_bucketing_rule_id UUID REFERENCES edi_bucketing_rules(id) ON DELETE CASCADE,
    is_active BOOLEAN DEFAULT true,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(100),
    updated_by VARCHAR(100)
);

CREATE INDEX idx_commit_criteria_rule ON edi_commit_criteria(linked_bucketing_rule_id);

-- EDI file naming templates
CREATE TABLE edi_file_naming_templates (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    template_name VARCHAR(255) UNIQUE NOT NULL,
    template_pattern VARCHAR(500) NOT NULL,
    date_format VARCHAR(50) DEFAULT 'yyyyMMdd',
    time_format VARCHAR(50) DEFAULT 'HHmmss',
    sequence_padding INTEGER DEFAULT 4,
    case_conversion VARCHAR(20) DEFAULT 'UPPER' CHECK (case_conversion IN ('UPPER', 'LOWER', 'NONE')),
    separator_type VARCHAR(10) DEFAULT '_',
    is_default BOOLEAN DEFAULT false,
    linked_bucketing_rule_id UUID REFERENCES edi_bucketing_rules(id),
    version INTEGER DEFAULT 1,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(100),
    updated_by VARCHAR(100)
);

CREATE INDEX idx_naming_templates_default ON edi_file_naming_templates(is_default);
CREATE INDEX idx_naming_templates_rule ON edi_file_naming_templates(linked_bucketing_rule_id);

-- File naming sequence tracker
CREATE TABLE file_naming_sequence (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    template_id UUID REFERENCES edi_file_naming_templates(id) ON DELETE CASCADE,
    payer_id UUID REFERENCES payers(id),
    current_sequence INTEGER DEFAULT 0,
    last_reset_date DATE DEFAULT CURRENT_DATE,
    reset_frequency VARCHAR(20) CHECK (reset_frequency IN ('DAILY', 'MONTHLY', 'YEARLY', 'NEVER')),
    UNIQUE(template_id, payer_id)
);

-- Payment methods
CREATE TABLE payment_methods (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    method_type VARCHAR(20) NOT NULL CHECK (method_type IN ('EFT', 'CHECK')),
    routing_number VARCHAR(9),
    account_number_encrypted TEXT,
    bank_name VARCHAR(255),
    payer_id UUID REFERENCES payers(id),
    is_active BOOLEAN DEFAULT true,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Adjustment code mappings (CARC/RARC)
CREATE TABLE adjustment_code_mapping (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code_type VARCHAR(10) NOT NULL CHECK (code_type IN ('CARC', 'RARC')),
    code VARCHAR(10) NOT NULL,
    description TEXT NOT NULL,
    group_code VARCHAR(2),
    is_active BOOLEAN DEFAULT true,
    UNIQUE(code_type, code)
);

-- =============================================================================
-- OPERATIONAL TABLES
-- =============================================================================

-- Active EDI file buckets
CREATE TABLE edi_file_buckets (
    bucket_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    status VARCHAR(50) NOT NULL DEFAULT 'ACCUMULATING'
        CHECK (status IN ('ACCUMULATING', 'PENDING_APPROVAL', 'GENERATING', 'COMPLETED', 'FAILED', 'MISSING_CONFIGURATION')),
    bucketing_rule_id UUID REFERENCES edi_bucketing_rules(id),
    bucketing_rule_name VARCHAR(255),
    payer_id VARCHAR(50) NOT NULL,
    payer_name VARCHAR(255),
    payee_id VARCHAR(50) NOT NULL,
    payee_name VARCHAR(255),
    bin_number VARCHAR(20),
    pcn_number VARCHAR(20),
    claim_count INTEGER DEFAULT 0,
    total_amount DECIMAL(15,2) DEFAULT 0,
    rejection_count INTEGER DEFAULT 0,
    file_naming_template_id UUID REFERENCES edi_file_naming_templates(id),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    last_updated TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    awaiting_approval_since TIMESTAMP,
    approved_by VARCHAR(100),
    approved_at TIMESTAMP,
    generation_started_at TIMESTAMP,
    generation_completed_at TIMESTAMP
);

CREATE INDEX idx_buckets_status ON edi_file_buckets(status);
CREATE INDEX idx_buckets_payer_payee ON edi_file_buckets(payer_id, payee_id);
CREATE INDEX idx_buckets_pending ON edi_file_buckets(status) WHERE status = 'PENDING_APPROVAL';

-- Bucket approval log
CREATE TABLE bucket_approval_log (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    bucket_id UUID REFERENCES edi_file_buckets(bucket_id) ON DELETE CASCADE,
    action VARCHAR(20) NOT NULL CHECK (action IN ('APPROVE', 'REJECT', 'OVERRIDE')),
    approved_by VARCHAR(100) NOT NULL,
    comments TEXT,
    scheduled_generation_time TIMESTAMP,
    approved_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_approval_log_bucket ON bucket_approval_log(bucket_id);

-- Claim processing log
CREATE TABLE claim_processing_log (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    claim_id VARCHAR(50) NOT NULL,
    bucket_id UUID REFERENCES edi_file_buckets(bucket_id),
    payer_id VARCHAR(50),
    payee_id VARCHAR(50),
    claim_amount DECIMAL(15,2),
    paid_amount DECIMAL(15,2),
    adjustment_amount DECIMAL(15,2),
    status VARCHAR(50),
    rejection_reason VARCHAR(500),
    processed_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_claim_log_bucket ON claim_processing_log(bucket_id);
CREATE INDEX idx_claim_log_claim ON claim_processing_log(claim_id);

-- File generation history
CREATE TABLE file_generation_history (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    bucket_id UUID REFERENCES edi_file_buckets(bucket_id),
    generated_file_name VARCHAR(500) NOT NULL,
    file_path TEXT,
    file_size_bytes BIGINT,
    claim_count INTEGER NOT NULL,
    total_amount DECIMAL(15,2) NOT NULL,
    generated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    generated_by VARCHAR(100),
    delivery_status VARCHAR(50) DEFAULT 'PENDING' 
        CHECK (delivery_status IN ('PENDING', 'DELIVERED', 'FAILED', 'RETRY')),
    delivered_at TIMESTAMP,
    delivery_attempt_count INTEGER DEFAULT 0,
    error_message TEXT
);

CREATE INDEX idx_file_history_bucket ON file_generation_history(bucket_id);
CREATE INDEX idx_file_history_status ON file_generation_history(delivery_status);
CREATE INDEX idx_file_history_date ON file_generation_history(generated_at DESC);

-- =============================================================================
-- AUDIT TRIGGERS
-- =============================================================================

-- Function to update updated_at timestamp
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ language 'plpgsql';

-- Apply to all configuration tables
CREATE TRIGGER update_payers_updated_at BEFORE UPDATE ON payers
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_payees_updated_at BEFORE UPDATE ON payees
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_bucketing_rules_updated_at BEFORE UPDATE ON edi_bucketing_rules
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_thresholds_updated_at BEFORE UPDATE ON edi_generation_thresholds
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_commit_criteria_updated_at BEFORE UPDATE ON edi_commit_criteria
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_naming_templates_updated_at BEFORE UPDATE ON edi_file_naming_templates
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- =============================================================================
-- NCPDP RAW CLAIMS TABLES (Migration 002)
-- =============================================================================
-- Purpose: Store raw NCPDP D.0 pharmacy claims before processing
-- This table is monitored by NcpdpChangeFeedProcessor for automatic parsing and processing

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

-- NCPDP Processing Log Table
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

-- Comments for documentation
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

-- =============================================================================
-- SAMPLE DATA (Optional - for testing)
-- =============================================================================

-- Insert sample adjustment codes (CARC)
INSERT INTO adjustment_code_mapping (code_type, code, description, group_code) VALUES
('CARC', '1', 'Deductible Amount', 'PR'),
('CARC', '2', 'Coinsurance Amount', 'PR'),
('CARC', '3', 'Co-payment Amount', 'PR'),
('CARC', '45', 'Charge exceeds fee schedule/maximum allowable', 'CO'),
('CARC', '97', 'Payment adjusted because the benefit for this service is included in another service', 'CO');
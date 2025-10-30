-- ===========================================================================
-- SQLite Admin Portal Schema
-- Adapted from PostgreSQL schema for local development
-- Includes all configuration and operational tables for admin portal
-- ===========================================================================

-- Pragmas for SQLite configuration
PRAGMA foreign_keys = ON;
PRAGMA journal_mode = WAL;
PRAGMA synchronous = NORMAL;
PRAGMA cache_size = 10000;

-- ===========================================================================
-- CONFIGURATION TABLES
-- ===========================================================================

-- Payers table (with SFTP fields)
CREATE TABLE IF NOT EXISTS payers (
    id TEXT PRIMARY KEY DEFAULT (lower(hex(randomblob(16)))),
    payer_id TEXT UNIQUE NOT NULL,
    payer_name TEXT NOT NULL,
    isa_qualifier TEXT DEFAULT 'ZZ',
    isa_sender_id TEXT NOT NULL,
    gs_application_sender_id TEXT,
    address_street TEXT,
    address_city TEXT,
    address_state TEXT,
    address_zip TEXT,
    -- SFTP configuration fields
    sftp_host TEXT,
    sftp_port INTEGER,
    sftp_username TEXT,
    sftp_password TEXT,  -- Encrypted in application layer
    sftp_path TEXT,
    requires_special_handling INTEGER DEFAULT 0 CHECK(requires_special_handling IN (0, 1)),
    is_active INTEGER DEFAULT 1 CHECK(is_active IN (0, 1)),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    created_by TEXT,
    updated_by TEXT
);

CREATE INDEX IF NOT EXISTS idx_payers_payer_id ON payers(payer_id);
CREATE INDEX IF NOT EXISTS idx_payers_active ON payers(is_active);

-- Payees table
CREATE TABLE IF NOT EXISTS payees (
    id TEXT PRIMARY KEY DEFAULT (lower(hex(randomblob(16)))),
    payee_id TEXT UNIQUE NOT NULL,
    payee_name TEXT NOT NULL,
    npi TEXT,
    tax_id TEXT,
    address_street TEXT,
    address_city TEXT,
    address_state TEXT,
    address_zip TEXT,
    requires_special_handling INTEGER DEFAULT 0 CHECK(requires_special_handling IN (0, 1)),
    is_active INTEGER DEFAULT 1 CHECK(is_active IN (0, 1)),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    created_by TEXT,
    updated_by TEXT
);

CREATE INDEX IF NOT EXISTS idx_payees_payee_id ON payees(payee_id);
CREATE INDEX IF NOT EXISTS idx_payees_active ON payees(is_active);

-- Insurance plans (for BIN/PCN reference)
CREATE TABLE IF NOT EXISTS insurance_plans (
    id TEXT PRIMARY KEY DEFAULT (lower(hex(randomblob(16)))),
    bin_number TEXT NOT NULL,
    pcn_number TEXT,
    plan_name TEXT,
    payer_id TEXT REFERENCES payers(id),
    is_active INTEGER DEFAULT 1 CHECK(is_active IN (0, 1)),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_insurance_bin_pcn ON insurance_plans(bin_number, pcn_number);

-- EDI bucketing rules
CREATE TABLE IF NOT EXISTS edi_bucketing_rules (
    id TEXT PRIMARY KEY DEFAULT (lower(hex(randomblob(16)))),
    rule_name TEXT UNIQUE NOT NULL,
    rule_type TEXT NOT NULL CHECK (rule_type IN ('PAYER_PAYEE', 'BIN_PCN', 'CUSTOM')),
    grouping_expression TEXT,
    priority INTEGER DEFAULT 0,
    linked_payer_id TEXT REFERENCES payers(id),
    linked_payee_id TEXT REFERENCES payees(id),
    description TEXT,
    is_active INTEGER DEFAULT 1 CHECK(is_active IN (0, 1)),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    created_by TEXT,
    updated_by TEXT
);

CREATE INDEX IF NOT EXISTS idx_bucketing_rules_active ON edi_bucketing_rules(is_active);
CREATE INDEX IF NOT EXISTS idx_bucketing_rules_priority ON edi_bucketing_rules(priority DESC);

-- EDI generation thresholds
CREATE TABLE IF NOT EXISTS edi_generation_thresholds (
    id TEXT PRIMARY KEY DEFAULT (lower(hex(randomblob(16)))),
    threshold_name TEXT NOT NULL,
    threshold_type TEXT NOT NULL CHECK (threshold_type IN ('CLAIM_COUNT', 'AMOUNT', 'TIME', 'HYBRID')),
    max_claims INTEGER,
    max_amount REAL,
    time_duration TEXT CHECK (time_duration IN ('DAILY', 'WEEKLY', 'BIWEEKLY', 'MONTHLY')),
    generation_schedule TEXT, -- Cron expression or time specification
    linked_bucketing_rule_id TEXT REFERENCES edi_bucketing_rules(id) ON DELETE CASCADE,
    is_active INTEGER DEFAULT 1 CHECK(is_active IN (0, 1)),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    created_by TEXT,
    updated_by TEXT
);

CREATE INDEX IF NOT EXISTS idx_gen_thresholds_rule ON edi_generation_thresholds(linked_bucketing_rule_id);

-- EDI commit criteria
CREATE TABLE IF NOT EXISTS edi_commit_criteria (
    id TEXT PRIMARY KEY DEFAULT (lower(hex(randomblob(16)))),
    criteria_name TEXT NOT NULL,
    commit_mode TEXT NOT NULL CHECK (commit_mode IN ('AUTO', 'MANUAL', 'HYBRID')),
    auto_commit_threshold INTEGER,
    manual_approval_threshold INTEGER,
    approval_required_roles TEXT, -- JSON array: '["ADMIN", "MANAGER"]'
    override_permissions TEXT, -- JSON array: '["OVERRIDE_THRESHOLD"]'
    linked_bucketing_rule_id TEXT REFERENCES edi_bucketing_rules(id) ON DELETE CASCADE,
    is_active INTEGER DEFAULT 1 CHECK(is_active IN (0, 1)),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    created_by TEXT,
    updated_by TEXT
);

CREATE INDEX IF NOT EXISTS idx_commit_criteria_rule ON edi_commit_criteria(linked_bucketing_rule_id);

-- EDI file naming templates
CREATE TABLE IF NOT EXISTS edi_file_naming_templates (
    id TEXT PRIMARY KEY DEFAULT (lower(hex(randomblob(16)))),
    template_name TEXT UNIQUE NOT NULL,
    template_pattern TEXT NOT NULL,
    date_format TEXT DEFAULT 'yyyyMMdd',
    time_format TEXT DEFAULT 'HHmmss',
    sequence_padding INTEGER DEFAULT 4,
    case_conversion TEXT DEFAULT 'UPPER' CHECK (case_conversion IN ('UPPER', 'LOWER', 'NONE')),
    separator_type TEXT DEFAULT '_',
    is_default INTEGER DEFAULT 0 CHECK(is_default IN (0, 1)),
    linked_bucketing_rule_id TEXT REFERENCES edi_bucketing_rules(id),
    version INTEGER DEFAULT 1,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    created_by TEXT,
    updated_by TEXT
);

CREATE INDEX IF NOT EXISTS idx_naming_templates_default ON edi_file_naming_templates(is_default);
CREATE INDEX IF NOT EXISTS idx_naming_templates_rule ON edi_file_naming_templates(linked_bucketing_rule_id);

-- File naming sequence tracker
CREATE TABLE IF NOT EXISTS file_naming_sequence (
    id TEXT PRIMARY KEY DEFAULT (lower(hex(randomblob(16)))),
    template_id TEXT REFERENCES edi_file_naming_templates(id) ON DELETE CASCADE,
    payer_id TEXT REFERENCES payers(id),
    current_sequence INTEGER DEFAULT 0,
    last_reset_date DATE DEFAULT (date('now')),
    reset_frequency TEXT CHECK (reset_frequency IN ('DAILY', 'MONTHLY', 'YEARLY', 'NEVER')),
    UNIQUE(template_id, payer_id)
);

-- Payment methods
CREATE TABLE IF NOT EXISTS payment_methods (
    id TEXT PRIMARY KEY DEFAULT (lower(hex(randomblob(16)))),
    method_type TEXT NOT NULL CHECK (method_type IN ('EFT', 'CHECK')),
    routing_number TEXT,
    account_number_encrypted TEXT,
    bank_name TEXT,
    payer_id TEXT REFERENCES payers(id),
    is_active INTEGER DEFAULT 1 CHECK(is_active IN (0, 1)),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Adjustment code mappings (CARC/RARC)
CREATE TABLE IF NOT EXISTS adjustment_code_mapping (
    id TEXT PRIMARY KEY DEFAULT (lower(hex(randomblob(16)))),
    code_type TEXT NOT NULL CHECK (code_type IN ('CARC', 'RARC')),
    code TEXT NOT NULL,
    description TEXT NOT NULL,
    group_code TEXT,
    is_active INTEGER DEFAULT 1 CHECK(is_active IN (0, 1)),
    UNIQUE(code_type, code)
);

-- ===========================================================================
-- OPERATIONAL TABLES
-- ===========================================================================

-- Active EDI file buckets
CREATE TABLE IF NOT EXISTS edi_file_buckets (
    bucket_id TEXT PRIMARY KEY DEFAULT (lower(hex(randomblob(16)))),
    status TEXT NOT NULL DEFAULT 'ACCUMULATING'
        CHECK (status IN ('ACCUMULATING', 'PENDING_APPROVAL', 'GENERATING', 'COMPLETED', 'FAILED')),
    bucketing_rule_id TEXT REFERENCES edi_bucketing_rules(id),
    bucketing_rule_name TEXT,
    payer_id TEXT NOT NULL,
    payer_name TEXT,
    payee_id TEXT NOT NULL,
    payee_name TEXT,
    bin_number TEXT,
    pcn_number TEXT,
    claim_count INTEGER DEFAULT 0,
    total_amount REAL DEFAULT 0.0,
    rejection_count INTEGER DEFAULT 0,
    file_naming_template_id TEXT REFERENCES edi_file_naming_templates(id),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    last_updated TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    awaiting_approval_since TIMESTAMP,
    approved_by TEXT,
    approved_at TIMESTAMP,
    generation_started_at TIMESTAMP,
    generation_completed_at TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_buckets_status ON edi_file_buckets(status);
CREATE INDEX IF NOT EXISTS idx_buckets_payer_payee ON edi_file_buckets(payer_id, payee_id);
CREATE INDEX IF NOT EXISTS idx_buckets_pending ON edi_file_buckets(status) WHERE status = 'PENDING_APPROVAL';

-- Bucket approval log
CREATE TABLE IF NOT EXISTS bucket_approval_log (
    id TEXT PRIMARY KEY DEFAULT (lower(hex(randomblob(16)))),
    bucket_id TEXT REFERENCES edi_file_buckets(bucket_id) ON DELETE CASCADE,
    action TEXT NOT NULL CHECK (action IN ('APPROVE', 'REJECT', 'OVERRIDE')),
    approved_by TEXT NOT NULL,
    comments TEXT,
    scheduled_generation_time TIMESTAMP,
    approved_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_approval_log_bucket ON bucket_approval_log(bucket_id);

-- Claim processing log
CREATE TABLE IF NOT EXISTS claim_processing_log (
    id TEXT PRIMARY KEY DEFAULT (lower(hex(randomblob(16)))),
    claim_id TEXT NOT NULL,
    bucket_id TEXT REFERENCES edi_file_buckets(bucket_id),
    payer_id TEXT,
    payee_id TEXT,
    claim_amount REAL,
    paid_amount REAL,
    adjustment_amount REAL,
    status TEXT,
    rejection_reason TEXT,
    processed_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_claim_log_bucket ON claim_processing_log(bucket_id);
CREATE INDEX IF NOT EXISTS idx_claim_log_claim ON claim_processing_log(claim_id);

-- File generation history
CREATE TABLE IF NOT EXISTS file_generation_history (
    id TEXT PRIMARY KEY DEFAULT (lower(hex(randomblob(16)))),
    bucket_id TEXT REFERENCES edi_file_buckets(bucket_id),
    generated_file_name TEXT NOT NULL,
    file_path TEXT,
    file_size_bytes INTEGER,
    claim_count INTEGER NOT NULL,
    total_amount REAL NOT NULL,
    generated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    generated_by TEXT,
    delivery_status TEXT DEFAULT 'PENDING'
        CHECK (delivery_status IN ('PENDING', 'DELIVERED', 'FAILED', 'RETRY')),
    delivered_at TIMESTAMP,
    delivery_attempt_count INTEGER DEFAULT 0,
    error_message TEXT,
    file_content TEXT  -- EDI file content stored as TEXT for SQLite
);

CREATE INDEX IF NOT EXISTS idx_file_history_bucket ON file_generation_history(bucket_id);
CREATE INDEX IF NOT EXISTS idx_file_history_status ON file_generation_history(delivery_status);
CREATE INDEX IF NOT EXISTS idx_file_history_date ON file_generation_history(generated_at DESC);

-- ===========================================================================
-- TRIGGERS FOR UPDATED_AT TIMESTAMPS
-- ===========================================================================

-- Payers
CREATE TRIGGER IF NOT EXISTS update_payers_updated_at
AFTER UPDATE ON payers
FOR EACH ROW
BEGIN
    UPDATE payers
    SET updated_at = CURRENT_TIMESTAMP
    WHERE id = NEW.id;
END;

-- Payees
CREATE TRIGGER IF NOT EXISTS update_payees_updated_at
AFTER UPDATE ON payees
FOR EACH ROW
BEGIN
    UPDATE payees
    SET updated_at = CURRENT_TIMESTAMP
    WHERE id = NEW.id;
END;

-- EDI bucketing rules
CREATE TRIGGER IF NOT EXISTS update_bucketing_rules_updated_at
AFTER UPDATE ON edi_bucketing_rules
FOR EACH ROW
BEGIN
    UPDATE edi_bucketing_rules
    SET updated_at = CURRENT_TIMESTAMP
    WHERE id = NEW.id;
END;

-- EDI generation thresholds
CREATE TRIGGER IF NOT EXISTS update_thresholds_updated_at
AFTER UPDATE ON edi_generation_thresholds
FOR EACH ROW
BEGIN
    UPDATE edi_generation_thresholds
    SET updated_at = CURRENT_TIMESTAMP
    WHERE id = NEW.id;
END;

-- EDI commit criteria
CREATE TRIGGER IF NOT EXISTS update_commit_criteria_updated_at
AFTER UPDATE ON edi_commit_criteria
FOR EACH ROW
BEGIN
    UPDATE edi_commit_criteria
    SET updated_at = CURRENT_TIMESTAMP
    WHERE id = NEW.id;
END;

-- EDI file naming templates
CREATE TRIGGER IF NOT EXISTS update_naming_templates_updated_at
AFTER UPDATE ON edi_file_naming_templates
FOR EACH ROW
BEGIN
    UPDATE edi_file_naming_templates
    SET updated_at = CURRENT_TIMESTAMP
    WHERE id = NEW.id;
END;

-- EDI file buckets
CREATE TRIGGER IF NOT EXISTS update_buckets_last_updated
AFTER UPDATE ON edi_file_buckets
FOR EACH ROW
BEGIN
    UPDATE edi_file_buckets
    SET last_updated = CURRENT_TIMESTAMP
    WHERE bucket_id = NEW.bucket_id;
END;

-- ===========================================================================
-- TEST AND SAMPLE DATA
-- ===========================================================================

-- =========================
-- PAYERS
-- =========================
-- Includes test payer (PAYER001) for test-change-feed.sh script and production-like samples

INSERT OR IGNORE INTO payers (id, payer_id, payer_name, isa_qualifier, isa_sender_id,
    gs_application_sender_id, address_street, address_city, address_state, address_zip,
    sftp_host, sftp_port, sftp_username, sftp_password, sftp_path,
    requires_special_handling, is_active, created_by)
VALUES
    -- Test payer for test-change-feed.sh script (uses localhost for local testing)
    ('550e8400-e29b-41d4-a716-446655440000', 'PAYER001', 'Test Insurance Company', 'ZZ', 'PAYER001ISA', 'PAYER001GS',
     '100 Test Street', 'New York', 'NY', '10001',
     'localhost', 22, 'payer001', '97e0cca0ebaca8f5cb511a7fd0dc7dd1aeede349096c4e70b00ef1c71e2144b7', '/test/835',
     0, 1, 'system'),

    -- Production-like sample payers
    ('550e8400-e29b-41d4-a716-446655440001', 'BCBS001', 'Blue Cross Blue Shield', 'ZZ', 'BCBS001ISA', 'BCBS001GS',
     '123 Insurance Way', 'Chicago', 'IL', '60601',
     'sftp.bcbs.com', 22, 'edi_user', NULL, '/inbound/835',
     0, 1, 'system'),

    ('550e8400-e29b-41d4-a716-446655440002', 'UHC001', 'UnitedHealthcare', 'ZZ', 'UHC001ISA', 'UHC001GS',
     '456 Health Plaza', 'Minneapolis', 'MN', '55401',
     'sftp.uhc.com', 22, 'edi_user', NULL, '/edi/835',
     0, 1, 'system'),

    ('550e8400-e29b-41d4-a716-446655440003', 'AETNA001', 'Aetna Insurance', 'ZZ', 'AETNA001ISA', 'AETNA001GS',
     '789 Medical Drive', 'Hartford', 'CT', '06156',
     'sftp.aetna.com', 22, 'edi_user', NULL, '/remittance/835',
     0, 1, 'system'),

    ('550e8400-e29b-41d4-a716-446655440004', 'CIGNA001', 'Cigna Health', 'ZZ', 'CIGNA001ISA', 'CIGNA001GS',
     '900 Coverage Street', 'Bloomfield', 'CT', '06002',
     NULL, NULL, NULL, NULL, NULL,
     0, 1, 'system');

-- =========================
-- PAYEES
-- =========================
-- Includes test payee (PAYEE001) for test-change-feed.sh script and production-like samples

INSERT OR IGNORE INTO payees (id, payee_id, payee_name, npi, tax_id,
    address_street, address_city, address_state, address_zip,
    requires_special_handling, is_active, created_by)
VALUES
    -- Test payee for test-change-feed.sh script
    ('660e8400-e29b-41d4-a716-446655440000', 'PAYEE001', 'General Hospital', '1234567890', '12-3456789',
     '100 Hospital Lane', 'New York', 'NY', '10001',
     0, 1, 'system'),

    -- Production-like sample payees
    ('660e8400-e29b-41d4-a716-446655440001', 'PAYEE002', 'City Medical Center', '9876543210', '98-7654321',
     '200 Clinic Avenue', 'Los Angeles', 'CA', '90001',
     0, 1, 'system'),

    ('660e8400-e29b-41d4-a716-446655440002', 'PAYEE003', 'Regional Clinic', '5555555555', '55-5555555',
     '300 Care Boulevard', 'Houston', 'TX', '77001',
     0, 1, 'system');

-- =========================
-- INSURANCE PLANS
-- =========================
-- Sample BIN/PCN combinations for pharmacy and medical plans

INSERT OR IGNORE INTO insurance_plans (id, bin_number, pcn_number, plan_name, payer_id, is_active)
VALUES
    ('890e8400-e29b-41d4-a716-446655440001', '610014', 'PAID', 'BCBS Standard Plan', '550e8400-e29b-41d4-a716-446655440001', 1),
    ('890e8400-e29b-41d4-a716-446655440002', '610014', 'RX01', 'BCBS Prescription Plan', '550e8400-e29b-41d4-a716-446655440001', 1),
    ('890e8400-e29b-41d4-a716-446655440003', '003858', 'MEDADV', 'UHC Medicare Advantage', '550e8400-e29b-41d4-a716-446655440002', 1);

-- =========================
-- EDI BUCKETING RULES
-- =========================
-- Define how claims are grouped into buckets

INSERT OR IGNORE INTO edi_bucketing_rules (id, rule_name, rule_type, grouping_expression, priority,
    linked_payer_id, linked_payee_id, description, is_active, created_by)
VALUES
    -- High-priority rule for specific payer-payee combination (used in test scripts)
    ('770e8400-e29b-41d4-a716-446655440000', 'BCBS - General Hospital', 'PAYER_PAYEE', NULL, 100,
     NULL, NULL, 'Default rule for BCBS and General Hospital combination', 1, 'system'),

    -- Default payer/payee bucketing (most common)
    ('770e8400-e29b-41d4-a716-446655440001', 'Default Payer/Payee Bucketing', 'PAYER_PAYEE', NULL, 1,
     NULL, NULL, 'Groups claims by payer and payee combination', 1, 'system'),

    -- BIN/PCN based bucketing for pharmacy claims
    ('770e8400-e29b-41d4-a716-446655440002', 'BIN/PCN Grouping', 'BIN_PCN', NULL, 2,
     NULL, NULL, 'Groups claims by insurance BIN and PCN numbers', 1, 'system');

-- =========================
-- EDI GENERATION THRESHOLDS
-- =========================
-- Define when buckets should trigger EDI file generation

INSERT OR IGNORE INTO edi_generation_thresholds (id, threshold_name, threshold_type,
    max_claims, max_amount, time_duration, generation_schedule, linked_bucketing_rule_id, is_active, created_by)
VALUES
    -- Claim count threshold (triggers at 5 claims)
    ('7a0e8400-e29b-41d4-a716-446655440001', 'Daily Claim Count', 'CLAIM_COUNT', 5, NULL, 'DAILY', NULL,
     '770e8400-e29b-41d4-a716-446655440001', 1, 'system'),

    -- Time-based threshold (weekly batch)
    ('7a0e8400-e29b-41d4-a716-446655440003', 'Weekly Batch', 'TIME', NULL, NULL, 'WEEKLY', NULL,
     '770e8400-e29b-41d4-a716-446655440002', 1, 'system'),

    -- Amount threshold (triggers at $5000)
    ('d5c45061-053a-4ff8-8b51-4f9e082c65bd', 'BCBS - General Hospital', 'AMOUNT', NULL, 5000.0, NULL, NULL,
     '770e8400-e29b-41d4-a716-446655440000', 1, 'system');

-- =========================
-- EDI COMMIT CRITERIA
-- =========================
-- Define approval workflows and automatic file generation rules

INSERT OR IGNORE INTO edi_commit_criteria (id, criteria_name, commit_mode,
    auto_commit_threshold, manual_approval_threshold, approval_required_roles, override_permissions,
    linked_bucketing_rule_id, is_active, created_by)
VALUES
    -- AUTO mode: Generate automatically when thresholds are met
    -- auto_commit_threshold = amount threshold ($500)
    -- manual_approval_threshold = claim count threshold (10 claims)
    ('7b0e8400-e29b-41d4-a716-446655440001', 'Auto Commit Small Batches', 'AUTO', 500, 10, NULL, NULL,
     NULL, 1, 'system'),

    -- MANUAL mode: Always require approval before generation
    ('7b0e8400-e29b-41d4-a716-446655440002', 'Manual Approval Required', 'MANUAL', NULL, NULL,
     '["OPERATIONS_MANAGER","FINANCE_ADMIN"]', NULL,
     NULL, 1, 'system'),

    -- HYBRID mode: Auto-generate below threshold, require approval above
    -- auto_commit_threshold = amount threshold ($5000)
    -- manual_approval_threshold = claim count threshold (10 claims)
    ('e887965a-0957-4036-94ea-7c0afefb2363', 'BCBS hybrid commit', 'HYBRID', 5000, 10,
     '["ADMIN"]', NULL,
     '770e8400-e29b-41d4-a716-446655440000', 1, 'system');

-- =========================
-- EDI FILE NAMING TEMPLATES
-- =========================
-- Define how generated EDI files should be named

INSERT OR IGNORE INTO edi_file_naming_templates (id, template_name, template_pattern,
    date_format, time_format, sequence_padding, case_conversion, separator_type, is_default,
    linked_bucketing_rule_id, version, created_by)
VALUES
    -- Default template (most commonly used)
    ('880e8400-e29b-41d4-a716-446655440001', 'Standard EDI 835', '{payerId}_{payeeId}_{date}_{sequenceNumber}.835',
     'yyyyMMdd', 'HHmmss', 4, 'UPPER', '_', 1, NULL, 1, 'system'),

    -- Detailed format with time component
    ('880e8400-e29b-41d4-a716-446655440002', 'Detailed Format', 'EDI835_{payerId}_{date}_{time}_SEQ{sequenceNumber}.txt',
     'yyyy-MM-dd', 'HHmmss', 6, 'UPPER', '_', 0, NULL, 1, 'system');

-- =========================
-- ADJUSTMENT CODE MAPPINGS
-- =========================
-- Standard CARC (Claim Adjustment Reason Codes) and RARC (Remittance Advice Remark Codes)

-- CARC Codes (most common)
INSERT OR IGNORE INTO adjustment_code_mapping (id, code_type, code, description, group_code, is_active)
VALUES
    -- Patient responsibility codes (PR group)
    ('8a0e8400-e29b-41d4-a716-446655440001', 'CARC', '1', 'Deductible Amount', 'PR', 1),
    ('8a0e8400-e29b-41d4-a716-446655440002', 'CARC', '2', 'Coinsurance Amount', 'PR', 1),
    ('8a0e8400-e29b-41d4-a716-446655440003', 'CARC', '3', 'Co-payment Amount', 'PR', 1),

    -- Contractual obligation codes (CO group)
    ('8a0e8400-e29b-41d4-a716-446655440004', 'CARC', '45', 'Charge exceeds fee schedule/maximum allowable', 'CO', 1),
    ('8a0e8400-e29b-41d4-a716-446655440005', 'CARC', '97', 'Payment adjusted because benefit included in another service', 'CO', 1),
    ('8a0e8400-e29b-41d4-a716-446655440006', 'CARC', '18', 'Exact duplicate claim/service', 'CO', 1),
    ('8a0e8400-e29b-41d4-a716-446655440007', 'CARC', '50', 'Non-covered service', 'CO', 1),
    ('8a0e8400-e29b-41d4-a716-446655440008', 'CARC', '96', 'Non-covered charge(s)', 'CO', 1),
    ('8a0e8400-e29b-41d4-a716-446655440009', 'CARC', '204', 'Service/equipment not covered by payer', 'CO', 1);

-- RARC Codes (Remittance Advice Remark Codes)
INSERT OR IGNORE INTO adjustment_code_mapping (id, code_type, code, description, group_code, is_active)
VALUES
    ('8a0e8400-e29b-41d4-a716-446655440010', 'RARC', 'N1', 'Alert: You may appeal this decision', NULL, 1),
    ('8a0e8400-e29b-41d4-a716-446655440011', 'RARC', 'N115', 'This decision is based on a local coverage determination', NULL, 1),
    ('8a0e8400-e29b-41d4-a716-446655440012', 'RARC', 'N130', 'Consult plan benefit documents/policies', NULL, 1),
    ('8a0e8400-e29b-41d4-a716-446655440013', 'RARC', 'M15', 'Separately billed services/tests have been bundled', NULL, 1);

-- ===========================================================================
-- NOTES
-- ===========================================================================
-- 1. This schema is for SQLite local development only
-- 2. Production should use the PostgreSQL schema in /database/schema.sql
-- 3. Change feed triggers DO NOT fire for these admin portal tables
-- 4. Only the 'claims' table (in schema.sql) has change feed triggers
-- 5. All IDs use proper UUID format (36 chars with hyphens: xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx)
-- 6. Sample data includes PAYER001/PAYEE001 for test-change-feed.sh script compatibility
-- 7. JSON arrays stored as TEXT (parse in application layer with StringArrayConverter)
-- 8. Foreign keys enabled via PRAGMA foreign_keys = ON
--
-- COMMIT CRITERIA FIELD MAPPING:
-- - auto_commit_threshold: Stores AMOUNT threshold (in dollars) for AUTO/HYBRID modes
-- - manual_approval_threshold: Stores CLAIM COUNT threshold for AUTO/HYBRID modes
-- - Frontend sends: approvalAmountThreshold → maps to auto_commit_threshold
-- - Frontend sends: approvalClaimCountThreshold → maps to manual_approval_threshold
-- - Backend controller handles mapping between frontend and database field names
-- ===========================================================================

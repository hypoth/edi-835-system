-- SQLite Change Feed Schema
-- Version-Based Approach for tracking changes across multiple feed runs

-- ============================================================================
-- DATA CHANGES TABLE
-- Tracks all changes to monitored tables with versioning support
-- ============================================================================
CREATE TABLE IF NOT EXISTS data_changes (
    change_id INTEGER PRIMARY KEY AUTOINCREMENT,
    feed_version INTEGER NOT NULL DEFAULT 1,
    table_name TEXT NOT NULL,
    operation TEXT NOT NULL CHECK(operation IN ('INSERT', 'UPDATE', 'DELETE')),
    row_id TEXT NOT NULL,  -- Can be UUID or numeric ID
    old_values TEXT,       -- JSON representation of old values
    new_values TEXT,       -- JSON representation of new values
    changed_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    sequence_number INTEGER NOT NULL,
    processed BOOLEAN DEFAULT 0,
    processed_at TIMESTAMP,
    error_message TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Index for efficient version-based queries
CREATE INDEX IF NOT EXISTS idx_data_changes_version
    ON data_changes(feed_version, sequence_number);

-- Index for finding unprocessed changes
CREATE INDEX IF NOT EXISTS idx_data_changes_processed
    ON data_changes(processed, feed_version);

-- Index for table-specific queries
CREATE INDEX IF NOT EXISTS idx_data_changes_table
    ON data_changes(table_name, changed_at);

-- Index for row tracking
CREATE INDEX IF NOT EXISTS idx_data_changes_row
    ON data_changes(table_name, row_id);

-- ============================================================================
-- FEED VERSION TRACKING
-- Tracks each change feed processing run
-- ============================================================================
CREATE TABLE IF NOT EXISTS feed_versions (
    version_id INTEGER PRIMARY KEY AUTOINCREMENT,
    started_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMP,
    status TEXT CHECK(status IN ('RUNNING', 'COMPLETED', 'FAILED')) DEFAULT 'RUNNING',
    changes_count INTEGER DEFAULT 0,
    processed_count INTEGER DEFAULT 0,
    error_count INTEGER DEFAULT 0,
    error_message TEXT,
    host_name TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Index for version status queries
CREATE INDEX IF NOT EXISTS idx_feed_versions_status
    ON feed_versions(status, started_at);

-- ============================================================================
-- CLAIMS TABLE (For testing change feed)
-- Simulates the Cosmos DB claims container
-- ============================================================================
CREATE TABLE IF NOT EXISTS claims (
    id TEXT PRIMARY KEY,
    claim_number TEXT UNIQUE NOT NULL,
    patient_name TEXT,
    payer_id TEXT,
    payee_id TEXT,
    service_date DATE,
    total_charge DECIMAL(12,2),
    total_paid DECIMAL(12,2),
    status TEXT CHECK(status IN ('PENDING', 'PROCESSING', 'PROCESSED', 'PAID', 'REJECTED', 'DENIED')),
    rejection_reason TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    version INTEGER DEFAULT 1  -- For optimistic locking
);

-- Index for status-based queries
CREATE INDEX IF NOT EXISTS idx_claims_status
    ON claims(status, updated_at);

-- Index for payer/payee queries
CREATE INDEX IF NOT EXISTS idx_claims_payer_payee
    ON claims(payer_id, payee_id, status);

-- ============================================================================
-- CLAIM STATUS HISTORY TABLE
-- Tracks status changes for claims
-- ============================================================================
CREATE TABLE IF NOT EXISTS claim_status_history (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    claim_id TEXT NOT NULL,
    old_status TEXT,
    new_status TEXT NOT NULL,
    changed_by TEXT,
    change_reason TEXT,
    changed_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (claim_id) REFERENCES claims(id) ON DELETE CASCADE
);

-- Index for claim history queries
CREATE INDEX IF NOT EXISTS idx_claim_history_claim
    ON claim_status_history(claim_id, changed_at DESC);

-- ============================================================================
-- CHANGE FEED CHECKPOINT TABLE
-- Tracks the last processed position for each consumer
-- ============================================================================
CREATE TABLE IF NOT EXISTS changefeed_checkpoint (
    consumer_id TEXT PRIMARY KEY,
    last_feed_version INTEGER NOT NULL DEFAULT 0,
    last_sequence_number INTEGER NOT NULL DEFAULT 0,
    last_checkpoint_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    total_processed INTEGER DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- ============================================================================
-- TRIGGERS FOR AUTOMATIC CHANGE TRACKING
-- Automatically capture INSERT, UPDATE, DELETE operations on claims table
-- ============================================================================

-- Trigger for INSERT operations
CREATE TRIGGER IF NOT EXISTS claims_insert_trigger
AFTER INSERT ON claims
FOR EACH ROW
BEGIN
    INSERT INTO data_changes (
        feed_version,
        table_name,
        operation,
        row_id,
        old_values,
        new_values,
        sequence_number,
        processed
    )
    SELECT
        COALESCE(MAX(version_id), 0) + 1,
        'claims',
        'INSERT',
        NEW.id,
        NULL,
        json_object(
            'id', NEW.id,
            'claimNumber', NEW.claim_number,
            'patientName', NEW.patient_name,
            'payerId', NEW.payer_id,
            'payeeId', NEW.payee_id,
            'serviceDate', NEW.service_date,
            'totalChargeAmount', NEW.total_charge,
            'paidAmount', NEW.total_paid,
            'status', NEW.status,
            'statusReason', NEW.rejection_reason
        ),
        (SELECT COALESCE(MAX(sequence_number), 0) + 1 FROM data_changes),
        0
    FROM feed_versions;
END;

-- Trigger for UPDATE operations
CREATE TRIGGER IF NOT EXISTS claims_update_trigger
AFTER UPDATE ON claims
FOR EACH ROW
WHEN NEW.version != OLD.version OR NEW.status != OLD.status
BEGIN
    INSERT INTO data_changes (
        feed_version,
        table_name,
        operation,
        row_id,
        old_values,
        new_values,
        sequence_number,
        processed
    )
    SELECT
        COALESCE(MAX(version_id), 0) + 1,
        'claims',
        'UPDATE',
        NEW.id,
        json_object(
            'id', OLD.id,
            'claimNumber', OLD.claim_number,
            'patientName', OLD.patient_name,
            'payerId', OLD.payer_id,
            'payeeId', OLD.payee_id,
            'serviceDate', OLD.service_date,
            'totalChargeAmount', OLD.total_charge,
            'paidAmount', OLD.total_paid,
            'status', OLD.status,
            'statusReason', OLD.rejection_reason
        ),
        json_object(
            'id', NEW.id,
            'claimNumber', NEW.claim_number,
            'patientName', NEW.patient_name,
            'payerId', NEW.payer_id,
            'payeeId', NEW.payee_id,
            'serviceDate', NEW.service_date,
            'totalChargeAmount', NEW.total_charge,
            'paidAmount', NEW.total_paid,
            'status', NEW.status,
            'statusReason', NEW.rejection_reason
        ),
        (SELECT COALESCE(MAX(sequence_number), 0) + 1 FROM data_changes),
        0
    FROM feed_versions;

    -- Track status change in history
    INSERT INTO claim_status_history (claim_id, old_status, new_status, change_reason)
    VALUES (NEW.id, OLD.status, NEW.status, 'Auto-tracked by trigger');
END;

-- Trigger for DELETE operations
CREATE TRIGGER IF NOT EXISTS claims_delete_trigger
AFTER DELETE ON claims
FOR EACH ROW
BEGIN
    INSERT INTO data_changes (
        feed_version,
        table_name,
        operation,
        row_id,
        old_values,
        new_values,
        sequence_number,
        processed
    )
    SELECT
        COALESCE(MAX(version_id), 0) + 1,
        'claims',
        'DELETE',
        OLD.id,
        json_object(
            'id', OLD.id,
            'claimNumber', OLD.claim_number,
            'patientName', OLD.patient_name,
            'payerId', OLD.payer_id,
            'payeeId', OLD.payee_id,
            'serviceDate', OLD.service_date,
            'totalChargeAmount', OLD.total_charge,
            'paidAmount', OLD.total_paid,
            'status', OLD.status,
            'statusReason', OLD.rejection_reason
        ),
        NULL,
        (SELECT COALESCE(MAX(sequence_number), 0) + 1 FROM data_changes),
        0
    FROM feed_versions;
END;

-- ============================================================================
-- HELPER VIEWS
-- ============================================================================

-- View for unprocessed changes
CREATE VIEW IF NOT EXISTS v_unprocessed_changes AS
SELECT
    dc.change_id,
    dc.feed_version,
    dc.table_name,
    dc.operation,
    dc.row_id,
    dc.new_values,
    dc.old_values,
    dc.changed_at,
    dc.sequence_number
FROM data_changes dc
WHERE dc.processed = 0
ORDER BY dc.feed_version ASC, dc.sequence_number ASC;

-- View for recent changes (last 1000)
CREATE VIEW IF NOT EXISTS v_recent_changes AS
SELECT
    dc.change_id,
    dc.feed_version,
    dc.table_name,
    dc.operation,
    dc.row_id,
    dc.changed_at,
    dc.processed,
    dc.processed_at
FROM data_changes dc
ORDER BY dc.changed_at DESC
LIMIT 1000;

-- View for feed version summary
CREATE VIEW IF NOT EXISTS v_feed_version_summary AS
SELECT
    fv.version_id,
    fv.started_at,
    fv.completed_at,
    fv.status,
    fv.changes_count,
    fv.processed_count,
    fv.error_count,
    COUNT(dc.change_id) as actual_changes
FROM feed_versions fv
LEFT JOIN data_changes dc ON dc.feed_version = fv.version_id
GROUP BY fv.version_id
ORDER BY fv.version_id DESC;

-- ============================================================================
-- SAMPLE DATA (For testing)
-- ============================================================================

-- Initialize first feed version
INSERT OR IGNORE INTO feed_versions (version_id, status, host_name)
VALUES (1, 'RUNNING', 'localhost');

-- Initialize checkpoint for default consumer
INSERT OR IGNORE INTO changefeed_checkpoint (consumer_id, last_feed_version, last_sequence_number)
VALUES ('edi835-processor-default', 0, 0);

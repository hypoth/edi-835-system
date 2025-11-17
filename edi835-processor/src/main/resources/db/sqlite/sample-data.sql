-- ===========================================================================
-- Sample Data for SQLite Change Feed Testing
-- This file populates test data for local development and testing
--
-- Prerequisites: Run admin-schema.sql first to create configuration tables
-- Usage: sqlite3 ./data/edi835-local.db < sample-data.sql
-- ===========================================================================

-- Note: Configuration data (payers, payees, rules, thresholds, etc.)
-- is already loaded by admin-schema.sql. This file only adds test claims.

-- Initialize feed version tracking
INSERT OR REPLACE INTO feed_versions (version_id, status, host_name, started_at)
VALUES (1, 'RUNNING', 'localhost', datetime('now'));

-- ===========================================================================
-- SAMPLE CLAIMS DATA
-- ===========================================================================
-- Uses payers/payees from admin-schema.sql:
-- - PAYER001, PAYEE001: For test-change-feed.sh script compatibility
-- - BCBS001, UHC001, AETNA001: Production-like sample payers
-- - PAYEE002, PAYEE003: Additional sample payees

INSERT OR IGNORE INTO claims (id, claim_number, patient_name, payer_id, payee_id, service_date, total_charge, total_paid, status, created_at, updated_at, version)
VALUES
    -- Claims for PAYER001 -> PAYEE001 (will group into 1 bucket)
    ('claim-001', 'CLM-2024-001', 'John Doe', 'PAYER001', 'PAYEE001', '2024-01-15', 1500.00, 0.00, 'PENDING', datetime('now', '-5 days'), datetime('now', '-5 days'), 1),
    ('claim-002', 'CLM-2024-002', 'Jane Smith', 'PAYER001', 'PAYEE001', '2024-01-16', 2500.00, 0.00, 'PENDING', datetime('now', '-4 days'), datetime('now', '-4 days'), 1),

    -- Claims for BCBS001 -> PAYEE001 (different bucket)
    ('claim-003', 'CLM-2024-003', 'Bob Johnson', 'BCBS001', 'PAYEE001', '2024-01-17', 3200.00, 0.00, 'PENDING', datetime('now', '-3 days'), datetime('now', '-3 days'), 1),

    -- Claims for PAYER001 -> PAYEE002 (different bucket)
    ('claim-004', 'CLM-2024-004', 'Alice Williams', 'PAYER001', 'PAYEE002', '2024-01-18', 1800.00, 0.00, 'PENDING', datetime('now', '-2 days'), datetime('now', '-2 days'), 1),

    -- Claims for UHC001 -> PAYEE002 (different bucket)
    ('claim-005', 'CLM-2024-005', 'Charlie Brown', 'UHC001', 'PAYEE002', '2024-01-19', 4500.00, 0.00, 'PENDING', datetime('now', '-1 day'), datetime('now', '-1 day'), 1);

-- ===========================================================================
-- SIMULATE CLAIM PROCESSING
-- ===========================================================================
-- These updates will automatically create entries in data_changes table via triggers

-- Process first claim (PAYER001 -> PAYEE001)
UPDATE claims
SET status = 'PROCESSING', updated_at = datetime('now', '-4 hours'), version = version + 1
WHERE id = 'claim-001';

UPDATE claims
SET status = 'PROCESSED', total_paid = 1350.00, updated_at = datetime('now', '-3 hours'), version = version + 1
WHERE id = 'claim-001';

UPDATE claims
SET status = 'PAID', updated_at = datetime('now', '-2 hours'), version = version + 1
WHERE id = 'claim-001';

-- Process second claim (PAYER001 -> PAYEE001, same bucket)
UPDATE claims
SET status = 'PROCESSING', updated_at = datetime('now', '-2 hours'), version = version + 1
WHERE id = 'claim-002';

UPDATE claims
SET status = 'PROCESSED', total_paid = 2100.00, updated_at = datetime('now', '-1 hour'), version = version + 1
WHERE id = 'claim-002';

UPDATE claims
SET status = 'PAID', updated_at = datetime('now', '-30 minutes'), version = version + 1
WHERE id = 'claim-002';

-- Process third claim (BCBS001 -> PAYEE001, different bucket)
UPDATE claims
SET status = 'PROCESSING', updated_at = datetime('now', '-1 hour'), version = version + 1
WHERE id = 'claim-003';

UPDATE claims
SET status = 'PROCESSED', total_paid = 2800.00, updated_at = datetime('now', '-30 minutes'), version = version + 1
WHERE id = 'claim-003';

-- Reject fourth claim (PAYER001 -> PAYEE002)
UPDATE claims
SET status = 'PROCESSING', updated_at = datetime('now', '-45 minutes'), version = version + 1
WHERE id = 'claim-004';

UPDATE claims
SET status = 'REJECTED', rejection_reason = 'Invalid procedure code', updated_at = datetime('now', '-30 minutes'), version = version + 1
WHERE id = 'claim-004';

-- Fifth claim still pending (UHC001 -> PAYEE002, no updates)

-- ===========================================================================
-- SUMMARY REPORTS
-- ===========================================================================

-- Display claim summary
SELECT
    'Total Claims:' AS metric,
    COUNT(*) AS value
FROM claims
UNION ALL
SELECT
    'Paid Claims:',
    COUNT(*)
FROM claims WHERE status = 'PAID'
UNION ALL
SELECT
    'Processed Claims:',
    COUNT(*)
FROM claims WHERE status = 'PROCESSED'
UNION ALL
SELECT
    'Pending Claims:',
    COUNT(*)
FROM claims WHERE status = 'PENDING'
UNION ALL
SELECT
    'Rejected Claims:',
    COUNT(*)
FROM claims WHERE status = 'REJECTED'
UNION ALL
SELECT
    'Total Changes Tracked:',
    COUNT(*)
FROM data_changes
UNION ALL
SELECT
    'Unprocessed Changes:',
    COUNT(*)
FROM data_changes WHERE processed = 0;

-- Show recent changes
SELECT
    '=== Recent Changes ===' AS info;

SELECT
    change_id,
    feed_version,
    table_name,
    operation,
    row_id,
    changed_at,
    processed
FROM data_changes
ORDER BY changed_at DESC
LIMIT 10;

-- Show expected buckets (after change feed processing)
SELECT
    '=== Expected Buckets (after processing) ===' AS info;

SELECT
    'PAYER001 -> PAYEE001' as bucket,
    '2 paid claims, $3,450.00' as expected_state
UNION ALL
SELECT
    'BCBS001 -> PAYEE001',
    '1 processed claim, $2,800.00'
UNION ALL
SELECT
    'PAYER001 -> PAYEE002',
    '1 rejected claim (not in bucket)'
UNION ALL
SELECT
    'UHC001 -> PAYEE002',
    '1 pending claim (not yet processed)';

-- ===========================================================================
-- NOTES
-- ===========================================================================
-- 1. After running this script, start the backend to process changes:
--    cd edi835-processor && mvn spring-boot:run
--
-- 2. The SQLiteChangeFeedProcessor will automatically:
--    - Detect unprocessed changes in data_changes table
--    - Process PROCESSED/PAID status claims
--    - Create buckets for PAYER_PAYEE combinations
--    - Update claim_processing_log
--
-- 3. Expected results (after ~10 seconds):
--    - 2 buckets created (PAYER001->PAYEE001, BCBS001->PAYEE001)
--    - 3 claims in claim_processing_log
--    - 1 rejected claim logged
--    - 1 pending claim ignored (not processed)
--
-- 4. Verify results:
--    - Check buckets: SELECT * FROM edi_file_buckets;
--    - Check processing log: SELECT * FROM claim_processing_log;
--    - Check API: curl http://localhost:8080/api/v1/dashboard/summary | jq
--    - Check dashboard: http://localhost:3000/dashboard
-- ===========================================================================

-- ===========================================================================
-- SAMPLE NCPDP CLAIMS DATA
-- ===========================================================================
-- Simulates raw NCPDP D.0 pharmacy claims for testing ingestion and processing

-- Sample NCPDP raw claims
INSERT OR IGNORE INTO ncpdp_raw_claims (
    id, payer_id, pharmacy_id, transaction_id, raw_content, transaction_type,
    service_date, patient_id, prescription_number, status, created_date, retry_count
)
VALUES
    -- Pending NCPDP claim from CVS Pharmacy
    (
        'ncpdp-001',
        'BCBS001',
        'PHARMACY001',
        'TXN-2024-001',
        'STX~AM01~PHARMACY001~AM04~PROVIDER001~AM07~BCBS001~AM11~123456789~AM13~2024-01-15~AM15~50.00~AN~SE~',
        'B1',
        '2024-01-15',
        '123456789',
        'RX123456',
        'PENDING',
        datetime('now', '-2 hours'),
        0
    ),

    -- Processing NCPDP claim from Walgreens
    (
        'ncpdp-002',
        'UHC001',
        'PHARMACY002',
        'TXN-2024-002',
        'STX~AM01~PHARMACY002~AM04~PROVIDER002~AM07~UHC001~AM11~987654321~AM13~2024-01-16~AM15~75.50~AN~SE~',
        'B1',
        '2024-01-16',
        '987654321',
        'RX789012',
        'PROCESSING',
        datetime('now', '-1 hour'),
        0
    ),

    -- Processed NCPDP claim
    (
        'ncpdp-003',
        'AETNA001',
        'PHARMACY001',
        'TXN-2024-003',
        'STX~AM01~PHARMACY001~AM04~PROVIDER001~AM07~AETNA001~AM11~555666777~AM13~2024-01-17~AM15~120.00~AN~SE~',
        'B1',
        '2024-01-17',
        '555666777',
        'RX345678',
        'PROCESSED',
        datetime('now', '-3 hours'),
        0
    );

-- Add processing log entries for the processed claim
INSERT OR IGNORE INTO ncpdp_processing_log (
    ncpdp_claim_id, processing_stage, status, message, details, created_date
)
VALUES
    ('ncpdp-003', 'PARSE', 'SUCCESS', 'Successfully parsed NCPDP transaction', '{"segments": 8}', datetime('now', '-2 hours', '-50 minutes')),
    ('ncpdp-003', 'MAP', 'SUCCESS', 'Mapped NCPDP to Claim entity', '{"claimId": "claim-ncpdp-003"}', datetime('now', '-2 hours', '-45 minutes')),
    ('ncpdp-003', 'PROCESS', 'SUCCESS', 'Claim processed successfully', '{"bucketId": "bucket-001"}', datetime('now', '-2 hours', '-40 minutes')),
    ('ncpdp-003', 'COMPLETE', 'SUCCESS', 'NCPDP claim processing complete', NULL, datetime('now', '-2 hours', '-35 minutes'));

-- Update processed claim with claim_id and processed_date
UPDATE ncpdp_raw_claims
SET claim_id = 'claim-ncpdp-003',
    processed_date = datetime('now', '-2 hours', '-35 minutes')
WHERE id = 'ncpdp-003';

-- Update processing claim with processing_started_date
UPDATE ncpdp_raw_claims
SET processing_started_date = datetime('now', '-1 hour')
WHERE id = 'ncpdp-002';

# SQLite Change Feed Configuration

This document explains how to use the SQLite-based change feed for local development and testing of the EDI 835 system.

## Overview

The SQLite change feed provides a lightweight alternative to Azure Cosmos DB for local development. It implements a **version-based change tracking** approach that allows:

- Multiple processing runs over the same data
- Replay capability for testing
- Checkpoint management for resumability
- Automatic change tracking via triggers

## Quick Start

### 1. Activate SQLite Profile

Set the Spring profile to `sqlite`:

```bash
# Using environment variable
export SPRING_PROFILES_ACTIVE=sqlite

# Or using command line
mvn spring-boot:run -Dspring-boot.run.profiles=sqlite

# Or using application.yml (already set as default)
spring.profiles.active: sqlite
```

### 2. Run the Application

The application will automatically:
- Create a SQLite database at `./data/edi835-local.db`
- Initialize the change feed schema
- Start polling for changes every 5 seconds

```bash
mvn spring-boot:run
```

### 3. Load Sample Data

Use the SQL script to populate test data:

```bash
# Using sqlite3 CLI
sqlite3 ./data/edi835-local.db < src/main/resources/db/sqlite/sample-data.sql

# Or using any SQLite GUI tool
```

## Configuration Options

### application-sqlite.yml

```yaml
changefeed:
  type: sqlite
  sqlite:
    enabled: true
    poll-interval-ms: 5000        # Poll every 5 seconds
    batch-size: 100                # Process up to 100 changes per batch
    auto-version: true             # Auto-increment feed version
    consumer-id: edi835-processor-default
    init-schema: true              # Auto-initialize database schema

spring:
  datasource:
    url: jdbc:sqlite:./data/edi835-local.db
    driver-class-name: org.sqlite.JDBC
```

### Environment Variables

- `SQLITE_DB_PATH`: Path to SQLite database file (default: `./data/edi835-local.db`)
- `SPRING_PROFILES_ACTIVE`: Set to `sqlite` to use SQLite profile
- `SQLITE_CHANGEFEED_ENABLED`: Set to `true` to enable SQLite change feed

## How It Works

### Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                     SQLite Database                          │
├─────────────────────────────────────────────────────────────┤
│                                                               │
│  ┌──────────┐    Triggers    ┌──────────────────┐          │
│  │  Claims  │ ──────────────> │  data_changes    │          │
│  │  Table   │                 │  (change feed)   │          │
│  └──────────┘                 └──────────────────┘          │
│                                        │                      │
│                                        ▼                      │
│                           ┌──────────────────────┐          │
│                           │ changefeed_checkpoint │          │
│                           └──────────────────────┘          │
└─────────────────────────────────────────────────────────────┘
                                    │
                                    ▼
                      ┌─────────────────────────┐
                      │ SQLiteChangeFeedProcessor│
                      │  (polls every 5 seconds) │
                      └─────────────────────────┘
                                    │
                                    ▼
                        ┌──────────────────────┐
                        │  ChangeFeedHandler   │
                        │ (same as Cosmos DB)  │
                        └──────────────────────┘
                                    │
                                    ▼
                    ┌────────────────────────────┐
                    │ RemittanceProcessorService │
                    │  (bucketing, aggregation)  │
                    └────────────────────────────┘
```

### Database Triggers

SQLite triggers automatically capture changes to the `claims` table:

- **INSERT trigger**: Records new claims
- **UPDATE trigger**: Records claim status/payment changes
- **DELETE trigger**: Records claim deletions

Each trigger creates an entry in the `data_changes` table with:
- `feed_version`: Current version number
- `operation`: INSERT, UPDATE, or DELETE
- `old_values`: JSON snapshot before change
- `new_values`: JSON snapshot after change
- `sequence_number`: Order within the feed

### Version-Based Processing

The version-based approach allows multiple processing runs:

```sql
-- Get changes for version 1 (initial processing)
SELECT * FROM data_changes
WHERE feed_version = 1
ORDER BY sequence_number;

-- Start a new version (replay)
UPDATE data_changes
SET feed_version = feed_version + 1
WHERE processed = true;

-- Process version 2 (replay with same data)
SELECT * FROM data_changes
WHERE feed_version = 2
ORDER BY sequence_number;
```

### Checkpoint Management

The processor maintains checkpoints to track progress:

```java
// Checkpoint structure
{
  "consumerId": "edi835-processor-default",
  "lastFeedVersion": 1,
  "lastSequenceNumber": 42,
  "totalProcessed": 150,
  "lastCheckpointAt": "2024-01-20T10:30:00"
}
```

## Testing the Change Feed

### 1. Insert Test Claims

```sql
INSERT INTO claims (id, claim_number, patient_name, payer_id, payee_id,
                    service_date, total_charge, total_paid, status)
VALUES ('test-001', 'TEST-001', 'Test Patient', 'PAYER001', 'PAYEE001',
        '2024-01-20', 1000.00, 0.00, 'PENDING');
```

The trigger will automatically create a change entry.

### 2. Update Claim Status

```sql
-- Simulate claim processing
UPDATE claims
SET status = 'PROCESSED',
    total_paid = 900.00,
    version = version + 1
WHERE id = 'test-001';

-- Mark as paid
UPDATE claims
SET status = 'PAID',
    version = version + 1
WHERE id = 'test-001';
```

Each update creates a new change entry with status = PROCESSED or PAID.

### 3. Monitor Change Feed

```sql
-- View unprocessed changes
SELECT * FROM v_unprocessed_changes;

-- View recent changes
SELECT * FROM v_recent_changes;

-- View checkpoint status
SELECT * FROM changefeed_checkpoint;

-- View feed version summary
SELECT * FROM v_feed_version_summary;
```

### 4. Watch Logs

The processor logs its activity:

```
2024-01-20 10:30:00 - SQLite Change Feed Processor initialized:
    consumerId=edi835-processor-default, batchSize=100, pollInterval=5000ms

2024-01-20 10:30:05 - Polling for changes...
2024-01-20 10:30:05 - Found 3 unprocessed changes to process
2024-01-20 10:30:05 - Processing batch of 3 changes
2024-01-20 10:30:05 - Processed 3 changes (3 successful, 0 errors) for version 1
```

## Useful SQL Queries

### View All Changes

```sql
SELECT
    dc.change_id,
    dc.feed_version,
    dc.operation,
    dc.row_id,
    json_extract(dc.new_values, '$.claim_number') AS claim_number,
    json_extract(dc.new_values, '$.status') AS new_status,
    dc.changed_at,
    dc.processed
FROM data_changes dc
ORDER BY dc.changed_at DESC;
```

### View Processed vs Unprocessed

```sql
SELECT
    processed,
    COUNT(*) AS count,
    MIN(changed_at) AS earliest,
    MAX(changed_at) AS latest
FROM data_changes
GROUP BY processed;
```

### View Changes by Claim

```sql
SELECT
    dc.operation,
    json_extract(dc.old_values, '$.status') AS old_status,
    json_extract(dc.new_values, '$.status') AS new_status,
    dc.changed_at
FROM data_changes dc
WHERE dc.row_id = 'claim-001'
ORDER BY dc.changed_at;
```

### Reset for Replay

```sql
-- Mark all changes as unprocessed for replay
UPDATE data_changes SET processed = 0;

-- Reset checkpoint
UPDATE changefeed_checkpoint
SET last_feed_version = 0,
    last_sequence_number = 0;

-- Increment feed version for all changes
UPDATE data_changes
SET feed_version = feed_version + 1;
```

## Switching Between SQLite and Cosmos DB

### Use SQLite (Local Dev)

```yaml
# application.yml
spring:
  profiles:
    active: sqlite

changefeed:
  type: sqlite
  sqlite:
    enabled: true
  cosmos:
    enabled: false
```

### Use Cosmos DB (Production)

```yaml
# application.yml or environment variables
spring:
  profiles:
    active: cosmos

changefeed:
  type: cosmos
  cosmos:
    enabled: true
  sqlite:
    enabled: false

spring:
  cloud:
    azure:
      cosmos:
        endpoint: https://your-account.documents.azure.com:443/
        key: your-cosmos-key
        database: claims
```

## Troubleshooting

### Database Locked Error

SQLite doesn't handle concurrent writes well. If you see "database locked" errors:

1. Ensure `maximum-pool-size: 1` in hikari configuration
2. Reduce `poll-interval-ms` if needed
3. Check that no other process is accessing the database

### Changes Not Processing

1. Check that triggers are created:
   ```sql
   SELECT name FROM sqlite_master WHERE type = 'trigger';
   ```

2. Verify change feed is enabled:
   ```sql
   SELECT * FROM changefeed_checkpoint;
   ```

3. Check application logs for errors

### Schema Initialization Failed

If schema fails to initialize:

1. Delete the database file and restart
   ```bash
   rm ./data/edi835-local.db
   mvn spring-boot:run
   ```

2. Manually run the schema script:
   ```bash
   sqlite3 ./data/edi835-local.db < src/main/resources/db/sqlite/schema.sql
   ```

## Performance Considerations

- SQLite is single-writer, so throughput is limited
- Suitable for development and testing, not production scale
- For high volume testing, consider PostgreSQL with similar triggers
- Batch size affects memory usage (default: 100 changes per batch)
- Poll interval affects responsiveness vs CPU usage (default: 5 seconds)

## Advantages of Version-Based Approach

1. **Replay Capability**: Reprocess same changes multiple times for testing
2. **No Data Loss**: All changes are preserved historically
3. **Debugging**: Easy to trace claim status changes over time
4. **Testing**: Reset and replay scenarios without regenerating data
5. **Audit Trail**: Complete history of all changes

## Migration to Production

When moving to production with Cosmos DB:

1. Same `ChangeFeedHandler` is used (no code changes)
2. Change spring profile to `cosmos`
3. Configure Cosmos DB connection in application.yml
4. The bucketing and file generation logic remains identical

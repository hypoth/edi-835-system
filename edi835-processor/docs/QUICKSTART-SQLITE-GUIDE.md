# Quick Start Guide - SQLite Mode

This guide will help you get the EDI 835 backend running locally using SQLite in under 5 minutes.

## Prerequisites

- JDK 17 or higher
- Maven 3.8+
- (Optional) SQLite CLI for database inspection

## Step 1: Run the Backend

The application uses SQLite by default, no configuration changes needed!

```bash
# Navigate to the backend directory
cd edi835-processor

# Run the application
mvn spring-boot:run
```

That's it! The application will:
- ✅ Create a SQLite database at `./data/edi835-local.db`
- ✅ Initialize all tables and triggers automatically
- ✅ Start the change feed processor (polling every 5 seconds)
- ✅ Start the REST API at `http://localhost:8080/api/v1`

## Step 2: Load Sample Data (Optional)

To test the change feed with sample claims:

```bash
# Make sure the backend is running first (from Step 1)

# In a new terminal, load the sample data
sqlite3 ./data/edi835-local.db < src/main/resources/db/sqlite/sample-data.sql
```

This will create:
- 5 sample claims
- Several status updates (simulating claim processing)
- Change feed entries (captured by triggers automatically)

Watch the backend logs - you should see the change feed processor picking up and processing the changes!

## Step 3: Verify It's Working

### Check the API

```bash
# Health check
curl http://localhost:8080/api/v1/actuator/health

# Dashboard summary (will be empty until you add payers/payees via admin portal)
curl http://localhost:8080/api/v1/dashboard/summary
```

### Check the Database

```bash
# View unprocessed changes
sqlite3 ./data/edi835-local.db "SELECT * FROM v_unprocessed_changes;"

# View all claims
sqlite3 ./data/edi835-local.db "SELECT id, claim_number, status FROM claims;"

# View change feed checkpoint
sqlite3 ./data/edi835-local.db "SELECT * FROM changefeed_checkpoint;"
```

## Step 4: Start the Frontend

Now that the backend is running, start the admin portal:

```bash
# In a new terminal, navigate to frontend directory
cd ../edi835-admin-portal

# Install dependencies (first time only)
npm install

# Start the dev server
npm run dev
```

Open your browser to `http://localhost:3000` and you'll see the admin portal!

## What's Next?

### Add Payers and Payees

1. Open the admin portal at `http://localhost:3000`
2. Navigate to **Configuration → Payers**
3. Click **Add Payer** and fill in the form
4. Navigate to **Configuration → Payees**
5. Click **Add Payee** and fill in the form

### Insert Test Claims

You can insert claims directly into the SQLite database:

```sql
-- Connect to the database
sqlite3 ./data/edi835-local.db

-- Insert a new claim
INSERT INTO claims (id, claim_number, patient_name, payer_id, payee_id,
                    service_date, total_charge, total_paid, status)
VALUES ('claim-test', 'CLM-TEST-001', 'Test Patient', 'PAYER001', 'PAYEE001',
        '2024-01-20', 5000.00, 0.00, 'PENDING');

-- Update claim status (this will trigger the change feed!)
UPDATE claims
SET status = 'PROCESSED', total_paid = 4500.00
WHERE id = 'claim-test';

-- View the change that was captured
SELECT * FROM data_changes
WHERE row_id = 'claim-test'
ORDER BY changed_at DESC;
```

The change feed processor will automatically detect and process these changes!

## Monitoring the Change Feed

### View Logs

The backend logs show the change feed activity:

```
2024-01-20 10:30:05 - SQLite Change Feed Processor initialized
2024-01-20 10:30:10 - Polling for changes...
2024-01-20 10:30:10 - Found 3 unprocessed changes to process
2024-01-20 10:30:10 - Processing batch of 3 changes
2024-01-20 10:30:10 - Processed 3 changes (3 successful, 0 errors)
```

### Database Queries

```sql
-- Count unprocessed changes
SELECT COUNT(*) FROM data_changes WHERE processed = 0;

-- View recent changes
SELECT change_id, table_name, operation,
       json_extract(new_values, '$.status') as status,
       changed_at, processed
FROM data_changes
ORDER BY changed_at DESC
LIMIT 10;

-- View checkpoint progress
SELECT consumer_id, last_feed_version, last_sequence_number,
       total_processed, last_checkpoint_at
FROM changefeed_checkpoint;

-- View feed version statistics
SELECT * FROM v_feed_version_summary;
```

## Troubleshooting

### Backend Won't Start

**Error: "Port 8080 already in use"**
```bash
# Check what's using port 8080
lsof -i :8080

# Kill the process or change the port
export SERVER_PORT=8081
mvn spring-boot:run
```

**Error: "Cannot create database file"**
```bash
# Create the data directory manually
mkdir -p ./data

# Ensure you have write permissions
chmod 755 ./data
```

### Changes Not Processing

**Check the database file exists:**
```bash
ls -lh ./data/edi835-local.db
```

**Check triggers are installed:**
```bash
sqlite3 ./data/edi835-local.db "SELECT name FROM sqlite_master WHERE type = 'trigger';"
```

You should see:
- claims_insert_trigger
- claims_update_trigger
- claims_delete_trigger

**Check for errors in logs:**
```bash
tail -f logs/edi835-processor.log
```

### Database Locked Error

SQLite is single-writer. If you see "database is locked":

1. Close any SQLite GUI tools
2. Make sure you're not running multiple `sqlite3` CLI sessions
3. The backend uses `maximum-pool-size: 1` to prevent this

### Reset Everything

To start fresh:

```bash
# Stop the backend (Ctrl+C)

# Delete the database
rm ./data/edi835-local.db

# Restart the backend
mvn spring-boot:run

# The database will be recreated automatically
```

## Advanced: Replay Scenarios

The version-based change feed allows you to replay changes:

```sql
-- Mark all changes as unprocessed
UPDATE data_changes SET processed = 0;

-- Reset checkpoint to start from beginning
UPDATE changefeed_checkpoint
SET last_feed_version = 0, last_sequence_number = 0;

-- Increment feed version for replay
UPDATE data_changes
SET feed_version = feed_version + 1;
```

The change feed processor will reprocess all changes as if they're new!

## Configuration Reference

All SQLite-specific settings are in `src/main/resources/application-sqlite.yml`:

```yaml
spring:
  profiles:
    active: sqlite  # This is the default

changefeed:
  sqlite:
    enabled: true
    poll-interval-ms: 5000    # Poll every 5 seconds
    batch-size: 100            # Process 100 changes at a time
    auto-version: true         # Auto-increment feed versions
    consumer-id: edi835-processor-default
    init-schema: true          # Auto-initialize schema
```

## Switching to Production (Cosmos DB)

When ready for production:

1. Update `application.yml`:
   ```yaml
   spring:
     profiles:
       active: cosmos

   changefeed:
     cosmos:
       enabled: true
     sqlite:
       enabled: false
   ```

2. Configure Cosmos DB connection (see main README.md)

3. Restart the backend

The same code will now use Cosmos DB instead of SQLite!

## Resources

- Full SQLite documentation: [README-SQLITE.md](./README-SQLITE.md)
- Main project README: [../README.md](../README.md)
- SQL schema: [src/main/resources/db/sqlite/schema.sql](./src/main/resources/db/sqlite/schema.sql)
- Sample data: [src/main/resources/db/sqlite/sample-data.sql](./src/main/resources/db/sqlite/sample-data.sql)

## Summary

✅ **No external database setup required**
✅ **Automatic schema initialization**
✅ **Sample data included**
✅ **Change feed works out of the box**
✅ **Easy to switch to production Cosmos DB**

Happy coding! 🚀

# Troubleshooting Guide

## Common Startup Issues

### 1. Cosmos DB Initialization Error (FIXED)

**Error:**
```
Illegal base64 character 2d
Error creating bean with name 'cosmosAsyncClient'
```

**Cause:**
Cosmos DB configuration was being initialized even when using SQLite mode.

**Fix Applied:**
- Added `@ConditionalOnProperty` to `CosmosDbConfig.java`
- Added `@ConditionalOnProperty` to `ClaimChangeFeedProcessor.java`
- Added `@ConditionalOnProperty` to `ChangeFeedConfig.java`

These beans now only initialize when `changefeed.cosmos.enabled=true`.

**Verification:**
```bash
# Should start successfully with SQLite (default)
mvn spring-boot:run

# Look for this in logs:
# "SQLite Change Feed Processor initialized"
# No Cosmos DB errors should appear
```

---

### 2. PaymentMethod Repository Error (FIXED)

**Error:**
```
No property 'paymentMethodCode' found for type 'PaymentMethod'
```

**Cause:**
Repository method `findByPaymentMethodCode()` referenced a non-existent field.

**Fix Applied:**
- Removed invalid `findByPaymentMethodCode()` from `PaymentMethodRepository`
- Updated `ConfigurationService.getPaymentMethod()` to use `methodType` enum
- Added `getPaymentMethodByCode()` for string-based lookups

**Verification:**
```bash
mvn clean compile
# Should compile without errors
```

---

### 3. Database File Permissions

**Error:**
```
Could not create database file
Permission denied
```

**Fix:**
```bash
# Create data directory with proper permissions
mkdir -p ./data
chmod 755 ./data

# If still failing, check parent directory permissions
ls -ld .
```

---

### 4. Port Already in Use

**Error:**
```
Port 8080 already in use
Web server failed to start
```

**Fix:**
```bash
# Option 1: Find and kill process using port 8080
lsof -ti:8080 | xargs kill -9

# Option 2: Use different port
export SERVER_PORT=8081
mvn spring-boot:run

# Option 3: Set in application.yml
server:
  port: 8081
```

---

### 5. SQLite Database Locked

**Error:**
```
database is locked
```

**Cause:**
Multiple processes accessing SQLite database simultaneously.

**Fix:**
```bash
# Close all SQLite connections
# 1. Stop any sqlite3 CLI sessions
# 2. Close any DB browser applications
# 3. Restart the backend

# If persistent, delete and recreate database
rm ./data/edi835-local.db
mvn spring-boot:run
```

---

### 6. Schema Initialization Failed

**Error:**
```
Failed to execute SQL script
Table already exists
```

**Fix:**
The schema script uses `CREATE TABLE IF NOT EXISTS`, so this shouldn't happen.
If it does:

```bash
# Option 1: Fresh start
rm ./data/edi835-local.db
mvn spring-boot:run

# Option 2: Disable auto-init
# Edit application-sqlite.yml:
changefeed:
  sqlite:
    init-schema: false
```

---

### 7. Frontend Can't Connect to Backend

**Error in Browser Console:**
```
Failed to fetch
Network error
CORS error
```

**Check:**
1. Backend is running: `curl http://localhost:8080/api/v1/actuator/health`
2. Correct API URL in frontend `.env`:
   ```
   VITE_API_BASE_URL=http://localhost:8080/api/v1
   ```
3. CORS is enabled in backend (it should be by default)

**Fix:**
```bash
# Restart both backend and frontend
# Backend:
cd edi835-processor
mvn spring-boot:run

# Frontend (new terminal):
cd edi835-admin-portal
npm run dev
```

---

### 8. Triggers Not Created

**Symptoms:**
- Changes to claims table not appearing in data_changes
- Change feed not processing anything

**Check:**
```bash
sqlite3 ./data/edi835-local.db "SELECT name FROM sqlite_master WHERE type='trigger';"
```

Should see:
- claims_insert_trigger
- claims_update_trigger
- claims_delete_trigger

**Fix:**
```bash
# Recreate database with schema
rm ./data/edi835-local.db
mvn spring-boot:run

# Or manually run schema script
sqlite3 ./data/edi835-local.db < src/main/resources/db/sqlite/schema.sql
```

---

### 9. No Changes Being Processed

**Check Logs:**
```bash
tail -f logs/edi835-processor.log | grep "Change Feed"
```

Should see every 5 seconds:
```
Polling for changes...
Found X unprocessed changes to process
```

**If not polling:**
1. Verify SQLite change feed is enabled:
   ```bash
   grep "sqlite.enabled" src/main/resources/application-sqlite.yml
   # Should be: enabled: true
   ```

2. Check if processor initialized:
   ```bash
   grep "SQLite Change Feed Processor initialized" logs/edi835-processor.log
   ```

3. Verify data exists:
   ```bash
   sqlite3 ./data/edi835-local.db "SELECT COUNT(*) FROM data_changes WHERE processed = 0;"
   ```

**Fix:**
```bash
# Force a poll by inserting test data
sqlite3 ./data/edi835-local.db <<EOF
INSERT INTO claims (id, claim_number, patient_name, payer_id, payee_id,
                    service_date, total_charge, total_paid, status)
VALUES ('test-claim', 'TEST-001', 'Test', 'PAYER001', 'PAYEE001',
        '2024-01-20', 1000.00, 0.00, 'PROCESSED');
EOF

# Watch logs - should process within 5 seconds
```

---

### 10. Maven Build Fails

**Error:**
```
Failed to execute goal maven-compiler-plugin
Compilation failure
```

**Check Java Version:**
```bash
java -version
# Should be 17 or higher

# If wrong version:
export JAVA_HOME=/path/to/jdk-17
```

**Clean and Rebuild:**
```bash
mvn clean install -DskipTests
```

---

## Diagnostic Commands

### Check All Services

```bash
# Backend health
curl http://localhost:8080/api/v1/actuator/health

# Frontend
curl http://localhost:3000

# Database file
ls -lh ./data/edi835-local.db

# Logs
tail -50 logs/edi835-processor.log
```

### Database Inspection

```bash
# Connect to database
sqlite3 ./data/edi835-local.db

# List all tables
.tables

# Check record counts
SELECT 'Claims', COUNT(*) FROM claims
UNION ALL
SELECT 'Changes', COUNT(*) FROM data_changes
UNION ALL
SELECT 'Unprocessed', COUNT(*) FROM data_changes WHERE processed = 0;

# View recent changes
SELECT * FROM v_recent_changes LIMIT 10;

# Exit
.quit
```

### Application Configuration

```bash
# View active profile
grep "spring.profiles.active" src/main/resources/application.yml

# View SQLite config
cat src/main/resources/application-sqlite.yml

# View environment
env | grep -E "SPRING|DB|COSMOS"
```

---

## Getting Help

### Enable Debug Logging

Edit `application-sqlite.yml`:

```yaml
logging:
  level:
    com.healthcare.edi835: TRACE
    org.springframework.data: DEBUG
    org.hibernate: DEBUG
```

Restart and check logs:
```bash
mvn spring-boot:run > debug.log 2>&1
```

### Collect Diagnostic Info

```bash
# System info
java -version
mvn -version
node -version
npm -version

# Application info
ls -lR edi835-processor/target/classes/
grep -r "ConditionalOnProperty" edi835-processor/src/

# Database info
sqlite3 ./data/edi835-local.db ".schema" > schema-dump.sql
sqlite3 ./data/edi835-local.db "SELECT * FROM v_feed_version_summary;" > feed-stats.txt
```

---

## Reset Everything

Nuclear option - start completely fresh:

```bash
# Stop all processes (Ctrl+C in all terminals)

# Backend cleanup
cd edi835-processor
rm -rf target/
rm -rf logs/
rm -rf data/
mvn clean

# Frontend cleanup
cd ../edi835-admin-portal
rm -rf node_modules/
rm -rf dist/

# Rebuild
cd ../edi835-processor
mvn clean install -DskipTests

cd ../edi835-admin-portal
npm install
npm run build

# Start fresh
cd ../edi835-processor
mvn spring-boot:run
```

---

## Profile-Specific Issues

### SQLite Mode (Default)

**Required:**
- Nothing! It should just work.

**Configuration File:**
- `application-sqlite.yml`

**Database Location:**
- `./data/edi835-local.db`

### Cosmos DB Mode

**Required:**
- Azure Cosmos DB account
- Valid endpoint and key
- PostgreSQL for configuration

**Enable:**
```yaml
# application.yml
spring:
  profiles:
    active: cosmos

changefeed:
  cosmos:
    enabled: true
  sqlite:
    enabled: false
```

**Common Issues:**
- Invalid Cosmos DB key (base64 error)
- Container doesn't exist
- Lease container doesn't exist
- Network connectivity issues

---

## Performance Issues

### Slow Startup

Normal startup time: 15-30 seconds

If slower:
1. Check disk I/O (SQLite performance)
2. Reduce log level to INFO
3. Disable metrics: `management.metrics.export.prometheus.enabled: false`

### High CPU Usage

If CPU constantly high:
1. Check poll interval (default: 5 seconds)
2. Reduce batch size if processing many changes
3. Check for infinite loops in processing logic

### Memory Issues

If OutOfMemoryError:
```bash
# Increase heap size
export MAVEN_OPTS="-Xmx2g"
mvn spring-boot:run

# Or for JAR:
java -Xmx2g -jar target/edi835-processor-1.0.0-SNAPSHOT.jar
```

---

## FAQ

**Q: Can I use PostgreSQL instead of SQLite for local dev?**
A: Yes, but you'll need to set up PostgreSQL and modify the change feed implementation.

**Q: Can I run multiple instances?**
A: Not recommended with SQLite (single-writer limitation). Use Cosmos DB for distributed processing.

**Q: How do I clear all processed changes?**
A: `sqlite3 ./data/edi835-local.db "DELETE FROM data_changes WHERE processed = 1;"`

**Q: How do I see what the change feed is processing?**
A: Watch logs with `tail -f logs/edi835-processor.log | grep -E "Processing|Processed"`

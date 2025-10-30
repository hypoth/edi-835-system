# Root Cause Analysis: Scheduled Delivery Job Failure with SQLite

## Problem Summary

The scheduled file delivery job fails with a SQLite JDBC driver error when attempting to retrieve pending deliveries.

**Error**:
```
SQL Error: 0, SQLState: null
ERROR o.h.e.jdbc.spi.SqlExceptionHelper - not implemented by SQLite JDBC driver
ERROR c.h.e.s.ScheduledDeliveryService - Error in scheduled delivery job:
Could not extract column [14] from JDBC ResultSet [not implemented by SQLite JDBC driver]
```

---

## Error Flow

### 1. Scheduled Job Triggers
**File**: `ScheduledDeliveryService.java:78`
```java
@Scheduled(cron = "${file-delivery.scheduler.cron:0 */5 * * * ?}")
public void autoDeliverPendingFiles() {
    log.info("Starting scheduled file delivery job...");
    List<FileGenerationHistory> pendingFiles = deliveryService.getPendingDeliveries(); // ← Line 88
    // ...
}
```

### 2. Service Method Call
**File**: `FileDeliveryService.java:348`
```java
public List<FileGenerationHistory> getPendingDeliveries() {
    return historyRepository.findPendingDeliveries(); // ← Line 349
}
```

### 3. Repository Native Query Executes
**File**: `FileGenerationHistoryRepository.java:40-45`
```java
@Query(value = "SELECT file_id, bucket_id, generated_file_name, file_path, file_size, " +
               "claim_count, payer_id, payee_id, generated_at, delivery_status, " +
               "delivered_at, delivery_attempt_count, error_message, NULL as file_content " +
               "FROM file_generation_history WHERE delivery_status = 'PENDING' ORDER BY generated_at ASC",
       nativeQuery = true)
List<FileGenerationHistory> findPendingDeliveries();
```

### 4. Column Mapping Fails
Hibernate attempts to map the 14 columns from the native SQL query to the `FileGenerationHistory` entity, but encounters errors.

---

## Root Cause Analysis

### Issue 1: Column Mismatch Between Query and Entity

**Native Query Columns** (14 columns):
1. `file_id` → maps to `id` (UUID)
2. `bucket_id` → maps to `bucket` (FK, but query returns UUID not entity)
3. `generated_file_name` → maps to `generatedFileName`
4. `file_path` → maps to `filePath`
5. **`file_size`** → **❌ MISMATCH**: Entity has `file_size_bytes` not `file_size`
6. `claim_count` → maps to `claimCount`
7. **`payer_id`** → **❌ DOES NOT EXIST** in FileGenerationHistory entity
8. **`payee_id`** → **❌ DOES NOT EXIST** in FileGenerationHistory entity
9. `generated_at` → maps to `generatedAt`
10. `delivery_status` → maps to `deliveryStatus`
11. `delivered_at` → maps to `deliveredAt`
12. `delivery_attempt_count` → maps to `deliveryAttemptCount`
13. `error_message` → maps to `errorMessage`
14. `NULL as file_content` → maps to `fileContent` (byte[] @Lob)

**Entity Fields** (from `FileGenerationHistory.java`):
```java
@Entity
public class FileGenerationHistory {
    private UUID id;                          // 1
    private EdiFileBucket bucket;             // 2 (ManyToOne relationship)
    private String generatedFileName;         // 3
    private String filePath;                  // 4
    private Long fileSizeBytes;               // 5 ← Note: file_size_BYTES
    private Integer claimCount;               // 6
    private BigDecimal totalAmount;           // 7 ← MISSING from query!
    private LocalDateTime generatedAt;        // 8
    private String generatedBy;               // 9 ← MISSING from query!
    private DeliveryStatus deliveryStatus;    // 10
    private LocalDateTime deliveredAt;        // 11
    private Integer deliveryAttemptCount;     // 12
    private String errorMessage;              // 13
    private byte[] fileContent;               // 14 (@Lob)
}
```

### Issue 2: Column Name Mismatch

The query selects `file_size` but the entity column is named `file_size_bytes`:

**Query**: `file_size`
**Entity**: `@Column(name = "file_size_bytes")` (line 46-47 of entity)

### Issue 3: Extra Columns in Query

The query includes `payer_id` and `payee_id` which:
- Do **NOT** exist in the `FileGenerationHistory` entity
- Exist in the `edi_file_buckets` table (linked via `bucket_id` FK)
- Cause column count and type mismatch during result mapping

### Issue 4: SQLite LOB Handling

Column [14] is `file_content` with `NULL as file_content`:
- Entity field is `byte[] fileContent` with `@Lob` annotation (line 77-80)
- SQLite JDBC driver has limited LOB support
- Even when explicitly set to NULL, SQLite driver fails during column extraction

---

## Why This Works in PostgreSQL But Fails in SQLite

1. **PostgreSQL** is more lenient with column mapping and type coercion
2. **PostgreSQL** has full LOB support for BYTEA and TEXT columns
3. **SQLite** JDBC driver:
   - Limited support for `@Lob` annotation
   - Strict column count matching requirements
   - Cannot handle NULL values for LOB columns in result sets properly

---

## Solution Options

### Option 1: Fix the Native Query ✅ **RECOMMENDED**

Correct the native query to match the entity structure exactly:

```java
@Query(value = "SELECT id as file_id, bucket_id, generated_file_name, file_path, " +
               "file_size_bytes, claim_count, total_amount, generated_at, generated_by, " +
               "delivery_status, delivered_at, delivery_attempt_count, error_message, " +
               "file_content " +
               "FROM file_generation_history " +
               "WHERE delivery_status = 'PENDING' " +
               "ORDER BY generated_at ASC",
       nativeQuery = true)
List<FileGenerationHistory> findPendingDeliveries();
```

**Changes**:
- Use `id` not `file_id` (or alias properly)
- Change `file_size` → `file_size_bytes`
- Remove `payer_id` and `payee_id` (not in entity)
- Add `total_amount` (exists in entity, missing from query)
- Add `generated_by` (exists in entity, missing from query)
- Remove `NULL as file_content` and select actual column

**Pros**:
- Matches entity structure
- Proper column mapping

**Cons**:
- Still includes `file_content` LOB which may cause SQLite issues

---

### Option 2: Use JPQL Instead of Native SQL ✅ **BEST PRACTICE**

Replace native SQL with JPQL to let Hibernate handle mapping:

```java
@Query("SELECT f FROM FileGenerationHistory f " +
       "WHERE f.deliveryStatus = 'PENDING' " +
       "ORDER BY f.generatedAt ASC")
List<FileGenerationHistory> findPendingDeliveries();
```

**Pros**:
- No column mapping issues
- Database-agnostic (works with both PostgreSQL and SQLite)
- Hibernate handles type conversions
- Simpler and more maintainable

**Cons**:
- Loads ALL columns including LOB `file_content`
- May cause performance issues with large files

---

### Option 3: Exclude file_content Using EntityGraph ✅ **OPTIMAL**

Use JPQL with `@EntityGraph` to exclude LOB column:

```java
@EntityGraph(attributePaths = {}) // Load no lazy attributes
@Query("SELECT f FROM FileGenerationHistory f " +
       "WHERE f.deliveryStatus = 'PENDING' " +
       "ORDER BY f.generatedAt ASC")
List<FileGenerationHistory> findPendingDeliveries();
```

Or create a custom query that explicitly doesn't fetch file_content:

```java
@Query("SELECT new FileGenerationHistory(" +
       "f.id, f.bucket, f.generatedFileName, f.filePath, f.fileSizeBytes, " +
       "f.claimCount, f.totalAmount, f.generatedAt, f.generatedBy, " +
       "f.deliveryStatus, f.deliveredAt, f.deliveryAttemptCount, f.errorMessage) " +
       "FROM FileGenerationHistory f " +
       "WHERE f.deliveryStatus = 'PENDING' " +
       "ORDER BY f.generatedAt ASC")
List<FileGenerationHistory> findPendingDeliveries();
```

**Note**: This requires adding a constructor to `FileGenerationHistory` that excludes `fileContent`.

---

### Option 4: Use DTO Projection ✅ **RECOMMENDED FOR DELIVERY**

Create a lightweight DTO for delivery operations:

```java
// New interface projection
public interface FileDeliveryInfo {
    UUID getFileId();
    String getGeneratedFileName();
    String getFilePath();
    Long getFileSizeBytes();
    LocalDateTime getGeneratedAt();
    Integer getDeliveryAttemptCount();
}

// Repository method
@Query("SELECT f.id as fileId, f.generatedFileName as generatedFileName, " +
       "f.filePath as filePath, f.fileSizeBytes as fileSizeBytes, " +
       "f.generatedAt as generatedAt, f.deliveryAttemptCount as deliveryAttemptCount " +
       "FROM FileGenerationHistory f " +
       "WHERE f.deliveryStatus = 'PENDING' " +
       "ORDER BY f.generatedAt ASC")
List<FileDeliveryInfo> findPendingDeliveryInfo();
```

Then update `FileDeliveryService` to work with the DTO instead of full entity.

**Pros**:
- No LOB loading issues
- Better performance (only loads needed fields)
- SQLite compatible
- Clean separation of concerns

**Cons**:
- Requires creating DTO/interface
- Requires updating service method signatures

---

## Recommended Implementation

**Use Option 2 (JPQL)** for simplicity, combined with marking `fileContent` as lazy-loaded (already done in entity):

### Step 1: Update Repository Query

**File**: `FileGenerationHistoryRepository.java:40-45`

**Before**:
```java
@Query(value = "SELECT file_id, bucket_id, generated_file_name, file_path, file_size, " +
               "claim_count, payer_id, payee_id, generated_at, delivery_status, " +
               "delivered_at, delivery_attempt_count, error_message, NULL as file_content " +
               "FROM file_generation_history WHERE delivery_status = 'PENDING' ORDER BY generated_at ASC",
       nativeQuery = true)
List<FileGenerationHistory> findPendingDeliveries();
```

**After**:
```java
@Query("SELECT f FROM FileGenerationHistory f " +
       "WHERE f.deliveryStatus = 'PENDING' " +
       "ORDER BY f.generatedAt ASC")
List<FileGenerationHistory> findPendingDeliveries();
```

### Step 2: Verify fileContent is Lazy-Loaded

**File**: `FileGenerationHistory.java:77-80`

Already correct:
```java
@Lob
@Basic(fetch = FetchType.LAZY)  // ← Already lazy
@Column(name = "file_content", columnDefinition = "TEXT")
private byte[] fileContent;
```

### Step 3: Test

1. Restart application
2. Wait for scheduled job to run (every 5 minutes)
3. Check logs for successful execution
4. Verify no SQLite JDBC errors

---

## Alternative: Fix for SQLite Specifically

If you need to keep native query for performance, fix it to exclude LOB:

```java
@Query(value = "SELECT id, bucket_id, generated_file_name, file_path, " +
               "file_size_bytes, claim_count, total_amount, generated_at, generated_by, " +
               "delivery_status, delivered_at, delivery_attempt_count, error_message " +
               "FROM file_generation_history " +
               "WHERE delivery_status = 'PENDING' " +
               "ORDER BY generated_at ASC",
       nativeQuery = true)
List<Object[]> findPendingDeliveriesRaw();
```

Then manually map to entity in service layer (avoiding LOB field).

---

## Verification Steps

### Before Fix
```bash
# Check logs for error
tail -f logs/edi835-processor.log | grep "not implemented by SQLite"

# Error appears every 5 minutes when scheduler runs
```

### After Fix
```bash
# Restart application
mvn spring-boot:run

# Wait for next scheduler execution (up to 5 minutes)
# Check logs
tail -f logs/edi835-processor.log | grep "Starting scheduled file delivery"

# Should see:
# "Starting scheduled file delivery job..."
# "No pending files found for delivery" (if no files)
# OR successful delivery messages
```

---

## Files Affected

- `FileGenerationHistoryRepository.java` - Query needs correction
- `ScheduledDeliveryService.java` - Calls the problematic query
- `FileDeliveryService.java` - Intermediary service
- `FileGenerationHistory.java` - Entity with LOB field

---

## Date
2025-10-17

## Status
**ANALYZED** - Fix pending implementation

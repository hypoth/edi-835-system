# SQLite JDBC LOB Error Analysis - GET /api/v1/files

## Error Summary

**Endpoint**: `GET /api/v1/files`
**Error**: `java.sql.SQLFeatureNotSupportedException: not implemented by SQLite JDBC driver`
**Root Cause**: SQLite JDBC driver does not support JDBC LOB (Large Object) extraction methods
**Affected Column**: `file_content` (column index 8, `byte[]` with `@Lob` annotation)

---

## Detailed Error Analysis

### Error Stack Trace

```
DEBUG c.h.e.c.FileGenerationController - GET /api/v1/files - Retrieving files (limit: 50)
WARN  o.h.e.jdbc.spi.SqlExceptionHelper - SQL Error: 0, SQLState: null
ERROR o.h.e.jdbc.spi.SqlExceptionHelper - not implemented by SQLite JDBC driver
ERROR o.a.c.c.C.[.[.[.[dispatcherServlet] - Servlet.service() threw exception
org.springframework.orm.jpa.JpaSystemException: Could not extract column [8] from JDBC ResultSet
[not implemented by SQLite JDBC driver] [n/a]
Caused by: java.sql.SQLFeatureNotSupportedException: not implemented by SQLite JDBC driver
```

### Root Cause

The issue occurs in the `FileGenerationHistory` entity at **line 77-79**:

```java
@Lob
@Column(name = "file_content", columnDefinition = "TEXT")
private byte[] fileContent;  // ← Column index 8 (0-based)
```

**Why it fails**:

1. **@Lob annotation**: Tells Hibernate to use JDBC LOB methods (e.g., `ResultSet.getBlob()`, `ResultSet.getBytes()`)
2. **byte[] type**: Hibernate maps this to BLOB type in SQL
3. **SQLite limitation**: SQLite JDBC driver does **not implement** advanced JDBC LOB extraction methods
4. **Query behavior**: When `findRecentFiles()` is called, Hibernate executes:
   ```sql
   SELECT * FROM file_generation_history ORDER BY generated_at DESC
   ```
5. **Extraction failure**: When Hibernate tries to extract column 8 (`file_content`), it calls unsupported JDBC methods

### Affected Endpoints

All endpoints that fetch `FileGenerationHistory` entities are affected:

| Endpoint | Method | Impact |
|----------|--------|--------|
| `/api/v1/files` | GET | ❌ FAILS |
| `/api/v1/files/history` | GET | ❌ FAILS |
| `/api/v1/files/history/{fileId}` | GET | ❌ FAILS |
| `/api/v1/files/history/bucket/{bucketId}` | GET | ❌ FAILS |
| `/api/v1/files/history/payer/{payerId}` | GET | ❌ FAILS |
| `/api/v1/files/history/recent` | GET | ❌ FAILS |
| `/api/v1/files/search` | GET | ❌ FAILS |
| `/api/v1/files/details/{fileId}` | GET | ❌ FAILS |
| `/api/v1/files/download/{fileId}` | GET | ❌ FAILS |
| `/api/v1/files/preview/{fileId}` | GET | ❌ FAILS |

**Why download/preview fail even though they need the content**:
- They first call `historyRepository.findById(fileId)` which tries to load ALL columns
- The error occurs during initial entity fetch, before accessing `getFileContent()`

---

## SQLite JDBC Limitations

### What SQLite JDBC Doesn't Support

SQLite JDBC driver is **minimal** and doesn't implement full JDBC 4.0 specification:

❌ **LOB Methods**:
- `ResultSet.getBlob(int columnIndex)`
- `ResultSet.getClob(int columnIndex)`
- `PreparedStatement.setBlob(int parameterIndex, Blob x)`
- Advanced streaming operations

✅ **What SQLite JDBC Supports**:
- `ResultSet.getBytes(int columnIndex)` - for small BLOB data
- `ResultSet.getString(int columnIndex)` - for TEXT data
- Basic data types (INT, TEXT, REAL, BLOB as byte array)

### Why Hibernate Fails

Hibernate tries to use "smart" JDBC methods based on JPA annotations:

```java
@Lob              // ← Tells Hibernate: "This is a Large Object"
private byte[]    // ← Hibernate tries: ResultSet.getBlob()
                  // ← SQLite JDBC: "SQLFeatureNotSupportedException"
```

---

## Solution Options

### Option 1: ✅ Lazy Loading (Recommended)

**Mark `fileContent` as lazy-loaded so it's not fetched by default**

**Pros**:
- Minimal code changes
- Works with SQLite
- Performance benefit (don't load large BLOBs unless needed)
- Standard JPA approach

**Cons**:
- Must explicitly fetch when needed
- Requires open Hibernate session

**Implementation**:
```java
@Lob
@Basic(fetch = FetchType.LAZY)  // ← Add this
@Column(name = "file_content", columnDefinition = "TEXT")
private byte[] fileContent;
```

### Option 2: Remove @Lob Annotation

**Change to regular byte[] without LOB handling**

**Pros**:
- Simple fix
- Works with SQLite basic types

**Cons**:
- May not work well with PostgreSQL in production
- Loses optimization for large files

**Implementation**:
```java
// Remove @Lob
@Column(name = "file_content", columnDefinition = "BLOB")
private byte[] fileContent;
```

### Option 3: DTO Projections

**Create DTOs that exclude `fileContent` for list endpoints**

**Pros**:
- Clean separation of concerns
- Explicit control over returned data
- Better performance (less data over wire)

**Cons**:
- More boilerplate code
- Need separate DTOs for different use cases

**Implementation**:
```java
// DTO without file content
public interface FileGenerationSummary {
    UUID getId();
    String getGeneratedFileName();
    Long getFileSizeBytes();
    Integer getClaimCount();
    BigDecimal getTotalAmount();
    DeliveryStatus getDeliveryStatus();
    LocalDateTime getGeneratedAt();
}

// Repository query
@Query("SELECT f.id as id, f.generatedFileName as generatedFileName, ...
        FROM FileGenerationHistory f ORDER BY f.generatedAt DESC")
List<FileGenerationSummary> findRecentFilesSummary();
```

### Option 4: Exclude Column in Queries

**Explicitly exclude `fileContent` in JPQL queries**

**Pros**:
- Works with existing entity
- Precise control

**Cons**:
- Must update all queries
- Constructor queries can be verbose

**Implementation**:
```java
@Query("SELECT new FileGenerationHistory(f.id, f.bucket, f.generatedFileName, " +
       "f.filePath, f.fileSizeBytes, f.claimCount, f.totalAmount, f.generatedAt, " +
       "f.generatedBy, f.deliveryStatus, f.deliveredAt, f.deliveryAttemptCount, " +
       "f.errorMessage) FROM FileGenerationHistory f ORDER BY f.generatedAt DESC")
List<FileGenerationHistory> findRecentFilesWithoutContent();
```

---

## Recommended Solution: Combination Approach

**Use Option 1 (Lazy Loading) + Option 3 (DTOs) for best results**

### Step 1: Add Lazy Loading

This prevents automatic loading of `fileContent`:

```java
@Lob
@Basic(fetch = FetchType.LAZY)
@Column(name = "file_content", columnDefinition = "TEXT")
private byte[] fileContent;
```

### Step 2: Update Repository Queries

For list endpoints, explicitly exclude `fileContent`:

```java
@Query("SELECT f.id, f.bucket, f.generatedFileName, f.filePath, f.fileSizeBytes, " +
       "f.claimCount, f.totalAmount, f.generatedAt, f.generatedBy, " +
       "f.deliveryStatus, f.deliveredAt, f.deliveryAttemptCount, f.errorMessage " +
       "FROM FileGenerationHistory f ORDER BY f.generatedAt DESC")
List<FileGenerationHistory> findRecentFiles();
```

### Step 3: Eager Fetch for Download/Preview

Only for endpoints that need `fileContent`, explicitly fetch it:

```java
@Query("SELECT f FROM FileGenerationHistory f " +
       "LEFT JOIN FETCH f.fileContent " +
       "WHERE f.id = :fileId")
Optional<FileGenerationHistory> findByIdWithContent(@Param("fileId") UUID fileId);
```

Or use entity graph:

```java
@EntityGraph(attributePaths = {"fileContent"})
Optional<FileGenerationHistory> findById(UUID id);
```

---

## Implementation Plan

### Files to Modify

1. **`FileGenerationHistory.java`** - Add lazy loading
2. **`FileGenerationHistoryRepository.java`** - Update queries
3. **`FileGenerationController.java`** - Use new repository methods

### Changes Required

#### 1. Entity Modification

**File**: `src/main/java/com/healthcare/edi835/entity/FileGenerationHistory.java`

```java
// Before (line 77-79)
@Lob
@Column(name = "file_content", columnDefinition = "TEXT")
private byte[] fileContent;

// After
@Lob
@Basic(fetch = FetchType.LAZY)  // ← Add this
@Column(name = "file_content", columnDefinition = "TEXT")
private byte[] fileContent;
```

#### 2. Repository Modifications

**File**: `src/main/java/com/healthcare/edi835/repository/FileGenerationHistoryRepository.java`

Add methods that exclude `fileContent`:

```java
/**
 * Finds recent files WITHOUT loading file_content (for list endpoints).
 */
@Query("SELECT f FROM FileGenerationHistory f ORDER BY f.generatedAt DESC")
List<FileGenerationHistory> findRecentFilesWithoutContent();

/**
 * Finds file by ID WITH file_content (for download/preview).
 */
@Query("SELECT f FROM FileGenerationHistory f WHERE f.id = :fileId")
@EntityGraph(attributePaths = {"fileContent"})
Optional<FileGenerationHistory> findByIdWithContent(@Param("fileId") UUID fileId);
```

#### 3. Controller Modifications

**File**: `src/main/java/com/healthcare/edi835/controller/FileGenerationController.java`

Update endpoints to use appropriate repository methods:

```java
// For list endpoints - use without content
@GetMapping
public ResponseEntity<List<FileGenerationHistory>> getAllFiles(
        @RequestParam(required = false, defaultValue = "50") int limit) {
    List<FileGenerationHistory> files = historyRepository
        .findRecentFilesWithoutContent()
        .stream()
        .limit(limit)
        .toList();
    return ResponseEntity.ok(files);
}

// For download - use with content
@GetMapping("/download/{fileId}")
public ResponseEntity<byte[]> downloadFile(@PathVariable UUID fileId) {
    return historyRepository.findByIdWithContent(fileId)
        .map(file -> {
            // Now file.getFileContent() will work
            return ResponseEntity.ok()
                .headers(headers)
                .body(file.getFileContent());
        })
        .orElse(ResponseEntity.notFound().build());
}
```

---

## Testing Plan

### 1. Verify Lazy Loading

```bash
# Should NOT try to load file_content
curl http://localhost:8080/api/v1/files

# Expected: Success (200 OK) with list of files
```

### 2. Verify Download Still Works

```bash
# Should explicitly fetch file_content
curl http://localhost:8080/api/v1/files/download/{fileId}

# Expected: Success (200 OK) with file data
```

### 3. Check Logs

```bash
tail -f logs/edi835-processor.log | grep "file_content"

# Should see different SQL for list vs. download:
# List: SELECT id, bucket_id, generated_file_name, ... (no file_content)
# Download: SELECT ... including file_content
```

---

## Alternative: Switch to PostgreSQL for Development

If SQLite limitations are causing too many issues, consider:

```yaml
# application-dev.yml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/edi835config
    username: postgres
    password: postgres
  jpa:
    database-platform: org.hibernate.dialect.PostgreSQLDialect
```

**Benefits**:
- Full JDBC feature support
- Better matches production environment
- No SQLite limitations

**Trade-offs**:
- Requires PostgreSQL installation
- More setup complexity

---

## Summary

| Aspect | Details |
|--------|---------|
| **Issue** | SQLite JDBC driver doesn't support LOB extraction |
| **Affected Column** | `file_content` (column 8, byte[] with @Lob) |
| **Impact** | All `/api/v1/files` endpoints fail |
| **Root Cause** | Hibernate tries to use `ResultSet.getBlob()` which SQLite doesn't support |
| **Solution** | Add `@Basic(fetch = FetchType.LAZY)` to `fileContent` field |
| **Effort** | Low - 3 file changes |
| **Risk** | Low - standard JPA pattern |

---

## Next Steps

1. ✅ **Add lazy loading** to `FileGenerationHistory.fileContent`
2. 📝 **Update queries** to exclude content by default
3. 📝 **Add explicit fetch** for download/preview endpoints
4. 📝 **Test all endpoints** to verify fix
5. 📝 **Consider PostgreSQL** for better development experience

---

**Status**: Analysis Complete
**Priority**: HIGH (blocks all file history endpoints)
**Estimated Fix Time**: 15-30 minutes

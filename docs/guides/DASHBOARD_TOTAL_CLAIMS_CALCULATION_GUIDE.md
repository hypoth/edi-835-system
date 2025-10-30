# Dashboard Total Claims Calculation

This document explains in detail how the "Total Claims" metric is calculated and displayed on the dashboard at `http://localhost:3000/dashboard`.

## Overview

The "Total Claims" metric shows the **total number of claim records** that have been processed by the system and logged in the `claim_processing_log` database table, regardless of their status (processed, accepted, or rejected).

---

## Complete Data Flow

### 1. Claim Ingestion & Logging

**Location**: `edi835-processor/src/main/java/com/healthcare/edi835/service/ClaimAggregationService.java:77, 246-257, 265-276`

When a claim enters the system (via Cosmos DB change feed or SQLite change feed), the `ClaimAggregationService` processes it:

```java
// For successfully processed claims:
private void logClaimProcessing(Claim claim, EdiFileBucket bucket) {
    ClaimProcessingLog log = ClaimProcessingLog.forProcessedClaim(
            claim.getId(),
            bucket,
            claim.getPayerId(),
            claim.getPayeeId(),
            claim.getTotalChargeAmount(),
            claim.getPaidAmount()
    );
    processingLogRepository.save(log);  // Saves to claim_processing_log table
}

// For rejected claims:
private void handleRejectedClaim(Claim claim, String reason) {
    ClaimProcessingLog log = ClaimProcessingLog.forRejectedClaim(
            claim.getId(),
            claim.getPayerId(),
            claim.getPayeeId(),
            reason
    );
    processingLogRepository.save(log);  // Also saves to claim_processing_log table
}
```

**Database Table**: `claim_processing_log`

Schema:
- **Columns**:
  - `id` (UUID/TEXT PRIMARY KEY)
  - `claim_id` (TEXT NOT NULL)
  - `bucket_id` (Foreign key to edi_file_buckets)
  - `payer_id` (TEXT)
  - `payee_id` (TEXT)
  - `claim_amount` (DECIMAL/REAL)
  - `paid_amount` (DECIMAL/REAL)
  - `adjustment_amount` (DECIMAL/REAL)
  - `status` (TEXT) - Values: 'PROCESSED', 'ACCEPTED', 'REJECTED'
  - `rejection_reason` (TEXT)
  - `processed_at` (TIMESTAMP)

- **Indexes**:
  - `idx_claim_log_bucket` on `bucket_id`
  - `idx_claim_log_claim` on `claim_id`

**File**: `edi835-processor/src/main/resources/db/sqlite/admin-schema.sql`

---

### 2. API Endpoint: GET /api/v1/dashboard/summary

**Location**: `edi835-processor/src/main/java/com/healthcare/edi835/controller/DashboardController.java:50-96`

When the frontend requests dashboard data, the backend executes:

```java
@GetMapping("/dashboard/summary")
public ResponseEntity<DashboardSummaryDTO> getDashboardSummary() {
    log.debug("GET /api/v1/dashboard/summary - Retrieving dashboard summary");

    // ... bucket and file statistics ...

    // Claim statistics
    long totalClaims = claimLogRepository.count();                    // Line 79
    long processedClaims = claimLogRepository.countProcessedClaims(); // Line 80
    long rejectedClaims = claimLogRepository.countRejectedClaims();   // Line 81

    DashboardSummaryDTO summary = DashboardSummaryDTO.builder()
            .totalBuckets(totalBuckets)
            .activeBuckets(activeBuckets)
            .pendingApprovalBuckets(pendingApproval)
            .totalFiles(totalFiles)
            .pendingDeliveryFiles(pendingDelivery)
            .failedDeliveryFiles(failedDelivery)
            .totalClaims(totalClaims)           // THIS IS THE TOTAL CLAIMS VALUE
            .processedClaims(processedClaims)
            .rejectedClaims(rejectedClaims)
            .build();

    return ResponseEntity.ok(summary);
}
```

---

### 3. Database Query Execution

**Location**: `edi835-processor/src/main/java/com/healthcare/edi835/repository/ClaimProcessingLogRepository.java:19, 73-80`

The `totalClaims` count is calculated using Spring Data JPA's built-in `count()` method:

```java
public interface ClaimProcessingLogRepository extends JpaRepository<ClaimProcessingLog, UUID> {
    // Inherited method from JpaRepository
    // This translates to SQL:
    // SELECT COUNT(*) FROM claim_processing_log
}
```

**Additional Metrics**:

```java
/**
 * Counts all processed claims (all statuses).
 */
@Query("SELECT COUNT(c) FROM ClaimProcessingLog c WHERE c.status IN ('PROCESSED', 'ACCEPTED')")
long countProcessedClaims();

/**
 * Counts all rejected claims.
 */
@Query("SELECT COUNT(c) FROM ClaimProcessingLog c WHERE c.status = 'REJECTED'")
long countRejectedClaims();
```

**Translated SQL Queries**:

| Method | SQL Query |
|--------|-----------|
| `count()` | `SELECT COUNT(*) FROM claim_processing_log` |
| `countProcessedClaims()` | `SELECT COUNT(*) FROM claim_processing_log WHERE status IN ('PROCESSED', 'ACCEPTED')` |
| `countRejectedClaims()` | `SELECT COUNT(*) FROM claim_processing_log WHERE status = 'REJECTED'` |

---

### 4. API Response Structure

**Location**: `edi835-processor/src/main/java/com/healthcare/edi835/model/dto/DashboardSummaryDTO.java:27`

The API returns a JSON response:

```json
{
  "totalBuckets": 5,
  "activeBuckets": 3,
  "pendingApprovalBuckets": 1,
  "totalFiles": 10,
  "pendingDeliveryFiles": 2,
  "failedDeliveryFiles": 0,
  "totalClaims": 150,
  "processedClaims": 140,
  "rejectedClaims": 10
}
```

**Field Descriptions**:

| Field | Description | Source |
|-------|-------------|--------|
| `totalClaims` | Total number of all claims (processed + rejected) | `count()` |
| `processedClaims` | Number of successfully processed claims | `countProcessedClaims()` |
| `rejectedClaims` | Number of rejected claims | `countRejectedClaims()` |

---

## Frontend Implementation

### 5. Data Fetching

**Location**:
- `edi835-admin-portal/src/pages/Dashboard.tsx:116-121`
- `edi835-admin-portal/src/services/dashboardService.ts:6-9`

The React frontend fetches data using React Query with automatic refresh:

```typescript
// Dashboard.tsx
const { data: summary, isLoading, error } = useQuery<DashboardSummary>({
  queryKey: ['dashboardSummary'],
  queryFn: dashboardService.getSummary,  // Calls API
  refetchInterval: 30000,  // Auto-refresh every 30 seconds
  retry: false,
});

// dashboardService.ts
export const dashboardService = {
  getSummary: async (): Promise<DashboardSummary> => {
    const response = await apiClient.get('/dashboard/summary');
    return response.data;
  },
  // ... other methods
};
```

**API Client Configuration**:
- Base URL: `http://localhost:8080/api/v1` (configured in `.env`)
- Axios instance with interceptors for error handling
- CORS enabled in backend `WebConfig.java` for `localhost:3000` and `localhost:5173`

---

### 6. UI Rendering

**Location**: `edi835-admin-portal/src/pages/Dashboard.tsx:278-286`

The total claims are displayed in a metric card:

```tsx
<Grid item xs={12} sm={6} md={3}>
  <MetricCard
    title="Total Claims"
    value={summary?.totalClaims || 0}    // ← DISPLAYED HERE
    icon={<AssessmentIcon />}
    color="#9c27b0"
    subtitle={`${summary?.processedClaims || 0} processed`}
  />
</Grid>
```

**Visual Display**:

```
┌─────────────────────────┐
│ Total Claims            │
│                         │
│      150        📊      │  ← Large purple number (color: #9c27b0)
│                         │
│ 140 processed           │  ← Subtitle showing processed count
└─────────────────────────┘
```

**MetricCard Component** (Dashboard.tsx:33-64):
- Displays title, value, icon, and optional subtitle
- Icon background color with 20% opacity
- Material-UI Card with responsive layout

---

## Calculation Summary

### Metrics Breakdown

| Metric | SQL Query | Status Filter | Description |
|--------|-----------|---------------|-------------|
| **Total Claims** | `SELECT COUNT(*) FROM claim_processing_log` | None | All claims (processed + rejected) |
| **Processed Claims** | `SELECT COUNT(*) ... WHERE status IN ('PROCESSED', 'ACCEPTED')` | PROCESSED, ACCEPTED | Successfully processed claims |
| **Rejected Claims** | `SELECT COUNT(*) ... WHERE status = 'REJECTED'` | REJECTED | Failed validation/processing |

### Claim Status Values

| Status | Set By | Description |
|--------|--------|-------------|
| `PROCESSED` | `ClaimProcessingLog.forProcessedClaim()` | Claim successfully aggregated into bucket |
| `ACCEPTED` | Alternative status | Another success status (future use) |
| `REJECTED` | `ClaimProcessingLog.forRejectedClaim()` | Claim failed validation or processing |

---

## Key Characteristics

### 1. Single Source of Truth
The `claim_processing_log` table is the authoritative source for all claim counts. Every claim that enters the system creates exactly one log entry.

### 2. Real-Time Updates
- New claims are logged immediately when processed via `ClaimAggregationService`
- Dashboard auto-refreshes every 30 seconds (configurable in `refetchInterval`)
- No caching layer between database and API

### 3. Transaction Safety
All logging happens within `@Transactional` methods in `ClaimAggregationService`, ensuring:
- Atomic operations (claim aggregation + logging)
- Data consistency across bucket updates and claim logs
- Rollback on errors

### 4. Performance Considerations

**Efficient Queries**:
- Simple `COUNT(*)` queries are highly optimized by database engines
- No joins required for total count
- Indexed columns (`bucket_id`, `claim_id`) speed up related queries

**Potential Bottlenecks**:
- As `claim_processing_log` grows, `COUNT(*)` may slow down
- Consider periodic archiving of old claim logs
- PostgreSQL handles millions of rows efficiently
- SQLite suitable for development/testing with smaller datasets

### 5. Error Handling

**Backend Errors** (DashboardController.java:52):
- Catches exceptions and returns appropriate HTTP status codes
- Logs errors for troubleshooting

**Frontend Errors** (Dashboard.tsx:155-177):
```tsx
if (summaryError) {
  return (
    <Alert severity="warning">
      Unable to connect to the backend API.
      The backend server may not be running at http://localhost:8080
    </Alert>
  );
}
```

---

## Related Dashboard Calculations

### Processing Rate

**Location**: `edi835-admin-portal/src/pages/Dashboard.tsx:327-329`

```tsx
<Typography variant="h5" color="success.main" fontWeight={600}>
  {summary?.totalClaims
    ? `${((summary.processedClaims / summary.totalClaims) * 100).toFixed(1)}%`
    : '0%'}
</Typography>
```

**Formula**: `(processedClaims / totalClaims) × 100`

**Example**:
- Total Claims: 150
- Processed Claims: 140
- Processing Rate: `(140 / 150) × 100 = 93.3%`

---

### Rejection Rate

**Location**: `edi835-admin-portal/src/pages/Dashboard.tsx:313-315`

```tsx
<Typography variant="caption" color="textSecondary">
  {summary?.totalClaims
    ? `${((summary.rejectedClaims / summary.totalClaims) * 100).toFixed(1)}% rejection rate`
    : ''}
</Typography>
```

**Formula**: `(rejectedClaims / totalClaims) × 100`

**Example**:
- Total Claims: 150
- Rejected Claims: 10
- Rejection Rate: `(10 / 150) × 100 = 6.7%`

---

## Data Flow Diagram

```
┌──────────────────────────────────────────────────────────────────┐
│                     CLAIM INGESTION                              │
│                                                                  │
│  Cosmos DB Change Feed  OR  SQLite Change Feed                  │
│           │                         │                            │
│           └─────────┬───────────────┘                           │
│                     ▼                                            │
│        ClaimAggregationService.aggregateClaim()                 │
└──────────────────────────────────────────────────────────────────┘
                      │
                      ├─── Validate Claim ───┐
                      │                       │
            ┌─────────▼─────────┐   ┌────────▼────────┐
            │   Valid Claim     │   │  Invalid Claim  │
            │   (status OK)     │   │   (rejected)    │
            └─────────┬─────────┘   └────────┬────────┘
                      │                      │
                      ▼                      ▼
         logClaimProcessing()    handleRejectedClaim()
              status='PROCESSED'      status='REJECTED'
                      │                      │
                      └──────────┬───────────┘
                                 ▼
                    processingLogRepository.save()
                                 │
                                 ▼
                  ┌──────────────────────────┐
                  │  claim_processing_log    │
                  │  (Database Table)        │
                  └──────────────────────────┘
                                 │
                                 │ HTTP GET /api/v1/dashboard/summary
                                 │
                  ┌──────────────▼──────────────┐
                  │  DashboardController        │
                  │  - count()                  │
                  │  - countProcessedClaims()   │
                  │  - countRejectedClaims()    │
                  └──────────────┬──────────────┘
                                 │
                                 │ JSON Response
                                 │
                  ┌──────────────▼──────────────┐
                  │  React Query (Frontend)     │
                  │  - Auto-refresh: 30s        │
                  │  - Error handling           │
                  └──────────────┬──────────────┘
                                 │
                                 ▼
                  ┌──────────────────────────┐
                  │  Dashboard UI            │
                  │  MetricCard:             │
                  │  "Total Claims: 150"     │
                  │  "140 processed"         │
                  └──────────────────────────┘
```

---

## Testing the Total Claims Calculation

### Manual Testing Steps

1. **Start the Backend**:
   ```bash
   cd edi835-processor
   mvn spring-boot:run
   ```

2. **Initialize Database** (SQLite profile):
   ```bash
   sqlite3 ./data/edi835-local.db < src/main/resources/db/sqlite/admin-schema.sql
   sqlite3 ./data/edi835-local.db < src/main/resources/db/sqlite/sample-data.sql
   ```

3. **Check Initial Count**:
   ```bash
   curl http://localhost:8080/api/v1/dashboard/summary | jq '.totalClaims'
   ```

4. **Insert Test Claims** (via SQLite):
   ```sql
   INSERT INTO claim_processing_log (id, claim_id, payer_id, payee_id, status, processed_at)
   VALUES
     (lower(hex(randomblob(16))), 'CLM001', 'PAYER1', 'PAYEE1', 'PROCESSED', CURRENT_TIMESTAMP),
     (lower(hex(randomblob(16))), 'CLM002', 'PAYER1', 'PAYEE1', 'REJECTED', CURRENT_TIMESTAMP);
   ```

5. **Verify Count Increased**:
   ```bash
   curl http://localhost:8080/api/v1/dashboard/summary | jq '.totalClaims'
   # Should show original count + 2
   ```

6. **View in Frontend**:
   ```bash
   cd edi835-admin-portal
   npm run dev
   ```
   Navigate to `http://localhost:3000/dashboard`

---

## Troubleshooting

### Total Claims Shows 0

**Possible Causes**:
1. Database not initialized
   - **Solution**: Run schema initialization scripts
2. No claims have been processed yet
   - **Solution**: Trigger claim ingestion via change feed
3. Database connection failure
   - **Check**: Application logs for connection errors

### Total Claims Not Updating

**Possible Causes**:
1. Change feed not running
   - **Check**: `SQLiteChangeFeedConfig` or `ChangeFeedConfig` initialization logs
2. Claims failing validation
   - **Check**: `ClaimAggregationService` logs for rejection messages
3. Frontend cache issue
   - **Solution**: Hard refresh browser (Ctrl+F5) or clear React Query cache

### Mismatch Between Database and Dashboard

**Possible Causes**:
1. Stale React Query cache
   - **Auto-resolves**: Wait 30 seconds for auto-refresh
   - **Manual**: Refresh page
2. Backend not restarted after schema changes
   - **Solution**: Restart Spring Boot application

---

## Configuration

### Backend Configuration

**File**: `edi835-processor/src/main/resources/application.yml` (or `application-sqlite.yml`)

```yaml
spring:
  datasource:
    # PostgreSQL (production)
    url: jdbc:postgresql://localhost:5432/edi835config

    # SQLite (development)
    url: jdbc:sqlite:./data/edi835-local.db

# Change Feed Configuration
changefeed:
  type: sqlite  # or cosmos
  sqlite:
    enabled: true
    poll-interval-ms: 5000
    batch-size: 100
```

### Frontend Configuration

**File**: `edi835-admin-portal/.env`

```
VITE_API_BASE_URL=http://localhost:8080/api/v1
```

**Dashboard Refresh Interval**: `edi835-admin-portal/src/pages/Dashboard.tsx:119`
```typescript
refetchInterval: 30000  // 30 seconds (configurable)
```

---

## Future Enhancements

### 1. Time-Based Filtering
Add support for filtering claims by date range:
```java
@Query("SELECT COUNT(c) FROM ClaimProcessingLog c WHERE c.processedAt >= :since")
long countClaimsSince(@Param("since") LocalDateTime since);
```

### 2. Real-Time WebSocket Updates
Replace polling with WebSocket for instant updates when new claims arrive.

### 3. Claim Count by Payer
Show breakdown of claims per payer on dashboard:
```java
@Query("SELECT c.payerId, COUNT(c) FROM ClaimProcessingLog c GROUP BY c.payerId")
List<Object[]> countClaimsByPayer();
```

### 4. Historical Trends
Track daily/weekly claim counts for trend analysis.

### 5. Performance Optimization
For very large datasets (millions of claims), consider:
- Materialized views with pre-calculated counts
- Caching layer (Redis) for frequently accessed metrics
- Database partitioning by date

---

## References

### Backend Files
- `edi835-processor/src/main/java/com/healthcare/edi835/controller/DashboardController.java`
- `edi835-processor/src/main/java/com/healthcare/edi835/repository/ClaimProcessingLogRepository.java`
- `edi835-processor/src/main/java/com/healthcare/edi835/service/ClaimAggregationService.java`
- `edi835-processor/src/main/java/com/healthcare/edi835/entity/ClaimProcessingLog.java`
- `edi835-processor/src/main/java/com/healthcare/edi835/model/dto/DashboardSummaryDTO.java`

### Frontend Files
- `edi835-admin-portal/src/pages/Dashboard.tsx`
- `edi835-admin-portal/src/services/dashboardService.ts`
- `edi835-admin-portal/src/services/apiClient.ts`
- `edi835-admin-portal/src/types/models.ts`

### Database Schema
- `edi835-processor/src/main/resources/db/sqlite/admin-schema.sql`
- `database/schema.sql` (PostgreSQL)

---

## Contact

For questions or issues related to the Total Claims calculation, please refer to:
- Project Documentation: `CLAUDE.md`
- Troubleshooting Guide: `TROUBLESHOOTING.md`
- SQLite Quick Start: `QUICKSTART-SQLITE.md`

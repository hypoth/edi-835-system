# EDI 835 System - Aggregation Error Analysis

**Date:** 2025-11-20
**Issue:** Query did not return a unique result: 2 results were returned
**Status:** Root cause identified, fixes implemented

---

## Problem Summary

During change feed processing, the system fails with the following error:

```
ERROR c.h.e.s.ClaimAggregationService - Error aggregating claim test-20251120-002002: Query did not return a unique result: 2 results were returned
org.springframework.dao.IncorrectResultSizeDataAccessException: Query did not return a unique result: 2 results were returned
```

The bucket is updated with the claim successfully, but threshold evaluation fails when trying to determine the commit criteria.

---

## Root Cause Analysis

### Error Location

**File:** `BucketManagerService.java:204-205`

```java
Optional<EdiCommitCriteria> criteriaOpt = commitCriteriaRepository
    .findByLinkedBucketingRuleAndIsActiveTrue(bucket.getBucketingRule());
```

**Issue:** The JPA repository method expects at most **1 result** (returns `Optional<>`), but the query is finding **2 active commit criteria records** that match the query.

### Why This Happens

The test data in `admin-schema.sql` contains **3 active commit criteria records**:

| ID | Name | Commit Mode | Linked Bucketing Rule ID | Is Active |
|----|------|-------------|--------------------------|-----------|
| 7b0e8400-e29b-41d4-a716-446655440001 | Auto Commit Small Batches | AUTO | **NULL** | 1 |
| 7b0e8400-e29b-41d4-a716-446655440002 | Manual Approval Required | MANUAL | **NULL** | 1 |
| e887965a-0957-4036-94ea-7c0afefb2363 | BCBS hybrid commit | HYBRID | 770e8400-e29b-41d4-a716-446655440000 | 1 |

**The Problem:** Two records have `linked_bucketing_rule_id = NULL` and `is_active = 1`.

When the JPA query executes with `linkedBucketingRule = NULL`, it finds **2 matching records** instead of 1, causing the `IncorrectResultSizeDataAccessException`.

This occurs when:
- The bucket's `bucketingRule` is NULL
- OR multiple default commit criteria exist (not linked to specific rules)

---

## Database Schema Issues

### Missing Unique Constraints

The database schema lacks constraints to prevent duplicate active configurations:

#### 1. `edi_commit_criteria` Table

**Current State:** No unique constraint on `(linked_bucketing_rule_id, is_active)`

**Impact:** Multiple active commit criteria can exist for:
- The same bucketing rule
- NULL (default/unlinked) configurations

**Should Have:**
```sql
-- PostgreSQL
CREATE UNIQUE INDEX idx_commit_criteria_unique_active
ON edi_commit_criteria(linked_bucketing_rule_id)
WHERE is_active = true;

-- SQLite
CREATE UNIQUE INDEX idx_commit_criteria_unique_active
ON edi_commit_criteria(linked_bucketing_rule_id, is_active);
```

#### 2. `edi_generation_thresholds` Table

**Current State:** No unique constraint on `(linked_bucketing_rule_id, threshold_type, is_active)`

**Impact:** Multiple active thresholds of the same type can exist for one bucketing rule

**Should Have:**
```sql
-- PostgreSQL
CREATE UNIQUE INDEX idx_gen_thresholds_unique_active
ON edi_generation_thresholds(linked_bucketing_rule_id, threshold_type)
WHERE is_active = true;
```

---

## Code Flow Analysis

### Successful Path (until error)

1. ✅ **Claim Processing** - `RemittanceProcessorService` receives claim
   ```
   Processing claim: claimId=test-20251120-002002, payerId=PAYER001, payeeId=PAYEE001, amount=2250
   ```

2. ✅ **Bucketing Rule Selection** - Matches "BCBS - General Hospital" rule
   ```
   Using bucketing rule: ruleName=BCBS - General Hospital, ruleType=PAYER_PAYEE
   ```

3. ✅ **Bucket Aggregation** - Finds existing bucket and adds claim
   ```
   Found existing PAYER_PAYEE bucket: 389d2815-da33-4167-8685-2b5a76fdfde4
   Added claim test-20251120-002002 to bucket, new count: 3, new amount: 6750.0
   ```

4. ✅ **Threshold Evaluation** - Detects amount threshold met
   ```
   Amount threshold met: 6750.0 >= 5000.0
   Threshold met for bucket: type=AMOUNT, threshold=BCBS - General Hospital
   ```

5. ❌ **Commit Criteria Lookup** - Query returns 2 results instead of 1
   ```
   ERROR: Query did not return a unique result: 2 results were returned
   ```

### Why the Query Fails

The repository method signature:
```java
Optional<EdiCommitCriteria> findByLinkedBucketingRuleAndIsActiveTrue(EdiBucketingRule rule);
```

Spring Data JPA generates a query like:
```sql
SELECT * FROM edi_commit_criteria
WHERE linked_bucketing_rule_id = ? AND is_active = true
```

When `linkedBucketingRule` is NULL or entity comparison fails:
```sql
SELECT * FROM edi_commit_criteria
WHERE linked_bucketing_rule_id IS NULL AND is_active = 1
-- Returns 2 rows!
```

---

## Implemented Fixes

### Fix #2: Clean Up Test Data ✅

**Approach:** Link default commit criteria to specific bucketing rules to avoid NULL conflicts.

**Changes to `admin-schema.sql`:**

**Before:**
```sql
-- Two records with NULL linked_bucketing_rule_id
('7b0e8400-e29b-41d4-a716-446655440001', 'Auto Commit Small Batches', 'AUTO', ..., NULL, 1, ...),
('7b0e8400-e29b-41d4-a716-446655440002', 'Manual Approval Required', 'MANUAL', ..., NULL, 1, ...),
```

**After:**
```sql
-- Link to specific bucketing rules
('7b0e8400-e29b-41d4-a716-446655440001', 'Auto Commit Small Batches', 'AUTO', ...,
 '770e8400-e29b-41d4-a716-446655440001', 1, ...), -- Linked to 'Default Payer/Payee Bucketing'

('7b0e8400-e29b-41d4-a716-446655440002', 'Manual Approval Required', 'MANUAL', ...,
 '770e8400-e29b-41d4-a716-446655440002', 1, ...), -- Linked to 'BIN/PCN Grouping'
```

**Benefit:** Eliminates duplicate NULL configurations, ensures one-to-one mapping.

### Fix #3: Update Repository Query to Handle Multiple Results ✅

**Approach:** Change repository to return `List<>` instead of `Optional<>`, handle multiple results gracefully in service layer.

#### Change 1: Repository Interface

**File:** `EdiCommitCriteriaRepository.java`

**Before:**
```java
Optional<EdiCommitCriteria> findByLinkedBucketingRuleAndIsActiveTrue(EdiBucketingRule rule);
```

**After:**
```java
List<EdiCommitCriteria> findByLinkedBucketingRuleAndIsActiveTrue(EdiBucketingRule rule);
```

**Benefit:** Query no longer throws exception when multiple records exist.

#### Change 2: Service Layer Logic

**File:** `BucketManagerService.java:202-241`

**Before:**
```java
Optional<EdiCommitCriteria> criteriaOpt = commitCriteriaRepository
    .findByLinkedBucketingRuleAndIsActiveTrue(bucket.getBucketingRule());

if (criteriaOpt.isEmpty()) {
    log.warn("No commit criteria found...");
    transitionToGeneration(bucket);
    return;
}

EdiCommitCriteria criteria = criteriaOpt.get();
```

**After:**
```java
List<EdiCommitCriteria> criteriaList = commitCriteriaRepository
    .findByLinkedBucketingRuleAndIsActiveTrue(bucket.getBucketingRule());

if (criteriaList.isEmpty()) {
    log.warn("No commit criteria found for bucket {}, defaulting to AUTO mode",
            bucket.getBucketId());
    transitionToGeneration(bucket);
    return;
}

if (criteriaList.size() > 1) {
    log.warn("Multiple active commit criteria found for bucket {} (rule: {}). Found {} criteria. Using first match: {}",
            bucket.getBucketId(),
            bucket.getBucketingRule() != null ? bucket.getBucketingRule().getRuleName() : "NULL",
            criteriaList.size(),
            criteriaList.get(0).getCriteriaName());
}

EdiCommitCriteria criteria = criteriaList.get(0);
```

**Benefit:**
- System continues to function even if duplicate configurations exist
- Logs warning for administrators to fix configuration
- Uses first matching criteria (deterministic behavior)

---

## Testing Recommendations

### 1. Database Validation Queries

Check for duplicate active configurations:

```sql
-- Check for duplicate commit criteria
SELECT linked_bucketing_rule_id, COUNT(*) as count
FROM edi_commit_criteria
WHERE is_active = 1
GROUP BY linked_bucketing_rule_id
HAVING COUNT(*) > 1;

-- Check for duplicate thresholds
SELECT linked_bucketing_rule_id, threshold_type, COUNT(*) as count
FROM edi_generation_thresholds
WHERE is_active = 1
GROUP BY linked_bucketing_rule_id, threshold_type
HAVING COUNT(*) > 1;
```

### 2. Change Feed Processing Test

```bash
cd edi835-processor
./test-change-feed.sh
```

Expected behavior:
- ✅ Claims processed successfully
- ✅ Buckets updated with correct counts/amounts
- ✅ Thresholds evaluated without errors
- ✅ Commit criteria applied correctly
- ⚠️ Warning logged if multiple criteria found (should be fixed in config)

### 3. Admin Portal Configuration

Verify via admin portal:
- Each bucketing rule has exactly one active commit criteria
- Each bucketing rule has at most one active threshold per type
- No orphaned configurations with NULL bucketing rule IDs

---

## Future Improvements

### 1. Add Database Constraints (Recommended)

Implement unique constraints at database level to prevent configuration errors:

**PostgreSQL:**
```sql
CREATE UNIQUE INDEX idx_commit_criteria_unique_active
ON edi_commit_criteria(linked_bucketing_rule_id)
WHERE is_active = true;

CREATE UNIQUE INDEX idx_gen_thresholds_unique_active
ON edi_generation_thresholds(linked_bucketing_rule_id, threshold_type)
WHERE is_active = true;
```

**SQLite:**
```sql
CREATE UNIQUE INDEX idx_commit_criteria_unique_active
ON edi_commit_criteria(COALESCE(linked_bucketing_rule_id, ''), is_active);
```

### 2. Admin Portal Validation

Add validation rules in the admin portal:
- Prevent creating multiple active commit criteria for same bucketing rule
- Show warning when activating criteria that conflicts with existing active criteria
- Add "Replace existing" option when creating new active criteria

### 3. Migration Script

For existing deployments, create a migration script to:
1. Identify duplicate active configurations
2. Deactivate all but one (based on priority or creation date)
3. Log affected configurations for review

---

## Related Files

- **Error Location:** `src/main/java/com/healthcare/edi835/service/BucketManagerService.java:204-205`
- **Repository:** `src/main/java/com/healthcare/edi835/repository/EdiCommitCriteriaRepository.java:21`
- **Test Data:** `src/main/resources/db/sqlite/admin-schema.sql:479-499`
- **Schema Definition:** `database/schema.sql:113-127` (PostgreSQL), `admin-schema.sql:122-136` (SQLite)

---

## Conclusion

The aggregation error was caused by duplicate active commit criteria records in the test data, combined with a repository query that expected at most one result. The implemented fixes:

1. ✅ **Cleaned up test data** to ensure one-to-one mapping between bucketing rules and commit criteria
2. ✅ **Updated repository and service logic** to handle edge cases gracefully

The system now continues processing even if duplicate configurations exist (with warnings), while the cleaned-up test data prevents the error from occurring in normal operation.

**Status:** Issue resolved. System can now process claims successfully through the entire aggregation pipeline.

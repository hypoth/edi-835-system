# Bucket Approval Transaction Flow

This document explains the transaction flow for bucket approval with automatic check assignment in the EDI 835 system.

## Database-Aware Transaction Strategy

The system supports two transaction strategies based on the underlying database:

### PostgreSQL Mode (Production)
- Uses **REQUIRES_NEW** propagation for check reservations
- Check reservation commits independently in its own transaction
- Requires **compensation logic** to release checks if subsequent operations fail
- Requires adequate connection pool size (minimum 3 connections)

### SQLite Mode (Development/Testing)
- Uses **single transaction** (participates in calling transaction)
- No separate transaction for check reservations
- No compensation needed - transaction rollback handles cleanup
- Works with single connection pool (SQLite limitation)

### Configuration Properties

```yaml
# application.yml (PostgreSQL)
check-reservation:
  use-separate-transaction: true   # Enable REQUIRES_NEW
  enable-compensation: true        # Enable compensation logic

# application-sqlite.yml
check-reservation:
  use-separate-transaction: false  # Single transaction mode
  enable-compensation: false       # Not needed - rollback handles it
```

## Configuration Prerequisites

For the flow described in this document to apply, the following configurations must be in place:

### 1. Check Payment Workflow Configuration
- **Workflow Mode**: `SEPARATE` (Approve first, then assign check separately)
- **Assignment Mode**: `AUTO` (System auto-assigns from pre-reserved check number ranges)

### 2. Commit Criteria Configuration
- **Commit Mode**: `MANUAL` (Requires admin approval via portal)

### 3. Check Reservations
- Active check reservations must exist for the payer
- Reservations must have available checks (`checksUsed < totalChecks`)

## The Call Chain

```
HTTP Request: POST /api/v1/approvals/approve/{bucketId}
    │
    ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│  ApprovalController.approveBucket()                                         │
│  - Extracts bucketId, approvedBy from request                               │
│  - Calls ApprovalWorkflowService                                            │
└─────────────────────────────────────────────────────────────────────────────┘
    │
    ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│  ApprovalWorkflowService.approveBucket()  [@Transactional - TX1]            │
│  - Validates bucket status is PENDING_APPROVAL                              │
│  - Creates BucketApprovalLog entry                                          │
│  - Sets approvedBy and approvedAt on bucket                                 │
│  - If paymentRequired=true, calls attemptAutoAssignment()                   │
└─────────────────────────────────────────────────────────────────────────────┘
    │
    ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│  ApprovalWorkflowService.attemptAutoAssignment()  [private method]          │
│  - Looks up workflow config via: bucket → bucketingRule → threshold         │
│  - Checks if workflowMode=SEPARATE and assignmentMode=AUTO                  │
│  - If yes, calls CheckPaymentService.assignCheckAutomaticallyFromBucket()   │
└─────────────────────────────────────────────────────────────────────────────┘
    │
    ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│  CheckPaymentService.assignCheckAutomaticallyFromBucket()  [@Transactional] │
│  - Extracts payerId from bucket                                             │
│  - Looks up Payer entity by business ID                                     │
│  - Calls assignCheckAutomatically()                                         │
└─────────────────────────────────────────────────────────────────────────────┘
    │
    ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│  CheckPaymentService.assignCheckAutomatically()  [@Transactional - TX1]     │
│  - Validates bucket exists                                                  │
│  - Calls reservationService.getAndReserveNextCheck()                        │
│  - On success: creates CheckPayment, updates bucket, creates audit log      │
│  - On failure: calls compensation to release reserved check (PostgreSQL)    │
│  - If bucket approved, calls transitionToGeneration()                       │
└─────────────────────────────────────────────────────────────────────────────┘
    │
    ├──────────────────────────────────────┐
    │                                      │
    ▼                                      ▼
┌──────────────────────────────┐    ┌──────────────────────────────────────────┐
│  CheckReservationService     │    │  BucketManagerService.transitionTo       │
│  .getAndReserveNextCheck()   │    │  Generation() [@Transactional - TX1]     │
│                              │    │  - Validates payment readiness           │
│  PostgreSQL: TX2 REQUIRES_NEW│    │  - Marks bucket as GENERATING            │
│  SQLite: Part of TX1         │    │  - Publishes BucketStatusChangeEvent     │
│                              │    └──────────────────────────────────────────┘
│  - Checks useSeparateTransaction config
│  - PostgreSQL: Uses TransactionTemplate with REQUIRES_NEW
│  - SQLite: Participates in TX1
│  - Finds available check reservation for payer
│  - Increments checksUsed
│  - Returns check info
└──────────────────────────────┘
```

## Transaction Boundaries

### Transaction 1 (TX1) - Main Approval Transaction
**Scope**: `ApprovalWorkflowService.approveBucket()` and all nested calls with default propagation

**Includes**:
- BucketApprovalLog creation
- Bucket approval metadata updates
- CheckPayment creation
- Bucket check assignment
- CheckAuditLog creation
- Bucket status transition to GENERATING
- BucketStatusChangeEvent publishing

**Behavior**: All operations succeed or fail together (atomic).

---

## PostgreSQL Mode: Separate Transactions

### Transaction 2 (TX2) - Check Reservation Transaction (PostgreSQL only)
**Scope**: `CheckReservationService.getAndReserveNextCheck()`

**Propagation**: `REQUIRES_NEW` (via `TransactionTemplate`)

**Includes**:
- Finding available reservation
- Incrementing `checksUsed` counter
- Saving reservation

**Behavior**: Commits independently of TX1. This ensures:
1. Check number is securely reserved before subsequent operations
2. No "lost" check numbers if TX1 fails after reservation
3. Requires compensation if TX1 fails after TX2 commits

**Implementation**: Uses programmatic transaction management:
```java
// In CheckReservationService
if (transactionConfig.isUseSeparateTransaction()) {
    return requiresNewTransactionTemplate.execute(status ->
            doReserveNextCheck(payerId, bucketId));
}
```

---

## SQLite Mode: Single Transaction

### All Operations in TX1 (SQLite)
**Scope**: Everything happens in the main transaction started by `approveBucket()`

**Includes**:
- All operations listed for TX1 above
- **Plus** check reservation (finds available, increments checksUsed)

**Behavior**: All operations succeed or fail together (fully atomic).

**Advantages**:
- No compensation logic needed - rollback handles everything
- Works with single connection pool (SQLite limitation)
- Simpler transaction flow

**Trade-offs**:
- Check reservation is not "locked in" until full commit
- If concurrent requests exist (rare in dev), potential race conditions

**Implementation**:
```java
// In CheckReservationService
if (!transactionConfig.isUseSeparateTransaction()) {
    // SQLite mode: Participate in existing transaction
    return doReserveNextCheck(payerId, bucketId);
}
```

## Compensation Logic (PostgreSQL Only)

Since TX2 commits independently in PostgreSQL mode, if TX1 fails after check reservation, we need compensation:

```
┌─────────────────────────────────────────────────────────────────────────────┐
│  Compensation Flow (PostgreSQL mode - on TX1 failure after TX2 success)     │
│                                                                             │
│  1. TX2 commits: Check CHK12345 reserved (checksUsed++)                     │
│  2. TX1 operation fails (e.g., transitionToGeneration() throws)             │
│  3. Catch block in assignCheckAutomatically() triggered                     │
│  4. Check if useSeparateTransaction=true (PostgreSQL mode)                  │
│  5. Call releaseReservedCheck() [REQUIRES_NEW - TX3]                        │
│  6. TX3 decrements checksUsed, commits                                      │
│  7. Check CHK12345 available again                                          │
│  8. Original exception re-thrown                                            │
└─────────────────────────────────────────────────────────────────────────────┘
```

### releaseReservedCheck() - Transaction 3 (TX3)
**Propagation**: `REQUIRES_NEW` (via `TransactionTemplate`)

**Purpose**: Return check to pool even if caller's transaction is in rollback state.

**Conditional Execution**:
```java
// In CheckPaymentService
} catch (Exception e) {
    if (reservationService.useSeparateTransaction()) {
        // PostgreSQL mode: compensation needed
        reservationService.releaseReservedCheck(checkNumber, reservationId, reason);
    } else {
        // SQLite mode: transaction rollback handles cleanup
        log.error("Transaction will rollback automatically (SQLite mode)");
    }
    throw e;
}
```

**Note**: In SQLite mode, `releaseReservedCheck()` is a no-op (logs and returns immediately).

## Success Scenario (PostgreSQL Mode)

```
Time    Transaction    Operation                                    Result
────────────────────────────────────────────────────────────────────────────
T1      TX1 START      approveBucket() called
T2      TX1            Create BucketApprovalLog                     Pending
T3      TX1            Set bucket.approvedBy, approvedAt            Pending
T4      TX2 START      getAndReserveNextCheck() called              [REQUIRES_NEW]
T5      TX2            Find reservation, increment checksUsed       Pending
T6      TX2 COMMIT     Check reservation committed                  COMMITTED
T7      TX1            Create CheckPayment                          Pending
T8      TX1            Update bucket.checkPayment                   Pending
T9      TX1            Create CheckAuditLog                         Pending
T10     TX1            transitionToGeneration()                     Pending
T11     TX1            Publish BucketStatusChangeEvent              Pending
T12     TX1 COMMIT     All TX1 operations committed                 COMMITTED
```

## Failure Scenario with Compensation (PostgreSQL Mode)

```
Time    Transaction    Operation                                    Result
────────────────────────────────────────────────────────────────────────────
T1      TX1 START      approveBucket() called
T2      TX1            Create BucketApprovalLog                     Pending
T3      TX1            Set bucket.approvedBy, approvedAt            Pending
T4      TX2 START      getAndReserveNextCheck() called              [REQUIRES_NEW]
T5      TX2            Find reservation, increment checksUsed       Pending
T6      TX2 COMMIT     Check CHK12345 reserved                      COMMITTED
T7      TX1            Create CheckPayment                          Pending
T8      TX1            Update bucket.checkPayment                   Pending
T9      TX1            transitionToGeneration() THROWS              EXCEPTION!
T10     TX1            Catch block triggered
T11     TX3 START      releaseReservedCheck() called                [REQUIRES_NEW]
T12     TX3            Decrement checksUsed                         Pending
T13     TX3 COMMIT     Check released back to pool                  COMMITTED
T14     TX1            Re-throw original exception
T15     TX1 ROLLBACK   All TX1 operations rolled back               ROLLED BACK
```

## Success Scenario (SQLite Mode)

```
Time    Transaction    Operation                                    Result
────────────────────────────────────────────────────────────────────────────
T1      TX1 START      approveBucket() called
T2      TX1            Create BucketApprovalLog                     Pending
T3      TX1            Set bucket.approvedBy, approvedAt            Pending
T4      TX1            getAndReserveNextCheck() called              [Same TX1]
T5      TX1            Find reservation, increment checksUsed       Pending
T6      TX1            Create CheckPayment                          Pending
T7      TX1            Update bucket.checkPayment                   Pending
T8      TX1            Create CheckAuditLog                         Pending
T9      TX1            transitionToGeneration()                     Pending
T10     TX1            Publish BucketStatusChangeEvent              Pending
T11     TX1 COMMIT     All operations committed together            COMMITTED
```

## Failure Scenario (SQLite Mode - No Compensation Needed)

```
Time    Transaction    Operation                                    Result
────────────────────────────────────────────────────────────────────────────
T1      TX1 START      approveBucket() called
T2      TX1            Create BucketApprovalLog                     Pending
T3      TX1            Set bucket.approvedBy, approvedAt            Pending
T4      TX1            getAndReserveNextCheck() called              [Same TX1]
T5      TX1            Find reservation, increment checksUsed       Pending
T6      TX1            Create CheckPayment                          Pending
T7      TX1            Update bucket.checkPayment                   Pending
T8      TX1            transitionToGeneration() THROWS              EXCEPTION!
T9      TX1            Catch block triggered (no compensation)
T10     TX1            Re-throw original exception
T11     TX1 ROLLBACK   ALL operations rolled back (including       ROLLED BACK
                       check reservation - no orphaned checks!)
```

## Audit Trail

Every check operation is logged to `check_audit_log` table:

| Action | Description |
|--------|-------------|
| `ASSIGNMENT` | Check assigned to bucket (manual or automatic) |
| `ACKNOWLEDGMENT` | Check amount acknowledged by user |
| `ISSUANCE` | Check physically issued/mailed |
| `VOID` | Check voided (with reason) |

Additional logging for reservations:
- `RESERVED check {checkNumber} from reservation {reservationId} for payer {payerId} (bucket: {bucketId})`
- `RELEASING reserved check {checkNumber} back to reservation {reservationId} - Reason: {reason}`
- `CRITICAL: Compensation failed! Check {checkNumber} may be orphaned.`

## Configuration Tables Involved

| Table | Purpose |
|-------|---------|
| `check_payment_workflow_config` | Defines workflow mode (NONE/SEPARATE/COMBINED) and assignment mode (MANUAL/AUTO/BOTH) |
| `edi_generation_thresholds` | Links bucketing rules to workflow configs |
| `edi_bucketing_rules` | Defines claim grouping strategies |
| `edi_commit_criteria` | Defines commit mode (AUTO/MANUAL/HYBRID) |
| `check_reservations` | Pre-allocated check number ranges per payer |
| `check_payments` | Actual check assignments to buckets |
| `check_audit_log` | Audit trail of all check operations |
| `check_payment_config` | System-wide config (void time limit, low stock threshold, etc.) |

## Error Handling Summary

### PostgreSQL Mode

| Error Type | Handling | Result |
|------------|----------|--------|
| No available reservations | Exception thrown before reservation | TX1 continues, bucket awaits manual assignment |
| Check payment creation fails | Compensation releases check (TX3) | Check returned to pool, TX1 rolled back |
| Transition to generation fails | Compensation releases check (TX3) | Check returned to pool, TX1 rolled back |
| Compensation fails | CRITICAL log, manual intervention needed | Check may be orphaned |

### SQLite Mode

| Error Type | Handling | Result |
|------------|----------|--------|
| No available reservations | Exception thrown before reservation | TX1 continues, bucket awaits manual assignment |
| Check payment creation fails | TX1 rollback | All operations rolled back (including reservation) |
| Transition to generation fails | TX1 rollback | All operations rolled back (including reservation) |
| Any failure | TX1 rollback | No orphaned checks possible |

## Key Design Decisions

1. **Database-Aware Transaction Strategy**: The system detects the database type and adjusts transaction behavior:
   - PostgreSQL: REQUIRES_NEW with compensation (production safety)
   - SQLite: Single transaction (avoids connection pool exhaustion)

2. **Programmatic Transaction Management**: Using `TransactionTemplate` instead of declarative `@Transactional(propagation = REQUIRES_NEW)` allows runtime configuration switching.

3. **Conditional Compensation**: Compensation logic only executes in PostgreSQL mode where it's needed. In SQLite mode, transaction rollback handles cleanup automatically.

4. **Connection Pool Awareness**: SQLite's single-writer limitation requires careful pool configuration:
   - SQLite: `maximum-pool-size: 1` (cannot use REQUIRES_NEW)
   - PostgreSQL: `minimum-idle: 5, maximum-pool-size: 10` (supports nested transactions)

5. **Fail-Fast on No Checks**: If no reservations available, fail immediately before any state changes.

6. **Audit Everything**: Every check state change is logged for compliance and debugging.

7. **List Query over Optional**: Using `List<CheckReservation>` instead of `Optional` avoids "non-unique result" exceptions when multiple reservations exist for a payer.

8. **Configuration-Driven**: All transaction behavior is externalized to configuration files, allowing environment-specific tuning without code changes:
   ```yaml
   check-reservation:
     use-separate-transaction: true/false
     enable-compensation: true/false
   ```

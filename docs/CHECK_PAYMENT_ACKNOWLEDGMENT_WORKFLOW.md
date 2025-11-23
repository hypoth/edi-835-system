# Check Payment Acknowledgment Workflow

## Overview

The **Require Acknowledgment** feature provides an optional intermediate verification step in the check payment lifecycle. When enabled, it requires explicit user confirmation of check details before EDI 835 file generation can proceed.

This document explains the feature's purpose, configuration, workflow integration, and business rationale.

---

## What is "Require Acknowledgment"?

The Require Acknowledgment feature creates an additional control point between check assignment and EDI generation:

```
Check ASSIGNED → [User Acknowledgment Required] → Check ACKNOWLEDGED → EDI Generation
```

**Without acknowledgment:**
```
Check ASSIGNED → EDI Generation (immediate)
```

**With acknowledgment:**
```
Check ASSIGNED → User reviews & confirms → Check ACKNOWLEDGED → EDI Generation
```

---

## Check Payment Lifecycle States

The system implements a multi-state check lifecycle:

```
RESERVED → ASSIGNED → ACKNOWLEDGED → ISSUED → (VOID/CANCELLED)
                ↓
           [If acknowledgment NOT required]
                ↓
         EDI Generation proceeds
```

### State Definitions

| State | Description | Triggered By |
|-------|-------------|--------------|
| **RESERVED** | Check number allocated from reservation pool | System (auto-assignment) |
| **ASSIGNED** | Check linked to a specific bucket | Manual or auto assignment |
| **ACKNOWLEDGED** | User confirmed check amount is correct | User action via UI |
| **ISSUED** | Check physically mailed/delivered | User action via UI |
| **VOID** | Check cancelled (within time limit) | Financial admin action |

### Corresponding Bucket Payment States

| Bucket Payment Status | Meaning |
|-----------------------|---------|
| `PENDING` | Payment required but not yet assigned |
| `ASSIGNED` | Check assigned, awaiting acknowledgment (if required) |
| `ACKNOWLEDGED` | Check amount confirmed by user |
| `ISSUED` | Check physically issued |

---

## Configuration Levels

The acknowledgment requirement can be configured at two levels:

### 1. Workflow-Level Configuration

**Location:** `check_payment_workflow_config` table

Each workflow configuration can specify whether acknowledgment is required:

```sql
-- Example: Create workflow requiring acknowledgment
INSERT INTO check_payment_workflow_config
  (config_name, workflow_mode, assignment_mode, require_acknowledgment, is_active)
VALUES
  ('High-Value Payments', 'SEPARATE', 'AUTO', 1, 1);
```

**Frontend Configuration:**
Navigate to: Configuration → Check Payment Workflow

| Field | Description |
|-------|-------------|
| `requireAcknowledgment` | Boolean toggle (Yes/No) |

### 2. System-Level Configuration

**Location:** `check_payment_config` table

System-wide setting that can override workflow settings:

| Config Key | Value Type | Default | Description |
|------------|------------|---------|-------------|
| `require_acknowledgment_before_edi` | BOOLEAN | `false` | Global requirement for all workflows |

**Frontend Configuration:**
Navigate to: Configuration → Check Payment Settings

---

## Workflow Integration

### Step-by-Step Flow

#### Step 1: Bucket Reaches Threshold
- Claims accumulate in bucket
- Generation threshold met (claim count, amount, or time)
- Bucket evaluated for commit mode

#### Step 2: Bucket Approval
- User approves bucket in Approval Queue
- System determines payment requirements

#### Step 3: Check Assignment
- **COMBINED Mode:** Check assigned during approval dialog
- **SEPARATE Mode (AUTO):** System auto-assigns from reservation pool
- **SEPARATE Mode (MANUAL):** User manually enters check details

Check status: `ASSIGNED`

#### Step 4: Acknowledgment Check

**If `requireAcknowledgment = true`:**
1. Bucket awaits user acknowledgment
2. User opens "Acknowledge Check" dialog
3. Reviews check details:
   - Check number
   - Check amount
   - Check date
   - Bank information
4. Clicks "Acknowledge Check" button
5. Check status: `ACKNOWLEDGED`
6. EDI generation can now proceed

**If `requireAcknowledgment = false`:**
- EDI generation proceeds immediately after assignment

#### Step 5: EDI Generation
- System validates payment readiness
- If acknowledged (or not required): Generate EDI 835 file
- If not acknowledged (but required): Block generation with error

---

## Validation Logic

The system validates payment readiness before EDI generation:

```java
public boolean isPaymentReadyForEdi(boolean requireAcknowledgmentBeforeEdi) {
    if (!isPaymentRequired()) {
        return true;  // EFT payment - no check needed
    }
    if (!hasPaymentAssigned()) {
        return false;  // Check required but not assigned
    }
    if (requireAcknowledgmentBeforeEdi) {
        // Must be acknowledged or already issued
        return paymentStatus == ACKNOWLEDGED || paymentStatus == ISSUED;
    } else {
        // Just needs to be assigned
        return paymentStatus == ASSIGNED ||
               paymentStatus == ACKNOWLEDGED ||
               paymentStatus == ISSUED;
    }
}
```

### Error Messages

| Scenario | Error Message |
|----------|---------------|
| Payment not assigned | "Payment must be assigned before EDI generation" |
| Acknowledgment required but not done | "Payment must be acknowledged before EDI generation" |

---

## Audit Trail

All acknowledgment actions are logged for compliance:

### check_payments Table
```sql
acknowledged_by  -- Username who acknowledged
acknowledged_at  -- Timestamp of acknowledgment
```

### check_audit_log Table
```sql
action = 'ACKNOWLEDGED'
performed_by = 'username'
notes = 'Check amount acknowledged'
created_at = timestamp
```

---

## Business Reasons

### 1. Financial Control & Segregation of Duties
- Separates "approve bucket" from "confirm payment amount"
- Requires multiple people involved in payment process
- Reduces fraud risk

### 2. Double-Verification of Amounts
- Forces explicit review of check amount
- Catches discrepancies between expected and actual amounts
- User must consciously confirm the payment

### 3. Audit Trail Compliance
- Complete lifecycle history for each check
- Timestamps for every state change
- Supports SOX and HIPAA compliance requirements

### 4. Risk Management
- Enable stricter controls for high-value payments
- Configure per-workflow based on risk level
- Different rules for different payer/payee combinations

### 5. Operational Flexibility
- Toggle on/off at system or workflow level
- Gradual rollout capabilities
- Adapt to organizational policies

---

## Frontend Components

### Acknowledge Check Dialog

**Location:** `src/components/checkpayments/AcknowledgeCheckDialog.tsx`

Displays:
- Check number
- Check amount (highlighted)
- Check date
- Bank name
- Routing number (masked)
- Account last 4 digits

**Actions:**
- "Cancel" - Close dialog without action
- "Acknowledge Check" - Confirm and transition to ACKNOWLEDGED

### Check Payment Settings Page

**Location:** `src/pages/config/CheckPaymentConfig.tsx`

**Route:** `/config/check-payment-settings`

Manage system-wide settings including:
- `require_acknowledgment_before_edi` - System-wide acknowledgment requirement

### Check Payment Workflow Config Page

**Location:** `src/pages/config/CheckPaymentWorkflowConfig.tsx`

**Route:** `/config/check-payment-workflow`

Create/edit workflow configurations with:
- `requireAcknowledgment` checkbox per workflow

---

## API Endpoints

### Acknowledge a Check

```http
POST /api/v1/check-payments/{checkPaymentId}/acknowledge
Content-Type: application/json

{
  "acknowledgedBy": "username"
}
```

**Response:**
```json
{
  "id": "uuid",
  "checkNumber": "CHK000123",
  "status": "ACKNOWLEDGED",
  "acknowledgedBy": "username",
  "acknowledgedAt": "2024-01-15T10:30:00"
}
```

### Get Pending Acknowledgments

```http
GET /api/v1/check-payments/pending-acknowledgments
```

Returns all checks in `ASSIGNED` status awaiting acknowledgment.

---

## Database Schema

### check_payment_workflow_config

```sql
CREATE TABLE check_payment_workflow_config (
    id TEXT PRIMARY KEY,
    config_name TEXT NOT NULL,
    workflow_mode TEXT NOT NULL,        -- NONE, COMBINED, SEPARATE
    assignment_mode TEXT NOT NULL,      -- AUTO, MANUAL, BOTH
    require_acknowledgment INTEGER DEFAULT 0,  -- 0=No, 1=Yes
    linked_threshold_id TEXT,
    description TEXT,
    is_active INTEGER DEFAULT 1,
    created_at TIMESTAMP,
    updated_at TIMESTAMP
);
```

### check_payment_config

```sql
-- System setting for acknowledgment
INSERT INTO check_payment_config
  (config_key, config_value, description, value_type)
VALUES
  ('require_acknowledgment_before_edi', 'false',
   'Whether check acknowledgment is required before EDI generation',
   'BOOLEAN');
```

---

## Configuration Examples

### Example 1: High-Value Payment Workflow with Acknowledgment

```json
{
  "configName": "Optum RX - High Value Checks",
  "workflowMode": "SEPARATE",
  "assignmentMode": "AUTO",
  "requireAcknowledgment": true,
  "linkedThresholdId": "threshold-optum-rx",
  "description": "Requires explicit acknowledgment for all check payments over $10,000",
  "isActive": true
}
```

### Example 2: Standard Workflow without Acknowledgment

```json
{
  "configName": "Standard Check Payments",
  "workflowMode": "SEPARATE",
  "assignmentMode": "MANUAL",
  "requireAcknowledgment": false,
  "description": "Manual check entry, no acknowledgment required",
  "isActive": true
}
```

### Example 3: Enable System-Wide Acknowledgment

```sql
UPDATE check_payment_config
SET config_value = 'true'
WHERE config_key = 'require_acknowledgment_before_edi';
```

---

## Summary

| Aspect | Details |
|--------|---------|
| **Feature Name** | Require Acknowledgment |
| **Purpose** | Additional financial control before EDI generation |
| **Configuration Levels** | System-wide + Per-workflow |
| **Check States** | ASSIGNED → ACKNOWLEDGED → ISSUED |
| **Default Value** | `false` (not required) |
| **Validation Method** | `isPaymentReadyForEdi()` |
| **Audit Tracking** | `check_audit_log` table |
| **Frontend Component** | `AcknowledgeCheckDialog.tsx` |

---

## Related Documentation

- [Bucket Approval Transaction Flow](./BUCKET_APPROVAL_TRANSACTION_FLOW.md)
- [Check Auto-Assignment for Optum RX](./CHECK_AUTO_ASSIGNMENT_OPTUM_RX.md)
- [EDI 835 Payment Integration Plan](./plans/EDI_835_Payment_Integration_Implementation_plan.md)

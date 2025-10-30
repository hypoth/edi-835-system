# EDI 835 Generation Thresholds Guide

## Table of Contents
1. [Overview](#overview)
2. [What Are Thresholds?](#what-are-thresholds)
3. [Threshold Types](#threshold-types)
4. [How Thresholds Work](#how-thresholds-work)
5. [Configuration Guide](#configuration-guide)
6. [Use Cases & Best Practices](#use-cases--best-practices)
7. [Troubleshooting](#troubleshooting)
8. [FAQ](#faq)

---

## Overview

Generation Thresholds are the rules that determine **when** accumulated claims should trigger the generation of an EDI 835 remittance advice file. Think of them as "trigger points" that automatically initiate file creation when specific conditions are met.

### Why Are Thresholds Important?

- **Automate File Generation**: No manual intervention needed once configured
- **Optimize Processing**: Balance between file size and processing frequency
- **Meet Partner Requirements**: Satisfy payer-specific submission schedules
- **Control Cash Flow**: Generate files based on dollar amounts for financial planning
- **Maintain Compliance**: Ensure timely submission per contractual obligations

---

## What Are Thresholds?

A **Generation Threshold** is a set of conditions that, when met, causes the system to:

1. Stop accumulating claims in a bucket
2. Transition the bucket to `PENDING_APPROVAL` or directly to `GENERATING` state (depending on commit criteria)
3. Initiate EDI 835 file generation
4. Deliver the file to the configured destination

### Key Concepts

- **Bucket**: A collection of claims grouped according to bucketing rules (e.g., by payer/payee, BIN/PCN)
- **Threshold**: The condition(s) that trigger file generation for a bucket
- **Commit Mode**: Determines if generation is automatic (AUTO), requires approval (MANUAL), or both (HYBRID)

---

## Threshold Types

The system supports four types of generation thresholds:

### 1. CLAIM_COUNT

**Description**: Triggers generation when the number of claims reaches a specified count.

**Configuration**:
- `maxClaims`: The number of claims (e.g., 100)

**Example**:
```
Threshold: maxClaims = 50
Behavior: Generate file when bucket reaches 50 claims
```

**Best For**:
- High-volume payers with predictable claim rates
- Quality assurance batches requiring specific claim counts
- Regulatory requirements specifying maximum claims per file

**Pros**:
- Predictable file sizes
- Easy to understand and monitor
- Consistent processing patterns

**Cons**:
- May delay processing if claim volume is low
- Doesn't account for claim value variations

---

### 2. AMOUNT

**Description**: Triggers generation when the total dollar amount of claims reaches a threshold.

**Configuration**:
- `maxAmount`: The dollar threshold (e.g., $50,000)

**Example**:
```
Threshold: maxAmount = $25,000
Behavior: Generate file when total claim amount reaches $25,000
```

**Best For**:
- High-value transactions requiring immediate processing
- Cash flow management and financial planning
- Financial reconciliation deadlines

**Pros**:
- Optimizes cash flow timing
- Handles variable claim values better than count-based
- Financial control and visibility

**Cons**:
- Unpredictable file generation timing
- May result in very large or very small claim counts

---

### 3. TIME

**Description**: Triggers generation at scheduled intervals regardless of claim count or amount.

**Configuration**:
- `timeDuration`: DAILY, WEEKLY, BIWEEKLY, or MONTHLY

**Example**:
```
Threshold: timeDuration = DAILY
Behavior: Generate file once per day at scheduled time (e.g., 5 PM)
```

**Best For**:
- Regular reporting schedules
- Payers requiring daily/weekly batches
- Consistent processing windows
- Low-volume payers

**Pros**:
- Predictable generation schedule
- Ensures no claims are delayed indefinitely
- Simple to explain to stakeholders

**Cons**:
- May generate files with very few claims
- Doesn't account for volume spikes
- Fixed schedule may not align with operational peaks

---

### 4. HYBRID (Recommended)

**Description**: Combines multiple conditions using **OR logic** - triggers when **ANY** condition is met (whichever comes first).

**Configuration**:
- `maxClaims`: Claim count threshold (optional)
- `maxAmount`: Dollar amount threshold (optional)
- `timeDuration`: Time-based schedule (optional)

**Example**:
```
Threshold: maxClaims = 100 OR maxAmount = $50,000 OR timeDuration = DAILY
Behavior: Generate file when:
  - Bucket reaches 100 claims, OR
  - Total amount reaches $50,000, OR
  - End of day (whichever happens first)
```

**Best For**:
- Most production scenarios
- Variable claim patterns requiring multiple safety valves
- Optimizing both volume and timing
- Flexible processing requirements

**Pros**:
- Maximum flexibility
- Multiple trigger points prevent delays
- Adapts to variable claim patterns
- Best balance of all approaches

**Cons**:
- More complex to configure initially
- Requires understanding of typical claim patterns

---

## How Thresholds Work

### System Flow

```
┌─────────────────────────────────────────────────────────────────┐
│ 1. CLAIMS ARRIVE                                                 │
│    D0 Claims Engine → Cosmos DB → Change Feed Processor          │
└────────────────┬────────────────────────────────────────────────┘
                 │
                 ▼
┌─────────────────────────────────────────────────────────────────┐
│ 2. CLAIM AGGREGATION                                             │
│    Claims grouped into buckets based on Bucketing Rules          │
│    Bucket status: ACCUMULATING                                   │
└────────────────┬────────────────────────────────────────────────┘
                 │
                 ▼
┌─────────────────────────────────────────────────────────────────┐
│ 3. THRESHOLD EVALUATION (After each claim OR scheduled)          │
│    ✓ Check claim count vs maxClaims                             │
│    ✓ Check total amount vs maxAmount                            │
│    ✓ Check time elapsed vs timeDuration                         │
│                                                                  │
│    IF threshold met → Proceed to step 4                         │
│    ELSE → Continue accumulating (back to step 2)                │
└────────────────┬────────────────────────────────────────────────┘
                 │
                 ▼
┌─────────────────────────────────────────────────────────────────┐
│ 4. COMMIT MODE EVALUATION                                        │
│    ┌──────────────┬──────────────┬──────────────┐              │
│    │ AUTO-COMMIT  │ MANUAL       │ HYBRID        │              │
│    │ Generate     │ Request      │ Evaluate      │              │
│    │ immediately  │ approval     │ criteria      │              │
│    └──────┬───────┴──────┬───────┴──────┬───────┘              │
│           │              │              │                       │
│           └──────────────┴──────────────┘                       │
└────────────────┬────────────────────────────────────────────────┘
                 │
                 ▼
┌─────────────────────────────────────────────────────────────────┐
│ 5. FILE GENERATION                                               │
│    Bucket status: GENERATING → COMPLETED                        │
│    StAEDI generates HIPAA 5010 X12 835 file                     │
└────────────────┬────────────────────────────────────────────────┘
                 │
                 ▼
┌─────────────────────────────────────────────────────────────────┐
│ 6. FILE DELIVERY                                                 │
│    File delivered via SFTP/AS2 to payer                         │
│    Delivery status: PENDING → DELIVERED                         │
└─────────────────────────────────────────────────────────────────┘
```

### Evaluation Frequency

Thresholds are evaluated in two ways:

1. **Real-time Evaluation**: After each claim is added to a bucket
   - Immediate response to threshold conditions
   - No polling delay
   - Most responsive approach

2. **Scheduled Evaluation**: Periodic background job (default: every 5 minutes)
   - Catches time-based thresholds
   - Fallback for missed real-time evaluations
   - Ensures no buckets are stuck in ACCUMULATING state

### Hybrid Threshold Logic

Hybrid thresholds use **OR logic**, meaning the file generates when **ANY** condition is met:

```java
boolean shouldTrigger =
    (claimCount >= maxClaims) ||           // Condition 1
    (totalAmount >= maxAmount) ||          // Condition 2
    (timeElapsed >= timeDuration);         // Condition 3

if (shouldTrigger) {
    transitionBucket();
}
```

**Example Scenario**:
```
Configuration:
  maxClaims: 100
  maxAmount: $50,000
  timeDuration: DAILY

Bucket State at 4 PM:
  Claims: 75
  Amount: $52,000

Result: File generates immediately (amount threshold met)
         even though claim count (75) is below threshold (100)
```

---

## Configuration Guide

### Step 1: Access Threshold Configuration

1. Open EDI 835 Admin Portal: `http://localhost:5173`
2. Navigate to: **Configuration → Thresholds**

### Step 2: Choose Configuration Method

#### Option A: Quick Start with Templates

1. Click on the **"Templates"** tab
2. Select a pre-configured template:
   - **Quick Daily Batch**: 10 claims OR $5,000 OR daily
   - **Standard Daily Processing**: 50 claims OR $25,000 OR daily
   - **High Volume Processing**: 100 claims OR $50,000 OR daily
   - **Weekly Reconciliation**: 200 claims OR $100,000 OR weekly
   - **Enterprise Large Batch**: 500 claims OR $250,000 OR daily
3. Customize the template values if needed
4. Save

#### Option B: Custom Configuration

1. Click **"Add Threshold"** button
2. Select threshold type (CLAIM_COUNT, AMOUNT, TIME, or HYBRID)
3. Fill in the configuration form:
   - **Threshold Name**: Descriptive name (e.g., "BCBS Daily Processing")
   - **Max Claims**: (if applicable) Number of claims to trigger generation
   - **Max Amount**: (if applicable) Dollar amount to trigger generation
   - **Time Duration**: (if applicable) Schedule (DAILY, WEEKLY, BIWEEKLY, MONTHLY)
   - **Linked Bucketing Rule**: (optional) Apply to specific rule or all rules
   - **Active**: Toggle to enable/disable
4. Preview affected buckets
5. Test the configuration (optional)
6. Save

### Step 3: Link to Bucketing Rule (Optional)

By default, thresholds apply to **all** bucketing rules. To apply a threshold to a specific rule:

1. In the threshold form, select **"Linked Bucketing Rule"**
2. Choose the specific rule (e.g., "BCBS - General Hospital")
3. Save

This allows you to have different thresholds for different payers/payees.

### Step 4: Configure Commit Criteria

Thresholds determine **when** to generate files, but **Commit Criteria** determine **whether** generation requires approval:

1. Navigate to: **Configuration → Commit Criteria**
2. Create or edit commit criteria for your bucketing rule
3. Choose mode:
   - **AUTO-COMMIT**: Generate immediately when threshold met
   - **MANUAL**: Always require approval
   - **HYBRID**: Auto-generate for high thresholds, require approval for low thresholds

### Step 5: Test Your Configuration

1. Click the **"Test"** icon (flask) next to your threshold
2. Configure a test scenario:
   - Claim count
   - Total amount
   - Time elapsed
3. Click **"Run Test"**
4. Verify the threshold triggers as expected

### Step 6: Monitor Performance

1. Click the **"Analytics"** icon (chart) next to your threshold
2. Review metrics:
   - Total triggers
   - Average claims at trigger
   - Average amount at trigger
   - Average time to trigger
   - Failure rate
3. Adjust configuration based on analytics

---

## Use Cases & Best Practices

### Use Case 1: High-Volume Payer (Daily Processing)

**Scenario**: BCBS sends 150-200 claims per day, average $450 per claim

**Recommended Configuration**:
```
Threshold Type: HYBRID
maxClaims: 100
maxAmount: $50,000
timeDuration: DAILY
```

**Rationale**:
- Claim count ensures files don't exceed 100 claims (manageable size)
- Amount ensures high-value days trigger earlier
- Daily schedule ensures no claims delayed past end-of-day

**Expected Behavior**:
- Typical day (150 claims @ $450 each = $67,500):
  - Triggers at 100 claims (first condition met)
  - Second file generated at end of day (remaining 50 claims)
- High-value day (50 claims @ $1,100 each = $55,000):
  - Triggers at ~45 claims when $50,000 is reached

---

### Use Case 2: Low-Volume Payer (Weekly Batch)

**Scenario**: Small regional payer sends 5-15 claims per week

**Recommended Configuration**:
```
Threshold Type: TIME
timeDuration: WEEKLY
```

**Rationale**:
- Claim count and amount thresholds would delay processing indefinitely
- Weekly schedule ensures regular processing
- Simple configuration for low complexity

**Expected Behavior**:
- File generates every Friday at 5 PM
- Contains all claims accumulated during the week (typically 5-15)

---

### Use Case 3: High-Value Claims (Large Medical Facility)

**Scenario**: Surgery center with fewer claims but high dollar amounts

**Recommended Configuration**:
```
Threshold Type: HYBRID
maxClaims: 20
maxAmount: $25,000
timeDuration: DAILY
```

**Rationale**:
- Low claim count reflects typical volume
- Amount threshold triggers on expensive procedures
- Daily ensures no delays for lower-value days

**Expected Behavior**:
- Single $30,000 procedure: Triggers immediately
- Multiple small claims: Wait until 20 claims or end of day

---

### Use Case 4: Quality Assurance / Testing

**Scenario**: Test environment or QA batches requiring specific sizes

**Recommended Configuration**:
```
Threshold Type: CLAIM_COUNT
maxClaims: 10
```

**Rationale**:
- Predictable batch sizes for testing
- No time dependency (generates when ready)
- Easy to verify exact count

**Expected Behavior**:
- File generates exactly when 10 claims accumulated
- Consistent for automated testing

---

### Best Practices

#### 1. Always Use Hybrid for Production

**DO**:
```
Threshold Type: HYBRID
maxClaims: 100
maxAmount: $50,000
timeDuration: DAILY
```

**DON'T**:
```
Threshold Type: CLAIM_COUNT
maxClaims: 100
(No fallback if volume is low)
```

**Why**: Hybrid provides multiple trigger points, ensuring files generate even if one condition is never met.

#### 2. Set Reasonable Defaults

**Good Thresholds**:
- Claims: 50-200 (balance between file size and frequency)
- Amount: $25,000-$100,000 (depends on claim values)
- Time: DAILY (ensures timely processing)

**Avoid**:
- Very low counts (< 5): Causes too many small files
- Very high counts (> 500): Delays processing, large file sizes
- Very long durations (MONTHLY): May delay cash flow

#### 3. Name Thresholds Descriptively

**Good Names**:
- "BCBS - Daily High Volume"
- "Cigna - Weekly Reconciliation"
- "Aetna - Amount-Based (Immediate)"

**Poor Names**:
- "Threshold 1"
- "Test"
- "Default"

#### 4. Link Thresholds to Rules When Appropriate

- Use **global thresholds** (no linked rule) for consistent behavior across all payers
- Use **rule-specific thresholds** when payers have unique requirements

Example:
```
Global Threshold: 100 claims OR $50,000 OR daily
BCBS Threshold: 200 claims OR $100,000 OR daily (higher volume)
Small Payer Threshold: 10 claims OR $5,000 OR weekly (lower volume)
```

#### 5. Test Before Activating

- Use the **Test** feature to verify threshold logic
- Start with a higher threshold initially, then lower as needed
- Monitor analytics after deployment to tune values

#### 6. Monitor and Adjust

- Review analytics monthly
- Look for patterns:
  - Always triggering on one condition? Adjust others
  - Triggering too frequently? Increase thresholds
  - Triggering too rarely? Decrease thresholds or add time component

---

## Troubleshooting

### Issue: Threshold Not Triggering

**Symptoms**:
- Bucket remains in ACCUMULATING state
- Claims keep accumulating beyond threshold

**Possible Causes & Solutions**:

1. **Threshold Not Active**
   - Check: Threshold `isActive` = true
   - Fix: Activate the threshold in admin portal

2. **No Threshold Configured for Rule**
   - Check: Bucketing rule has linked threshold
   - Fix: Create and link a threshold (this was your original issue!)
   - Log message: `No thresholds configured for bucket`

3. **Threshold Values Too High**
   - Check: Bucket metrics vs threshold values
   - Fix: Lower thresholds or add time-based component

4. **Threshold Evaluation Not Running**
   - Check: Scheduler status in logs
   - Fix: Restart application or check scheduler configuration

5. **Commit Criteria Blocking**
   - Check: Commit mode (might be MANUAL and awaiting approval)
   - Fix: Approve in approval queue or change to AUTO mode

### Issue: Generating Too Many Files

**Symptoms**:
- Files generating very frequently
- Small files with few claims

**Solutions**:
- Increase `maxClaims` threshold
- Increase `maxAmount` threshold
- Change time duration from DAILY to WEEKLY

### Issue: Files Generating at Wrong Time

**Symptoms**:
- Time-based threshold not firing at expected time

**Check**:
1. Server timezone configuration
2. Scheduled job cron expression
3. Application logs for scheduler execution

**Fix**:
- Update `application.yml` scheduler configuration
- Verify `scheduled-threshold-evaluation-cron` setting

### Issue: Validation Errors

**Common Errors**:

1. **"Max claims must be greater than 0"**
   - Solution: Enter a positive integer for maxClaims

2. **"Time duration is required"**
   - Solution: Select a time duration for TIME or HYBRID thresholds

3. **"A threshold with this name already exists"**
   - Solution: Choose a unique threshold name

4. **"Multiple active thresholds for same rule"**
   - Warning only: Multiple thresholds will compete
   - Consider: Consolidate into single HYBRID threshold

---

## FAQ

### Q: Can I have multiple thresholds for the same bucketing rule?

**A**: Yes, but they will compete. If multiple thresholds apply to the same bucket, whichever triggers first will transition the bucket. This can lead to unpredictable behavior.

**Recommendation**: Use a single HYBRID threshold with multiple conditions instead.

### Q: What happens if a threshold is deactivated while buckets are accumulating?

**A**: Buckets will continue to accumulate indefinitely until:
- The threshold is reactivated, OR
- A different threshold applies, OR
- The bucket is manually triggered

**Best Practice**: Don't deactivate production thresholds. If you need to change settings, edit the existing threshold instead.

### Q: Can I test thresholds without affecting production?

**A**: Yes, use the **Test** feature in the admin portal:
1. Configure a test scenario (claims, amount, time)
2. Run test to see if threshold would trigger
3. No actual buckets or files are affected

### Q: How do I handle different requirements for different payers?

**A**: Create payer-specific thresholds:
1. Create bucketing rule for each payer (e.g., "BCBS - Hospital A")
2. Create threshold linked to that specific rule
3. Configure threshold based on payer requirements

Example:
```
BCBS Rule → BCBS Threshold (100 claims, daily)
Cigna Rule → Cigna Threshold (50 claims, weekly)
Default Rule → Global Threshold (75 claims, daily)
```

### Q: What's the difference between thresholds and commit criteria?

**A**:
- **Thresholds**: Determine **WHEN** to generate files (technical trigger)
- **Commit Criteria**: Determine **WHETHER** generation requires approval (business logic)

Both work together:
```
Threshold Met → Commit Criteria Evaluated → Generate or Request Approval
```

### Q: Can time-based thresholds generate empty files?

**A**: No. If a time-based threshold fires but the bucket has 0 claims, no file is generated. The bucket remains in ACCUMULATING state.

### Q: How do I calculate the right threshold values?

**A**: Use historical data:

1. Review past claim volumes:
   ```sql
   SELECT
     DATE(created_at) as date,
     COUNT(*) as claim_count,
     SUM(total_amount) as total_amount
   FROM claims
   WHERE payer_id = 'PAYER001'
   GROUP BY DATE(created_at)
   ORDER BY date DESC
   LIMIT 30;
   ```

2. Calculate averages and set thresholds at:
   - 50-75% of average daily volume (for daily processing)
   - 50-75% of average weekly volume (for weekly processing)

3. Monitor and adjust based on analytics

### Q: What if I want to force generation before threshold is met?

**A**: Use manual approval workflow:
1. Navigate to **Approvals** page
2. Find the bucket in pending approval queue
3. Click **"Approve"** to force generation

Or via API:
```bash
POST /api/v1/buckets/{bucketId}/transition-to-generation
```

### Q: Can I schedule generation for a specific time?

**A**: Yes, when manually approving:
1. Go to **Approvals** page
2. Select bucket
3. Choose **"Schedule Generation"**
4. Select date and time
5. Approve

The bucket will generate at the scheduled time.

### Q: How do I see which buckets are affected by a threshold?

**A**: In the admin portal:
1. Edit or create a threshold
2. Click **"Preview"** step
3. See list of affected buckets and whether they would trigger

---

## Summary

### Key Takeaways

1. **Thresholds are essential** for automated file generation
2. **HYBRID is recommended** for most production scenarios
3. **Test before deploying** to avoid unexpected behavior
4. **Monitor analytics** to optimize threshold values
5. **Link to rules** when payers have unique requirements
6. **Use time-based fallbacks** to prevent indefinite accumulation

### Quick Configuration Checklist

- [ ] Threshold created with descriptive name
- [ ] Threshold type selected (prefer HYBRID)
- [ ] Appropriate values configured:
  - [ ] maxClaims (if applicable)
  - [ ] maxAmount (if applicable)
  - [ ] timeDuration (if applicable)
- [ ] Linked to bucketing rule (if payer-specific)
- [ ] Threshold set to **Active**
- [ ] Commit criteria configured for rule
- [ ] Test run successfully
- [ ] Preview shows expected buckets
- [ ] Validation passed with no errors

### Support

For additional help:
- Review system logs: `edi835-processor/logs/edi835-processor.log`
- Check bucket status in admin portal: `/buckets`
- Consult CLAUDE.md for technical details
- Review analytics for threshold performance

---

**Document Version**: 1.0
**Last Updated**: 2025-10-18
**Author**: Claude Code AI Assistant

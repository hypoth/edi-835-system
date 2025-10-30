# SFTP Delivery Tracking Guide

## Overview

This document provides a comprehensive guide to understanding how SFTP file delivery tracking works in the EDI 835 system, including the complete implementation plan and configuration requirements.

## System Architecture

### Data Flow

```
EDI File Generation (Edi835GeneratorService)
    ↓
FileGenerationHistory record created (delivery_status = 'PENDING')
    ↓
File stored in database as BLOB
    ↓
Visible on Delivery Tracking page (http://localhost:3000/delivery)
    ↓
Manual OR Scheduled delivery trigger
    ↓
FileDeliveryService.deliverFile(fileId)
    ↓
SFTP Upload to Payer's Server (using Spring Integration SFTP)
    ↓
Status updated: DELIVERED / FAILED / RETRY
```

---

## 1. Database Tracking

### file_generation_history Table

Tracks all generated files and their delivery status:

| Column | Type | Description |
|--------|------|-------------|
| `id` | UUID | Primary key |
| `bucket_id` | UUID | Reference to edi_file_buckets |
| `generated_file_name` | VARCHAR(500) | File name |
| `file_path` | TEXT | Local file system path |
| `file_size_bytes` | BIGINT | File size |
| `claim_count` | INTEGER | Number of claims in file |
| `total_amount` | DECIMAL(15,2) | Total claim amount |
| `generated_at` | TIMESTAMP | Generation timestamp |
| `generated_by` | VARCHAR(100) | User who generated |
| `delivery_status` | VARCHAR(50) | PENDING, DELIVERED, FAILED, RETRY |
| `delivered_at` | TIMESTAMP | Successful delivery timestamp |
| `delivery_attempt_count` | INTEGER | Number of retry attempts |
| `error_message` | TEXT | Last error encountered |

### Delivery Status States

- **PENDING**: File generated, awaiting first delivery attempt
- **RETRY**: Delivery failed, retrying (attempt < max)
- **DELIVERED**: Successfully uploaded to SFTP server
- **FAILED**: All retry attempts exhausted

---

## 2. SFTP Configuration

### Per-Payer Configuration (payers table)

Each payer must have complete SFTP configuration:

```sql
-- PostgreSQL Schema (MIGRATION REQUIRED)
ALTER TABLE payers ADD COLUMN sftp_host VARCHAR(255);
ALTER TABLE payers ADD COLUMN sftp_port INTEGER DEFAULT 22;
ALTER TABLE payers ADD COLUMN sftp_username VARCHAR(100);
ALTER TABLE payers ADD COLUMN sftp_password TEXT;  -- Encrypted in application
ALTER TABLE payers ADD COLUMN sftp_path VARCHAR(500);
ALTER TABLE payers ADD COLUMN requires_special_handling BOOLEAN DEFAULT FALSE;
```

### Required SFTP Fields

| Field | Required | Example | Validated By |
|-------|----------|---------|--------------|
| `sftp_host` | ✅ Yes | `sftp.payer.com` | FileDeliveryService.hasSftpConfig() |
| `sftp_port` | ✅ Yes | `22` | FileDeliveryService.hasSftpConfig() |
| `sftp_username` | ✅ Yes | `edi_user` | FileDeliveryService.hasSftpConfig() |
| `sftp_password` | ⚠️ Optional* | `encrypted_pwd` | Used for connection |
| `sftp_path` | ✅ Yes | `/edi/835` | FileDeliveryService.hasSftpConfig() |

*Password validated during connection attempt

### Application-Level Configuration (application.yml)

```yaml
file-delivery:
  sftp:
    enabled: ${SFTP_ENABLED:true}  # Enable SFTP delivery
    host: ${SFTP_HOST:}  # Global fallback (optional)
    port: ${SFTP_PORT:22}
    username: ${SFTP_USERNAME:}
    password: ${SFTP_PASSWORD:}
    remote-directory: ${SFTP_REMOTE_DIR:/edi/835}

    # Connection settings
    connection-timeout-ms: 30000  # 30 seconds
    session-timeout-ms: 300000    # 5 minutes

  retry:
    max-attempts: 3
    backoff-ms: 5000  # Base delay for exponential backoff

  scheduler:
    enabled: ${SFTP_SCHEDULER_ENABLED:true}
    cron: ${SFTP_DELIVERY_CRON:0 */5 * * * ?}  # Every 5 minutes
    batch-size: 10  # Max files to deliver per run
```

---

## 3. Delivery Triggers

### Manual Delivery

#### Via Admin Portal
On the delivery page, click "Deliver" button for pending files:

```typescript
// Frontend: DeliveryTracking.tsx
const handleDeliver = (fileId: string) => {
  deliverMutation.mutate(fileId);  // POST /api/v1/delivery/deliver/{fileId}
};
```

#### Via REST API
```bash
# Deliver single file
curl -X POST http://localhost:8080/api/v1/delivery/deliver/{fileId}

# Batch delivery
curl -X POST http://localhost:8080/api/v1/delivery/deliver/batch \
  -H "Content-Type: application/json" \
  -d '{"fileIds": ["uuid1", "uuid2"]}'

# Retry all failed
curl -X POST http://localhost:8080/api/v1/delivery/retry-all-failed
```

### Automatic Scheduled Delivery

Scheduled job runs every 5 minutes (configurable):

```java
@Scheduled(cron = "${file-delivery.scheduler.cron}")
public void autoDeliverPendingFiles() {
    List<FileGenerationHistory> pending = getPendingDeliveries();
    for (FileGenerationHistory file : pending) {
        deliverFile(file.getFileId());
    }
}
```

**Enable/Disable:**
```bash
export SFTP_SCHEDULER_ENABLED=true
export SFTP_DELIVERY_CRON="0 */5 * * * ?"  # Every 5 minutes
```

---

## 4. Retry Logic

### Exponential Backoff Strategy

| Attempt | Wait Before Retry | Formula |
|---------|-------------------|---------|
| 1 | Immediate | - |
| 2 | 5 seconds | 5000ms × 2^(1-1) |
| 3 | 10 seconds | 5000ms × 2^(2-1) |
| 4* | 20 seconds | 5000ms × 2^(3-1) |

*Max attempts configurable (default: 3)

### State Transitions

```
PENDING → (delivery attempt) → DELIVERED ✓
PENDING → (failed attempt 1) → RETRY
RETRY → (failed attempt 2) → RETRY
RETRY → (failed attempt 3) → FAILED
FAILED → (manual retry) → RETRY or DELIVERED
```

### Automatic Retry

- Automatic retry for transient failures (connection timeout, network error)
- Manual retry required for failed files after all attempts exhausted
- Retry count tracked in `delivery_attempt_count` field

---

## 5. SFTP Implementation (Spring Integration)

### Dependencies (pom.xml)

```xml
<dependency>
    <groupId>org.springframework.integration</groupId>
    <artifactId>spring-integration-sftp</artifactId>
</dependency>
<dependency>
    <groupId>com.jcraft</groupId>
    <artifactId>jsch</artifactId>
    <version>0.1.55</version>
</dependency>
```

### SFTP Configuration Bean

```java
@Configuration
@EnableIntegration
public class SftpConfig {

    @Bean
    public SessionFactory<ChannelSftp.LsEntry> sftpSessionFactory(
            Payer payer) {
        DefaultSftpSessionFactory factory = new DefaultSftpSessionFactory(true);
        factory.setHost(payer.getSftpHost());
        factory.setPort(payer.getSftpPort());
        factory.setUser(payer.getSftpUsername());
        factory.setPassword(payer.getSftpPassword());
        factory.setAllowUnknownKeys(true);
        factory.setTimeout(30000);
        return new CachingSessionFactory<>(factory);
    }
}
```

### Upload Implementation

```java
private void uploadToSftp(FileGenerationHistory file, Payer payer) throws Exception {
    SessionFactory<ChannelSftp.LsEntry> sessionFactory =
        createSessionFactory(payer);

    Session<ChannelSftp.LsEntry> session = sessionFactory.getSession();
    try {
        String remotePath = payer.getSftpPath() + "/" + file.getFileName();
        InputStream inputStream = new ByteArrayInputStream(file.getFileContent());

        session.write(inputStream, remotePath);

        log.info("File {} uploaded successfully to {}:{}{}",
            file.getFileName(), payer.getSftpHost(),
            payer.getSftpPort(), remotePath);
    } finally {
        session.close();
    }
}
```

---

## 6. REST API Endpoints

Base Path: `/api/v1/delivery`

### Delivery Operations

| Endpoint | Method | Purpose |
|----------|--------|---------|
| `/deliver/{fileId}` | POST | Manually trigger SFTP delivery for single file |
| `/deliver/batch` | POST | Batch delivery for multiple files |
| `/retry/{fileId}` | POST | Retry delivery for single failed file |
| `/retry-all-failed` | POST | Automatic retry all failed deliveries |
| `/mark-delivered/{fileId}` | POST | Manual override: mark as delivered |

### Status Queries

| Endpoint | Method | Purpose |
|----------|--------|---------|
| `/status/{fileId}` | GET | Get current delivery status of a file |
| `/pending` | GET | List all files pending delivery |
| `/failed` | GET | List all failed delivery files |
| `/delivered` | GET | List all successfully delivered files |

### Configuration & Monitoring

| Endpoint | Method | Purpose |
|----------|--------|---------|
| `/validate-sftp/{payerId}` | GET | Validate SFTP config for payer |
| `/statistics` | GET | Delivery statistics by status |
| `/statistics/summary` | GET | Summary: pending, failed, delivered counts + rates |
| `/statistics/retry-metrics` | GET | Retry statistics |
| `/queue/size` | GET | Current delivery queue size |
| `/queue/details` | GET | Detailed queue contents |
| `/errors` | GET | List delivery errors with details |

---

## 7. Frontend Implementation

### Delivery Tracking Page Components

#### Statistics Cards
- **Total Files**: All generated files
- **Delivered**: Successfully uploaded (with % success rate)
- **Pending**: Awaiting delivery attempt
- **Failed**: All retries exhausted (with % failure rate)

#### Pending Deliveries Table
- File name (monospace font)
- Payer → Payee
- Status chip (blue for PENDING, orange for RETRY)
- Generated timestamp
- Retry count
- **"Deliver" button** to manually trigger upload

#### Failed Deliveries Table
- File name
- Payer → Payee
- Error message (red text)
- Retry count (red chip if ≥3)
- Last attempt timestamp
- **"Retry" button** to manually retry
- **"View Log" button** to see detailed error log

#### Auto-Refresh
- Pending files: Every 10 seconds
- Failed files: Every 15 seconds
- Overall statistics: Every 30 seconds

### User Actions

```typescript
// Deliver pending file
const handleDeliver = (fileId: string) => {
  deliverMutation.mutate(fileId);
};

// Retry failed file
const handleRetry = (fileId: string) => {
  retryMutation.mutate(fileId);
};

// View failure log
const handleViewLog = (file: FileHistory) => {
  setSelectedFile(file);
  setLogModalOpen(true);
};
```

---

## 8. Troubleshooting Guide

### Files Stuck in PENDING

**Symptoms:**
- Files remain in PENDING status
- No delivery attempts visible in logs

**Possible Causes & Solutions:**

1. **SFTP delivery disabled**
   ```bash
   # Check application.yml or environment
   export SFTP_ENABLED=true
   ```

2. **Scheduled delivery disabled**
   ```bash
   export SFTP_SCHEDULER_ENABLED=true
   ```

3. **Payer missing SFTP configuration**
   ```sql
   -- Verify SFTP config exists
   SELECT payer_id, sftp_host, sftp_port, sftp_username, sftp_path
   FROM payers WHERE payer_id = 'YOUR_PAYER';

   -- Add if missing
   UPDATE payers SET
     sftp_host = 'sftp.payer.com',
     sftp_port = 22,
     sftp_username = 'edi_user',
     sftp_path = '/edi/835'
   WHERE payer_id = 'YOUR_PAYER';
   ```

4. **Manual delivery needed**
   - No automatic delivery job running
   - Trigger manually via API or UI button

### Delivery Failures

**Common Error Messages:**

| Error | Cause | Solution |
|-------|-------|----------|
| "No SFTP configuration" | Payer missing SFTP fields | Add SFTP config to payer |
| "Connection refused" | Wrong host/port or firewall | Verify host reachable, check firewall |
| "Authentication failed" | Wrong username/password | Verify credentials |
| "Permission denied" | User lacks write permission | Check SFTP user permissions |
| "No such file or directory" | Invalid sftp_path | Verify remote directory exists |
| "File content is empty" | File not stored in DB | Check file generation process |

**Debugging Steps:**

1. **Check application logs:**
   ```bash
   tail -f logs/edi835-processor.log | grep -i sftp
   ```

2. **Test SFTP connection manually:**
   ```bash
   sftp -P 22 username@sftp.payer.com
   ```

3. **Validate payer configuration:**
   ```bash
   curl http://localhost:8080/api/v1/delivery/validate-sftp/{payerId}
   ```

4. **Check retry count:**
   ```sql
   SELECT file_id, generated_file_name, delivery_status,
          delivery_attempt_count, error_message
   FROM file_generation_history
   WHERE delivery_status = 'FAILED';
   ```

---

## 9. Security Considerations

### Password Encryption

**Current Implementation:**
- SFTP passwords stored in `payers.sftp_password` field
- ⚠️ **TODO**: Implement encryption at application layer

**Recommended Approach:**

1. **Use Spring Encryption:**
   ```java
   @Autowired
   private TextEncryptor textEncryptor;

   // Before saving
   payer.setSftpPassword(textEncryptor.encrypt(plainPassword));

   // Before use
   String plainPassword = textEncryptor.decrypt(payer.getSftpPassword());
   ```

2. **Use Environment Variables for Global Credentials:**
   ```yaml
   file-delivery:
     sftp:
       password: ${SFTP_PASSWORD:}  # From secure vault
   ```

3. **Use Azure Key Vault / AWS Secrets Manager:**
   - Store sensitive credentials in vault
   - Retrieve at runtime

### SSH Key Authentication

For production, prefer SSH keys over passwords:

```java
DefaultSftpSessionFactory factory = new DefaultSftpSessionFactory(true);
factory.setHost(payer.getSftpHost());
factory.setPort(payer.getSftpPort());
factory.setUser(payer.getSftpUsername());
factory.setPrivateKey(new ClassPathResource("ssh/id_rsa"));
factory.setPrivateKeyPassphrase("passphrase");
```

### Network Security

- Use SFTP (port 22) instead of FTP
- Enable firewall rules for outbound SFTP
- Use IP whitelisting on payer SFTP servers
- Enable MFA for SFTP accounts where supported

---

## 10. Monitoring & Alerting

### Key Metrics to Monitor

1. **Delivery Success Rate:**
   ```sql
   SELECT
     delivery_status,
     COUNT(*) as count,
     ROUND(COUNT(*) * 100.0 / SUM(COUNT(*)) OVER(), 2) as percentage
   FROM file_generation_history
   WHERE generated_at > NOW() - INTERVAL '24 hours'
   GROUP BY delivery_status;
   ```

2. **Average Retry Count:**
   ```sql
   SELECT AVG(delivery_attempt_count) as avg_retries
   FROM file_generation_history
   WHERE delivery_status IN ('DELIVERED', 'FAILED');
   ```

3. **Pending File Age:**
   ```sql
   SELECT
     generated_file_name,
     EXTRACT(EPOCH FROM (NOW() - generated_at))/3600 as hours_pending
   FROM file_generation_history
   WHERE delivery_status = 'PENDING'
   ORDER BY generated_at ASC;
   ```

### Prometheus Metrics

Expose via `/actuator/prometheus`:

```java
// Custom metrics in FileDeliveryService
@Autowired
private MeterRegistry meterRegistry;

// Track delivery attempts
meterRegistry.counter("sftp.delivery.attempts", "status", "success").increment();
meterRegistry.counter("sftp.delivery.attempts", "status", "failed").increment();

// Track delivery duration
Timer.Sample sample = Timer.start(meterRegistry);
// ... perform delivery ...
sample.stop(meterRegistry.timer("sftp.delivery.duration", "payer", payerId));
```

### Alert Conditions

Set up alerts for:
- Pending files older than 1 hour
- Failed delivery rate > 10%
- More than 5 files in FAILED status
- SFTP connection failures

---

## 11. Configuration Examples

### Example 1: Test Payer Configuration

```sql
-- Complete payer setup for testing
INSERT INTO payers (
    payer_id, payer_name, isa_qualifier, isa_sender_id,
    sftp_host, sftp_port, sftp_username, sftp_password, sftp_path,
    is_active, created_by
) VALUES (
    'TEST001', 'Test Insurance Company', 'ZZ', 'TESTINSURANCE',
    'sftp.test.com', 22, 'edi_test_user', 'test_password', '/test/835',
    true, 'system'
);
```

### Example 2: Production Payer Configuration

```sql
-- Production payer with encrypted password
UPDATE payers SET
    sftp_host = 'sftp.bcbs.com',
    sftp_port = 22,
    sftp_username = 'edi835_prod',
    sftp_password = 'ENCRYPTED_PASSWORD_HERE',  -- Use encryption!
    sftp_path = '/inbound/edi/835/remittance',
    requires_special_handling = true
WHERE payer_id = 'BCBS001';
```

### Example 3: Environment Variables

```bash
# .env or docker-compose.yml
SFTP_ENABLED=true
SFTP_SCHEDULER_ENABLED=true
SFTP_DELIVERY_CRON="0 */5 * * * ?"

# For global fallback SFTP (optional)
SFTP_HOST=sftp.default.com
SFTP_PORT=22
SFTP_USERNAME=default_user
SFTP_PASSWORD=default_password
SFTP_REMOTE_DIR=/edi/835
```

---

## 12. Testing

### Manual Testing

1. **Generate EDI file** (file starts in PENDING)
2. **Verify pending status:**
   ```bash
   curl http://localhost:8080/api/v1/delivery/pending
   ```
3. **Trigger delivery:**
   ```bash
   curl -X POST http://localhost:8080/api/v1/delivery/deliver/{fileId}
   ```
4. **Check logs:**
   ```bash
   tail -f logs/edi835-processor.log
   ```
5. **Verify delivered status:**
   ```bash
   curl http://localhost:8080/api/v1/delivery/status/{fileId}
   ```

### Automated Testing

```java
@Test
public void testSftpDelivery() {
    // Mock payer with SFTP config
    Payer payer = createTestPayer();

    // Mock file with content
    FileGenerationHistory file = createTestFile();

    // Attempt delivery
    deliveryService.deliverFile(file.getFileId());

    // Verify status changed to DELIVERED
    assertEquals(DeliveryStatus.DELIVERED, file.getDeliveryStatus());
    assertNotNull(file.getDeliveredAt());
}
```

---

## 13. Implementation Checklist

### Backend

- [x] Database schema with SFTP columns in payers table
- [x] FileGenerationHistory entity with delivery tracking fields
- [x] FileDeliveryService with retry logic
- [x] DeliveryController with REST endpoints
- [ ] Spring Integration SFTP implementation (replace placeholder)
- [ ] Scheduled auto-delivery job (@Scheduled)
- [ ] SFTP configuration validation
- [ ] Password encryption for SFTP credentials
- [ ] Comprehensive error handling and logging

### Frontend

- [x] DeliveryTracking page with statistics
- [x] Pending deliveries table
- [x] Failed deliveries table with retry button
- [ ] "Deliver" button for pending files
- [ ] Failure log viewing modal/dialog
- [x] Auto-refresh with React Query
- [ ] Loading states during delivery attempts
- [ ] Success/error toast notifications

### Configuration

- [ ] Add SFTP columns to PostgreSQL schema (migration SQL)
- [x] Application.yml with file-delivery configuration
- [ ] Environment variables documented
- [ ] Payer SFTP configuration documented
- [ ] Security best practices implemented

### Testing

- [ ] Unit tests for FileDeliveryService
- [ ] Integration tests with mock SFTP server
- [ ] Manual testing with real SFTP server
- [ ] Frontend E2E tests for delivery flow

### Documentation

- [x] This comprehensive guide
- [ ] API documentation (Swagger/OpenAPI)
- [ ] User guide for admin portal
- [ ] Deployment guide with SFTP setup

---

## 14. Next Steps

1. **Immediate (Critical):**
   - Add SFTP columns to PostgreSQL schema
   - Implement Spring Integration SFTP upload
   - Add "Deliver" button to frontend
   - Add scheduled auto-delivery job

2. **Short-term (High Priority):**
   - Implement password encryption
   - Add failure log viewing
   - Comprehensive error handling
   - Testing with real SFTP servers

3. **Medium-term (Important):**
   - SSH key authentication support
   - Prometheus metrics integration
   - Alert configuration
   - Performance optimization

4. **Long-term (Enhancement):**
   - AS2 protocol support (alternative to SFTP)
   - File encryption before upload
   - Delivery confirmation tracking (997/999 acknowledgments)
   - Advanced retry strategies (priority queues)

---

## 15. Support & Resources

### Internal Documentation
- `CLAUDE.md` - Project overview and architecture
- `edi835-processor/README.md` - Backend service documentation
- `edi835-admin-portal/README.md` - Frontend portal documentation

### External Resources
- [Spring Integration SFTP](https://docs.spring.io/spring-integration/reference/html/sftp.html)
- [JSch Documentation](http://www.jcraft.com/jsch/)
- [EDI X12 835 Standard](https://www.x12.org/)
- [HIPAA 5010 Implementation Guide](https://www.cms.gov/Regulations-and-Guidance/Administrative-Simplification/HIPAA-ACA/HIPAAGenInfo/HIPAAStandardsandOperatingRules)

### Contact
For questions or issues, refer to:
- GitHub Issues: [Repository URL]
- Internal Wiki: [Wiki URL]
- Development Team: [Team Contact]

---

**Document Version:** 1.0
**Last Updated:** 2025-10-17
**Author:** Claude Code (AI Assistant)
**Status:** Implementation In Progress

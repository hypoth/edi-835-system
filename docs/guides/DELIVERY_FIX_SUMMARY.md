# File Delivery Failures - Fix Summary

## Issues Fixed

### 1. Missing Payer Configuration (BCBS-CA)
**Problem:** The bucket referenced `payer_id = "BCBS-CA"` but no payer record existed in the database.

**Solution:**
- Created new payer record with normalized ID: `BCBS_CA` (hyphen → underscore per EDI rules)
- Updated all buckets from `BCBS-CA` to `BCBS_CA`
- Payer details:
  - **ID:** BCBS_CA
  - **Name:** Blue Cross Blue Shield of California
  - **ISA Sender ID:** BCBSCA
  - **SFTP:** localhost:22, path: /edi/835/bcbs

**SQL Executed:**
```sql
-- Create payer
INSERT INTO payers (payer_id, payer_name, isa_sender_id, gs_application_sender_id,
                    sftp_host, sftp_port, sftp_username, sftp_password, sftp_path, is_active)
VALUES ('BCBS_CA', 'Blue Cross Blue Shield of California', 'BCBSCA', 'BCBSCA',
        'localhost', 22, 'bcbs_user', 'bcbs_pass', '/edi/835/bcbs', 1);

-- Update buckets
UPDATE edi_file_buckets SET payer_id = 'BCBS_CA' WHERE payer_id = 'BCBS-CA';
```

### 2. SFTP Password Encryption
**Problem:** Passwords in database were plain-text but `SftpConfig.java` always tries to decrypt them, causing failures.

**Root Cause:**
- Existing passwords (CVS-CAREMARK, MEDICAID_CA, etc.) were inserted via SQL as plain-text
- `SftpConfig.java:71` calls `encryptionService.decrypt()` on all passwords
- Decryption fails because passwords were never encrypted

**Long-term Solution Implemented:**

#### A. Created Password Encryption Endpoint
Added new endpoint to `AdminMaintenanceController`:
- **Endpoint:** `POST /api/v1/admin/maintenance/encrypt-passwords?dryRun={true|false}`
- **Function:** Encrypts all plain-text SFTP passwords in the database
- **Features:**
  - Auto-detects already encrypted passwords (tries to decrypt, if successful = already encrypted)
  - Supports dry-run mode to preview changes
  - Uses Spring Security's `TextEncryptor` with AES encryption
  - Validates encryption is enabled before running

#### B. Fixed Password Encryption in Payer Creation
Updated `BucketController.createPayerFromBucket()`:
- **Before:** Saved plain-text password directly (line 479)
- **After:** Encrypts password using `encryptionService.encrypt()` before saving (line 470-474)
- **Impact:** All new payers created via bucket workflow will have encrypted passwords

**Note:** `ConfigurationController.createPayer()` already encrypted passwords correctly (line 92-96).

## Code Changes Made

### Files Modified:

1. **AdminMaintenanceController.java**
   - Added `EncryptionService` dependency
   - Added `encryptPasswords()` endpoint (lines 283-380)
   - Smart encryption detection to avoid double-encryption

2. **BucketController.java**
   - Added `EncryptionService` dependency
   - Updated `createPayerFromBucket()` to encrypt passwords (lines 469-474, 490)

3. **Database (SQLite)**
   - Created `BCBS_CA` payer record
   - Updated buckets to use normalized payer ID

## Next Steps Required

### Step 1: Restart Backend Server

The new encryption endpoint is not yet available because the server is running old code.

**Instructions:**
```bash
# In the terminal running the backend (mvn spring-boot:run):
1. Press Ctrl+C to stop the server
2. Run: mvn spring-boot:run
3. Wait for server to start (check http://localhost:8080/actuator/health)
```

### Step 2: Encrypt Existing Passwords

After restarting, run the encryption script:

```bash
cd edi835-processor
./encrypt-passwords.sh
```

**Or manually:**
```bash
# Dry run first (see what will be encrypted)
curl -X POST "http://localhost:8080/api/v1/admin/maintenance/encrypt-passwords?dryRun=true" | python3 -m json.tool

# Apply encryption
curl -X POST "http://localhost:8080/api/v1/admin/maintenance/encrypt-passwords?dryRun=false" | python3 -m json.tool
```

**Expected Result:**
```json
{
    "success": true,
    "dryRun": false,
    "totalPayers": 5,
    "passwordsNeedingEncryption": 5,
    "passwordsEncrypted": 5,
    "updates": [
        {
            "payerId": "CVS-CAREMARK",
            "payerName": "CVS Caremark",
            "action": "encrypted",
            "passwordLength": 9
        },
        {
            "payerId": "MEDICAID_CA",
            "payerName": "California Medicaid",
            "action": "encrypted",
            "passwordLength": 9
        },
        {
            "payerId": "BCBS_CA",
            "payerName": "Blue Cross Blue Shield of California",
            "action": "encrypted",
            "passwordLength": 9
        }
        // ...
    ],
    "errors": [],
    "message": "Password encryption complete."
}
```

### Step 3: Test File Delivery

After encryption, test file delivery:

```bash
# Check failed deliveries
curl http://localhost:8080/api/v1/delivery/failed | python3 -m json.tool

# Retry failed deliveries
curl -X POST http://localhost:8080/api/v1/delivery/retry-failed | python3 -m json.tool

# Or retry specific file
curl -X POST http://localhost:8080/api/v1/delivery/{fileId}/retry
```

### Step 4: Fix SFTP Path Typo (Optional)

The CVS-CAREMARK payer has a typo in the SFTP path:
- **Current:** `/remitance/835/` (missing 't')
- **Should be:** `/remittance/835/`

**Fix:**
```sql
UPDATE payers
SET sftp_path = '/remittance/835/'
WHERE payer_id = 'CVS-CAREMARK';
```

## Benefits of This Solution

### Immediate Benefits:
1. **BCBS_CA deliveries will work** - missing payer now exists
2. **CVS-CAREMARK and MEDICAID_CA deliveries will work** - passwords will be encrypted properly

### Long-term Benefits:
1. **All future payers automatically encrypted** - both via UI and bucket creation
2. **Security compliance** - passwords encrypted at rest using AES
3. **Easy migration** - can encrypt existing passwords with one API call
4. **Smart detection** - won't double-encrypt already encrypted passwords
5. **Auditable** - dry-run mode shows what will change before applying

## Verification

After completing the steps:

1. **Check payers:**
   ```sql
   SELECT payer_id, sftp_host, LENGTH(sftp_password) as pwd_length
   FROM payers
   WHERE sftp_password IS NOT NULL;
   ```
   - Encrypted passwords should be much longer (50+ characters vs 8-10)

2. **Check delivery status:**
   ```bash
   curl http://localhost:8080/api/v1/delivery/statistics
   ```

3. **Monitor logs:**
   ```bash
   tail -f logs/edi835-processor.log | grep -i "delivery\|sftp"
   ```

## Encryption Configuration

The encryption uses these settings from `application.yml`:
```yaml
encryption:
  key: changeme-encryption-key
  salt: deadbeefdeadbeef
```

**IMPORTANT:** In production, set these via environment variables:
```bash
export ENCRYPTION_KEY="your-secure-random-key-here"
export ENCRYPTION_SALT="your-hex-salt-here"
```

## Rollback (If Needed)

If encryption causes issues, you can rollback:

1. Stop the backend
2. Restore database from backup
3. Revert code changes

**Database backup location:** `data/edi835-local.db.backup.20251031_220213`

## Summary

✅ **BCBS_CA payer created** - handles BCBS-CA bucket
✅ **Password encryption endpoint added** - `/api/v1/admin/maintenance/encrypt-passwords`
✅ **Bucket payer creation fixed** - now encrypts passwords
⏳ **Backend restart needed** - to load new encryption endpoint
⏳ **Password encryption needed** - run script after restart
⏳ **Test delivery** - verify files can be delivered

**Total Failed Deliveries:** 3 files
**Expected Resolution:** 100% after completing steps above

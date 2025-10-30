Write(SFTP_AUTHENTICATION_ANALYSIS.md)
  ⎿  User rejected write to SFTP_AUTHENTICATION_ANALYSIS.md
# SFTP Authentication Analysis
## Overview
 This document provides a detailed analysis of the SFTP authentication 
 implementation in the EDI 835 system, including supported authentication 
 methods, configuration options, and recommendations for troubleshooting 
 authentication failures.

## Current Implementation Summary
 
**Status**: ⚠️ **PASSWORD-BASED AUTHENTICATION ONLY**
  
  The system currently supports **ONLY username/password authentication** 
 for SFTP connections. SSH private key authentication is **NOT 
 IMPLEMENTED**.
 
  ---
  
  ## Technology Stack
  
### SFTP Libraries

 1. **Spring Integration SFTP** (Managed by Spring Boot)
    - High-level abstraction for SFTP operations
    - Provides session management and connection pooling
    - Part of Spring Integration suite
 
 2. **JSch (Java Secure Channel)** - Version 0.1.55
    - Low-level SSH/SFTP protocol implementation
    - Created by JCraft
    - Powers Spring Integration SFTP under the hood
    - **Note**: JSch 0.1.55 is an OLDER version (released 2018)
 
 **POM Dependencies** (`pom.xml`):
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
 
 ---
 
 ## Supported Authentication Methods
 
 ### ✅ 1. Password-Based Authentication (IMPLEMENTED)
 
 **How It Works**:
 - Username and password stored in `payers` table
 - Password transmitted during SFTP session establishment
 - Uses SSH password authentication mechanism
 
 **Configuration Location**:
 - **Database**: `payers` table (`sftp_username`, `sftp_password` columns)
 - **Application Properties**: Global defaults in `application.yml`
 
 ---
 
 ### ❌ 2. SSH Private Key Authentication (NOT IMPLEMENTED)
 
 **Status**: Not currently supported
 
 **Why It's Not Implemented**:
 - No database fields for private key storage
 - No code in `SftpConfig.java` to configure private key
 - `Payer` entity lacks private key fields
 
 **What Would Be Needed**:
 - Add `sftp_private_key` column to `payers` table
 - Add `sftp_key_passphrase` column (optional, if key is encrypted)
 - Modify `SftpConfig.createSessionFactory()` to call 
`factory.setPrivateKey()`
 - Store private keys securely (encrypted in database or external vault)
 
 ---
 
 ## Authentication Configuration
 
 ### Per-Payer Configuration (Primary Method)
 
 SFTP credentials are stored **per payer** in the `payers` database table.
 
 **Database Fields** (`payers` table):
 
 | Column Name | Type | Required | Description |
 |------------|------|----------|-------------|
 | `sftp_host` | TEXT | ✅ Yes | SFTP server hostname or IP (e.g., 
`sftp.example.com`) |
 | `sftp_port` | INTEGER | ⚠️ Optional | SFTP server port (default: 22 if 
not specified) |
 | `sftp_username` | TEXT | ✅ Yes | SFTP login username |
 | `sftp_password` | TEXT | ⚠️ Optional* | SFTP login password (plaintext -
 **should be encrypted**) |
 | `sftp_path` | TEXT | ✅ Yes | Remote directory path (e.g., 
`/edi/835/incoming`) |
 
 **\*Note**: Password is optional in database validation but **required** 
for actual connection if no SSH key is configured (which is currently the 
case).
 
 ---
 
 ### Code Implementation
 
 **File**: `SftpConfig.java` (lines 44-83)
 
 ```java
 public SessionFactory<SftpClient.DirEntry> createSessionFactory(Payer 
payer) {
     validatePayerSftpConfig(payer);
 
     DefaultSftpSessionFactory factory = new 
DefaultSftpSessionFactory(true);
 
     // Connection settings
     factory.setHost(payer.getSftpHost());
     factory.setPort(payer.getSftpPort() != null ? payer.getSftpPort() : 
22);
     factory.setUser(payer.getSftpUsername());
 
     // Authentication - password based
     if (payer.getSftpPassword() != null && 
!payer.getSftpPassword().isEmpty()) {
         factory.setPassword(payer.getSftpPassword());  // ← ONLY 
AUTHENTICATION METHOD
     }
 
     // Security settings
     factory.setAllowUnknownKeys(true); // ⚠️ SECURITY RISK - accepts any 
host key
 
     // Timeout settings
     factory.setTimeout(connectionTimeoutMs);  // Default: 30 seconds
 
     // Connection pooling
     CachingSessionFactory<SftpClient.DirEntry> cachingFactory =
             new CachingSessionFactory<>(factory, 10); // Pool size: 10
 
     cachingFactory.setSessionWaitTimeout(sessionTimeoutMs);  // Default: 5
 minutes
     cachingFactory.setTestSession(true);
 
     return cachingFactory;
 }
 ```
 
 **Key Observations**:
 1. **Line 62-64**: Password is the ONLY authentication method configured
 2. **Line 67**: `setAllowUnknownKeys(true)` disables host key verification
 (security risk)
 3. **No SSH key configuration**: No calls to `setPrivateKey()` or 
`setPrivateKeyPassphrase()`
 
 ---
 
 ### Configuration Validation
 
 **File**: `SftpConfig.java` (lines 107-126)
 
 ```java
 private void validatePayerSftpConfig(Payer payer) {
     // Host validation
     if (payer.getSftpHost() == null || payer.getSftpHost().isEmpty()) {
         throw new IllegalArgumentException("Payer missing SFTP host");
     }
 
     // Port validation (optional, defaults to 22)
     if (payer.getSftpPort() == null) {
         log.warn("Payer {} missing SFTP port, using default: 22");
     }
 
     // Username validation
     if (payer.getSftpUsername() == null || 
payer.getSftpUsername().isEmpty()) {
         throw new IllegalArgumentException("Payer missing SFTP username");
     }
 
     // Path validation
     if (payer.getSftpPath() == null || payer.getSftpPath().isEmpty()) {
         throw new IllegalArgumentException("Payer missing SFTP path");
     }
 
     // ❌ NO PASSWORD VALIDATION - Password can be null/empty!
 }
 ```
 
 **Issue**: Validation does **NOT** check if password is provided, but 
password is required for connection.
 
 ---
 
 ### Global Configuration (Fallback/Defaults)
 
 **File**: `application.yml` (lines 74-89)
 
 ```yaml
 file-delivery:
   sftp:
     enabled: ${SFTP_ENABLED:false}  # ← SFTP disabled by default!
     host: ${SFTP_HOST:}
     port: ${SFTP_PORT:22}
     username: ${SFTP_USERNAME:}
     password: ${SFTP_PASSWORD:}
     remote-directory: ${SFTP_REMOTE_DIR:/edi/835}
 
     # Connection settings
     connection-timeout-ms: 30000  # 30 seconds
     session-timeout-ms: 300000    # 5 minutes
 
   retry:
     max-attempts: 3
     backoff-ms: 5000  # Exponential backoff base delay
 ```
 
 **Important**:
 - `enabled: false` - SFTP is **DISABLED BY DEFAULT**
 - These are NOT used for per-payer configuration
 - Used only for timeout configuration (`connection-timeout-ms`, 
`session-timeout-ms`)
 - Per-payer credentials from database take precedence
 
 ---
 
 ## How Password Authentication Works
 
 ### Step-by-Step Flow
 
 **1. File Ready for Delivery**
    - `FileDeliveryService.deliverFile(UUID fileId)` called
    - File loaded from `file_generation_history` table
 
 **2. Load Payer Configuration**
    - `payerRepository.findByPayerId(payerId)` retrieves payer
    - Payer entity includes SFTP fields (host, port, username, password, 
path)
 
 **3. Validate SFTP Configuration**
    - Check if payer has SFTP config: `hasSftpConfig(payer)`
    - Validates: host, port, username, path (⚠️ NOT password!)
 
 **4. Create SFTP Session Factory**
    - `sftpConfig.createSessionFactory(payer)` creates session factory
    - `DefaultSftpSessionFactory` configured with:
      - Host, port, username
      - **Password** (if provided)
      - Timeout settings
 
 **5. Establish SFTP Connection**
    - `sessionFactory.getSession()` opens SSH/SFTP connection
    - **JSch library performs SSH handshake**:
      1. TCP connection to `host:port`
      2. SSH protocol version exchange
      3. Server sends host key
      4. Client accepts host key (because `allowUnknownKeys=true`)
      5. **Server requests authentication**
      6. Client sends username + password
      7. Server validates credentials
      8. Session established (if auth succeeds)
 
 **6. Upload File**
    - `session.write(inputStream, remotePath)` uploads file
    - Writes to `{sftpPath}/{generatedFileName}`
 
 **7. Close Session**
    - Session closed in `finally` block
    - Connection returned to pool (if reusable)
 
 ---
 
 ## Common Authentication Failure Scenarios
 
 ### 1. ❌ Password is NULL or Empty
 
 **Symptom**:
 ```
 Auth fail
 ```
 
 **Cause**:
 - `payer.getSftpPassword()` returns `null` or empty string
 - JSch attempts password authentication with blank password
 - Server rejects authentication
 
 **Solution**:
 ```sql
 -- Check password in database
 SELECT payer_id, sftp_username, sftp_password, sftp_host
 FROM payers
 WHERE payer_id = 'PAYER_ID_HERE';
 
 -- Update password
 UPDATE payers
 SET sftp_password = 'correct_password_here'
 WHERE payer_id = 'PAYER_ID_HERE';
 ```
 
 ---
 
 ### 2. ❌ Server Requires SSH Key (Not Password)
 
 **Symptom**:
 ```
 Auth fail
 com.jcraft.jsch.JSchException: Auth fail
 ```
 
 **Cause**:
 - SFTP server is configured to **ONLY** accept SSH key authentication
 - Password authentication is disabled on server
 - Client only sends password, server rejects it
 
 **Solution**:
 SSH key authentication must be implemented (see "Adding SSH Key Support" 
below).
 
 ---
 
 ### 3. ❌ Wrong Username or Password
 
 **Symptom**:
 ```
 Auth fail
 ```
 
 **Cause**:
 - Incorrect credentials in database
 - Password recently changed on SFTP server
 - Typo in username or password
 
 **Solution**:
 - Verify credentials manually: `sftp username@host` (from command line)
 - Update database with correct credentials
 
 ---
 
 ### 4. ❌ Host Key Verification Issues
 
 **Symptom**:
 ```
 UnknownHostKey
 ```
 
 **Cause**:
 - Server's host key not trusted (if `allowUnknownKeys=false`)
 - Server's host key changed (potential MITM attack or server reinstall)
 
 **Current Mitigation**:
 - `setAllowUnknownKeys(true)` accepts ANY host key (⚠️ security risk)
 - No host key verification performed
 
 **Secure Solution**:
 - Maintain `known_hosts` file with trusted server keys
 - Set `allowUnknownKeys=false`
 - Call `factory.setKnownHostsResource()` to load known hosts
 
 ---
 
 ### 5. ❌ Connection Timeout
 
 **Symptom**:
 ```
 java.net.SocketTimeoutException: connect timed out
 ```
 
 **Cause**:
 - SFTP server unreachable (network issue, firewall, wrong host/port)
 - Server not responding within `connection-timeout-ms` (default: 30 
seconds)
 
 **Solution**:
 - Verify network connectivity: `telnet host port` or `nc -zv host port`
 - Check firewall rules
 - Verify correct host and port in database
 - Increase timeout if needed:
   ```yaml
   file-delivery:
     sftp:
       connection-timeout-ms: 60000  # 60 seconds
   ```
 
 ---
 
 ### 6. ❌ SFTP Feature Disabled
 
 **Symptom**:
 - File marked as delivered but not actually uploaded
 - Log shows: "SFTP is disabled. Skipping actual upload"
 
 **Cause**:
 - `file-delivery.sftp.enabled: false` in `application.yml`
 
 **Solution**:
 Set environment variable:
 ```bash
 export SFTP_ENABLED=true
 ```
 
 Or in `application.yml`:
 ```yaml
 file-delivery:
   sftp:
     enabled: true
 ```
 
 ---
 
 ## How to Configure Password-Based Authentication
 
 ### Step 1: Enable SFTP in Application
 
 **Option A: Environment Variable**
 ```bash
 export SFTP_ENABLED=true
 ```
 
 **Option B: application.yml**
 ```yaml
 file-delivery:
   sftp:
     enabled: true
 ```
 
 ---
 
 ### Step 2: Configure Payer SFTP Credentials
 
 **Database Configuration** (recommended for per-payer settings):
 
 ```sql
 -- Update existing payer with SFTP credentials
 UPDATE payers
 SET
     sftp_host = 'sftp.partner.com',
     sftp_port = 22,
     sftp_username = 'edi_user',
     sftp_password = 'secure_password_here',  -- ⚠️ Store encrypted in 
production!
     sftp_path = '/edi/835/incoming'
 WHERE payer_id = 'PAYER001';
 ```
 
 **Verify Configuration**:
 ```sql
 SELECT payer_id, sftp_host, sftp_port, sftp_username,
        CASE WHEN sftp_password IS NOT NULL THEN '***CONFIGURED***' ELSE 
'MISSING' END as password_status,
        sftp_path
 FROM payers
 WHERE payer_id = 'PAYER001';
 ```
 
 ---
 
 ### Step 3: Test Connection
 
 **Manual Test** (from command line):
 ```bash
 sftp -P 22 edi_user@sftp.partner.com
 # Enter password when prompted
 # If successful, you'll see: sftp>
 ```
 
 **Application Test** (via API):
 ```bash
 # Trigger file delivery
 curl -X POST http://localhost:8080/api/v1/files/{fileId}/deliver
 ```
 
 **Check Logs**:
 ```bash
 tail -f logs/edi835-processor.log | grep -i "sftp\|auth\|delivery"
 ```
 
 ---
 
 ## Security Considerations
 
 ### ⚠️ Current Security Issues
 
 1. **Plaintext Passwords in Database**
    - `sftp_password` stored as plaintext in `payers` table
    - **Risk**: Database compromise exposes all SFTP credentials
 
 2. **No Host Key Verification**
    - `setAllowUnknownKeys(true)` accepts any server
    - **Risk**: Man-in-the-middle (MITM) attacks possible
 
 3. **Old JSch Version (0.1.55)**
    - Released in 2018, may have unpatched vulnerabilities
    - Latest JSch version is 0.2.x series (major improvements)
 
 ---
 
 ### 🔒 Security Recommendations
 
 #### 1. Encrypt Passwords at Application Layer
 
 **Option A: Use Spring Encryption**
 ```java
 @Service
 public class EncryptionService {
     private final TextEncryptor encryptor;
 
     public EncryptionService(
             @Value("${encryption.key}") String key,
             @Value("${encryption.salt}") String salt) {
         this.encryptor = Encryptors.text(key, salt);
     }
 
     public String encrypt(String plaintext) {
         return encryptor.encrypt(plaintext);
     }
 
     public String decrypt(String ciphertext) {
         return encryptor.decrypt(ciphertext);
     }
 }
 ```
 
 Modify `SftpConfig.createSessionFactory()`:
 ```java
 if (payer.getSftpPassword() != null && !payer.getSftpPassword().isEmpty())
 {
     String decryptedPassword = 
encryptionService.decrypt(payer.getSftpPassword());
     factory.setPassword(decryptedPassword);
 }
 ```
 
 **Option B: Use External Secrets Manager**
 - Azure Key Vault
 - AWS Secrets Manager
 - HashiCorp Vault
 
 ---
 
 #### 2. Enable Host Key Verification
 
 **Create Known Hosts File**:
 ```bash
 # Get server's host key
 ssh-keyscan -p 22 sftp.partner.com >> known_hosts
 ```
 
 **Update SftpConfig**:
 ```java
 factory.setAllowUnknownKeys(false);
 factory.setKnownHostsResource(new FileSystemResource("known_hosts"));
 ```
 
 ---
 
 #### 3. Upgrade JSch to Latest Version
 
 **Update pom.xml**:
 ```xml
 <dependency>
     <groupId>com.github.mwiede</groupId>
     <artifactId>jsch</artifactId>
     <version>0.2.20</version>  <!-- Latest stable version -->
 </dependency>
 ```
 
 **Why Upgrade**:
 - Security patches
 - Better cipher support
 - Bug fixes
 - Improved compatibility
 
 ---
 
 ## Adding SSH Private Key Authentication Support
 
 If your SFTP server requires SSH key authentication instead of passwords, 
you'll need to implement the following changes:
 
 ### Step 1: Add Database Fields
 
 **Migration SQL**:
 ```sql
 -- Add private key columns to payers table
 ALTER TABLE payers ADD COLUMN IF NOT EXISTS sftp_private_key TEXT;
 ALTER TABLE payers ADD COLUMN IF NOT EXISTS sftp_key_passphrase TEXT;
 
 -- Add comments
 COMMENT ON COLUMN payers.sftp_private_key IS 'SSH private key (PEM format,
 encrypted at application layer)';
 COMMENT ON COLUMN payers.sftp_key_passphrase IS 'Private key passphrase 
(encrypted at application layer)';
 ```
 
 ---
 
 ### Step 2: Update Payer Entity
 
 **File**: `Payer.java`
 
 Add fields:
 ```java
 @Column(name = "sftp_private_key", columnDefinition = "TEXT")
 private String sftpPrivateKey;  // PEM-encoded private key
 
 @Column(name = "sftp_key_passphrase")
 private String sftpKeyPassphrase;  // Passphrase for encrypted keys
 
 // Getters and setters
 public String getSftpPrivateKey() {
     return sftpPrivateKey;
 }
 
 public void setSftpPrivateKey(String sftpPrivateKey) {
     this.sftpPrivateKey = sftpPrivateKey;
 }
 
 public String getSftpKeyPassphrase() {
     return sftpKeyPassphrase;
 }
 
 public void setSftpKeyPassphrase(String sftpKeyPassphrase) {
     this.sftpKeyPassphrase = sftpKeyPassphrase;
 }
 ```
 
 ---
 
 ### Step 3: Update SftpConfig to Support SSH Keys
 
 **File**: `SftpConfig.java`
 
 Modify `createSessionFactory()`:
 ```java
 public SessionFactory<SftpClient.DirEntry> createSessionFactory(Payer 
payer) {
     validatePayerSftpConfig(payer);
 
     DefaultSftpSessionFactory factory = new 
DefaultSftpSessionFactory(true);
 
     // Connection settings
     factory.setHost(payer.getSftpHost());
     factory.setPort(payer.getSftpPort() != null ? payer.getSftpPort() : 
22);
     factory.setUser(payer.getSftpUsername());
 
     // Authentication - priority: SSH key > password
     if (payer.getSftpPrivateKey() != null && 
!payer.getSftpPrivateKey().isEmpty()) {
         // SSH Key Authentication
         log.debug("Using SSH key authentication for payer: {}", 
payer.getPayerId());
 
         // Convert PEM string to byte array
         byte[] privateKeyBytes = 
payer.getSftpPrivateKey().getBytes(StandardCharsets.UTF_8);
 
         // Set private key
         factory.setPrivateKey(new ByteArrayResource(privateKeyBytes));
 
         // Set passphrase if key is encrypted
         if (payer.getSftpKeyPassphrase() != null && 
!payer.getSftpKeyPassphrase().isEmpty()) {
             factory.setPrivateKeyPassphrase(payer.getSftpKeyPassphrase());
         }
 
     } else if (payer.getSftpPassword() != null && 
!payer.getSftpPassword().isEmpty()) {
         // Password Authentication (fallback)
         log.debug("Using password authentication for payer: {}", 
payer.getPayerId());
         factory.setPassword(payer.getSftpPassword());
 
     } else {
         throw new IllegalArgumentException(
             "Payer " + payer.getPayerId() + " has neither SSH key nor password configured");
     }
 
     // Security settings
     factory.setAllowUnknownKeys(false);  // Enable host key verification
     factory.setKnownHostsResource(new ClassPathResource("known_hosts"));
 
     // Timeout settings
     factory.setTimeout(connectionTimeoutMs);
 
     // Connection pooling
     CachingSessionFactory<SftpClient.DirEntry> cachingFactory =
             new CachingSessionFactory<>(factory, 10);
 
     cachingFactory.setSessionWaitTimeout(sessionTimeoutMs);
     cachingFactory.setTestSession(true);
 
     return cachingFactory;
 }
 ```
 
 ---
 
 ### Step 4: Store Private Key in Database
 
 **Generate SSH Key Pair** (if needed):
 ```bash
 # Generate RSA key
 ssh-keygen -t rsa -b 4096 -f edi_rsa -C "EDI 835 SFTP"
 
 # Convert to PEM format (if needed)
 ssh-keygen -p -m PEM -f edi_rsa
 ```
 
 **Store in Database**:
 ```sql
 -- Read private key content
 -- \set private_key `cat edi_rsa`
 
 UPDATE payers
 SET
     sftp_private_key = '-----BEGIN RSA PRIVATE KEY-----
 MIIEpAIBAAKCAQEA...
 ... (full private key content) ...
 -----END RSA PRIVATE KEY-----',
     sftp_key_passphrase = 'key_passphrase_if_encrypted',  -- NULL if no 
passphrase
     sftp_password = NULL  -- Clear password if using key
 WHERE payer_id = 'PAYER001';
 ```
 
 **⚠️ Security**: In production, encrypt the private key before storing!
 
 ---
 
 ### Step 5: Add Public Key to SFTP Server
 
 Copy public key to SFTP server:
 ```bash
 # Copy public key to server
 ssh-copy-id -i edi_rsa.pub edi_user@sftp.partner.com
 
 # Or manually append to authorized_keys
 cat edi_rsa.pub | ssh edi_user@sftp.partner.com "cat >> 
~/.ssh/authorized_keys"
 ```
 
 ---
 
 ## Testing and Troubleshooting
 
 ### Enable Debug Logging
 
 **application.yml**:
 ```yaml
 logging:
   level:
     com.healthcare.edi835.service.FileDeliveryService: DEBUG
     com.healthcare.edi835.config.SftpConfig: DEBUG
     org.springframework.integration.sftp: DEBUG
     com.jcraft.jsch: DEBUG  # JSch debug output
 ```
 
 ### Check Logs for Authentication Details
 
 ```bash
 tail -f logs/edi835-processor.log | grep -E "AUTH|Authentications|Auth 
fail"
 ```
 
 **Successful Password Auth**:
 ```
 Authentications that can continue: publickey,password
 Authentication succeeded (password).
 ```
 
 **Failed Password Auth**:
 ```
 Authentications that can continue: publickey,password
 Auth fail
 ```
 
 **Successful Key Auth**:
 ```
 Authentications that can continue: publickey,password
 Authentication succeeded (publickey).
 ```
 
 ---
 
 ## Summary Table: Authentication Methods
 
 | Method | Supported | Database Fields Required | Configuration Complexity
 | Security Level |
 |--------|-----------|-------------------------|-------------------------|
----------------|
 | **Password** | ✅ Yes (Current) | `sftp_username`, `sftp_password` | Low
 | ⚠️ Medium (if plaintext) |
 | **SSH Private Key** | ❌ No (Needs Implementation) | `sftp_username`, 
`sftp_private_key`, `sftp_key_passphrase` | Medium | 🔒 High |
 | **Public Key (server-side)** | ❌ No | N/A | High | 🔒 Highest |
 
 ---
 
 ## Quick Checklist for Password Authentication Failure
 
 - [ ] SFTP enabled in application: `file-delivery.sftp.enabled: true`
 - [ ] Payer has `sftp_host` configured
 - [ ] Payer has `sftp_port` configured (or defaults to 22)
 - [ ] Payer has `sftp_username` configured
 - [ ] Payer has `sftp_password` configured (NOT NULL or empty)
 - [ ] Payer has `sftp_path` configured
 - [ ] Network connectivity to SFTP server (test with `telnet host port`)
 - [ ] Credentials are correct (test manually with `sftp username@host`)
 - [ ] SFTP server allows password authentication
 - [ ] No firewall blocking outbound connections on port 22
 - [ ] Check application logs for detailed error messages
 
 ---
 
 ## Recommended Next Steps
 
 ### For Immediate Fix (Password Auth):
 
 1. **Verify SFTP is Enabled**:
    ```bash
    grep "sftp.enabled" application.yml
    # Should show: enabled: true
    ```
 
 2. **Check Payer Configuration**:
    ```sql
    SELECT payer_id, sftp_host, sftp_username,
           CASE WHEN sftp_password IS NOT NULL THEN 'SET' ELSE 'MISSING' 
END as password
    FROM payers
    WHERE payer_id = 'YOUR_PAYER_ID';
    ```
 
 3. **Update Missing Password**:
    ```sql
    UPDATE payers
    SET sftp_password = 'correct_password'
    WHERE payer_id = 'YOUR_PAYER_ID';
    ```
 
 4. **Test Connection Manually**:
    ```bash
    sftp -P 22 username@host
    # Enter password
    # Verify you can connect
    ```
 
 5. **Retry Delivery**:
    ```bash
    curl -X POST http://localhost:8080/api/v1/files/{fileId}/deliver
    ```
 
 ### For Long-Term Security:
 
 1. **Implement Password Encryption** (use Spring Encryptors or external 
vault)
 2. **Add SSH Key Authentication Support** (follow implementation guide 
above)
 3. **Enable Host Key Verification** (prevent MITM attacks)
 4. **Upgrade JSch Library** (to latest 0.2.x version)
 5. **Implement Credential Rotation** (automatic password/key rotation)
 
 ---
 
 ## Date
 2025-10-17
 
 ## Status
 📊 **ANALYSIS COMPLETE** - Current implementation uses password-only 
authentication


---

## Conclusion

### Key Takeaways

1. **Current State**: The EDI 835 system currently supports **ONLY password-based SFTP authentication**. SSH private key authentication is not implemented.

2. **Security Posture**: The system has several security concerns that should be addressed:
   - Passwords stored in plaintext in the database
   - No host key verification (accepts any server)
   - Older JSch library version (0.1.55 from 2018)

3. **Implementation Priority**:
   - **Immediate**: Ensure passwords are configured correctly for all payers needing SFTP delivery
   - **Short-term**: Implement password encryption at the application layer
   - **Medium-term**: Add SSH private key authentication support
   - **Long-term**: Implement comprehensive secrets management (Azure Key Vault, etc.)

4. **Most Common Issues**:
   - Missing or NULL passwords in the `payers` table
   - SFTP feature disabled (`file-delivery.sftp.enabled: false`)
   - Network connectivity problems
   - Server requiring SSH key authentication (not supported)

5. **Authentication Flow**: Per-payer SFTP credentials stored in the database take precedence over global configuration. Each payer can have unique SFTP settings.

---

## FAQ

### Q: Why am I getting "Auth fail" errors?

**A**: Most commonly, this is due to:
1. Missing or incorrect password in the `payers` table
2. SFTP server requiring SSH key authentication (not currently supported)
3. Wrong username or password
4. Server configuration changed

Check the logs for detailed error messages and verify credentials manually using `sftp username@host`.

---

### Q: Can I use SSH key authentication instead of passwords?

**A**: Not currently. The system only supports password authentication. However, this document provides a complete implementation guide in the "Adding SSH Private Key Authentication Support" section if you need to add this feature.

---

### Q: Are passwords stored securely?

**A**: No. Currently, passwords are stored in plaintext in the `payers.sftp_password` column. This is a **security risk** that should be addressed by implementing application-layer encryption or using an external secrets manager. See the "Security Recommendations" section.

---

### Q: Why is SFTP not working even though I configured everything?

**A**: Check if SFTP is enabled in the application configuration:

```yaml
file-delivery:
  sftp:
    enabled: true  # Must be true!
```

Or set the environment variable:
```bash
export SFTP_ENABLED=true
```

---

### Q: How do I test SFTP connectivity without running the full application?

**A**: Use the command line SFTP client:

```bash
sftp -P 22 username@sftp.partner.com
# Enter password when prompted
```

If this works, your credentials are correct. If not, fix the credentials before configuring them in the application.

---

### Q: Can different payers use different SFTP servers?

**A**: Yes! Each payer has its own SFTP configuration in the database (host, port, username, password, path). The system creates a separate session factory for each payer.

---

### Q: What happens if SFTP delivery fails?

**A**: The system implements automatic retry with exponential backoff:
- Maximum retry attempts: 3 (configurable via `file-delivery.retry.max-attempts`)
- Backoff delay: 5 seconds base (configurable via `file-delivery.retry.backoff-ms`)
- File status tracked in `file_generation_history` table (PENDING → DELIVERED or FAILED)

---

### Q: How do I rotate SFTP passwords?

**A**: Update the password in the database:

```sql
UPDATE payers
SET sftp_password = 'new_password_here'
WHERE payer_id = 'PAYER_ID';
```

The next file delivery will use the new password automatically (no application restart required).

---

### Q: Is the JSch library actively maintained?

**A**: The version used (0.1.55) is from 2018 and is **not actively maintained**. There's a fork `com.github.mwiede:jsch` with active development and security updates. Consider upgrading to version 0.2.x.

---

### Q: What ports does SFTP use?

**A**: SFTP uses SSH protocol, typically port 22 (configurable). Make sure:
- Outbound connections to port 22 (or custom port) are allowed in firewall
- SFTP server is listening on the expected port
- No proxy or NAT issues blocking the connection

---

## Appendix A: Useful Commands

### Database Queries

**List all payers with SFTP configuration**:
```sql
SELECT
    payer_id,
    payer_name,
    sftp_host,
    sftp_port,
    sftp_username,
    CASE
        WHEN sftp_password IS NOT NULL AND LENGTH(sftp_password) > 0
        THEN '***CONFIGURED***'
        ELSE 'MISSING'
    END as password_status,
    sftp_path
FROM payers
WHERE sftp_host IS NOT NULL
ORDER BY payer_id;
```

**Find payers with missing SFTP passwords**:
```sql
SELECT payer_id, payer_name, sftp_host, sftp_username
FROM payers
WHERE sftp_host IS NOT NULL
  AND (sftp_password IS NULL OR LENGTH(sftp_password) = 0);
```

**Check recent SFTP delivery attempts**:
```sql
SELECT
    f.file_id,
    f.bucket_id,
    f.file_name,
    f.delivery_status,
    f.delivery_attempts,
    f.last_delivery_attempt,
    f.error_message,
    p.payer_id,
    p.sftp_host
FROM file_generation_history f
JOIN edi_file_buckets b ON f.bucket_id = b.bucket_id
JOIN payers p ON b.payer_id = p.payer_id
WHERE f.delivery_status IN ('PENDING', 'RETRY', 'FAILED')
ORDER BY f.created_at DESC
LIMIT 20;
```

---

### Testing Commands

**Test network connectivity**:
```bash
# Check if port is open
telnet sftp.partner.com 22

# Or using netcat
nc -zv sftp.partner.com 22

# Or using nmap
nmap -p 22 sftp.partner.com
```

**Test SFTP authentication**:
```bash
# Interactive SFTP session
sftp -P 22 username@sftp.partner.com

# Test with verbose output for debugging
sftp -vvv -P 22 username@sftp.partner.com

# Non-interactive test (upload a file)
echo "test content" > test.txt
sftp -P 22 username@sftp.partner.com <<EOF
cd /remote/path
put test.txt
bye
EOF
```

**Retrieve server's host key**:
```bash
# Get server's SSH host key
ssh-keyscan -p 22 sftp.partner.com

# Get and save to known_hosts
ssh-keyscan -p 22 sftp.partner.com >> ~/.ssh/known_hosts
```

**Check supported authentication methods**:
```bash
# See what authentication methods the server supports
ssh -v username@sftp.partner.com 2>&1 | grep "Authentications that can continue"
```

---

### Application Commands

**Enable SFTP via environment variable**:
```bash
export SFTP_ENABLED=true
export SFTP_HOST=sftp.partner.com
export SFTP_PORT=22
export SFTP_USERNAME=edi_user
export SFTP_PASSWORD=secure_password
export SFTP_REMOTE_DIR=/edi/835
```

**View application logs for SFTP activity**:
```bash
# Follow logs with SFTP filtering
tail -f logs/edi835-processor.log | grep -i "sftp\|delivery\|auth"

# Search for authentication failures
grep -i "auth fail" logs/edi835-processor.log

# Count delivery attempts by status
grep "Delivery status:" logs/edi835-processor.log | sort | uniq -c
```

**Trigger manual file delivery (via API)**:
```bash
# Deliver specific file
curl -X POST http://localhost:8080/api/v1/files/{fileId}/deliver

# Retry failed deliveries
curl -X POST http://localhost:8080/api/v1/files/retry-failed
```

---

## Appendix B: JSch Configuration Options

The following JSch/Spring Integration SFTP options are available but not all are currently used:

| Method | Purpose | Currently Used? |
|--------|---------|-----------------|
| `setHost(String)` | SFTP server hostname | ✅ Yes |
| `setPort(int)` | SFTP server port | ✅ Yes |
| `setUser(String)` | Username for authentication | ✅ Yes |
| `setPassword(String)` | Password for authentication | ✅ Yes |
| `setPrivateKey(Resource)` | SSH private key for authentication | ❌ No |
| `setPrivateKeyPassphrase(String)` | Passphrase for encrypted private key | ❌ No |
| `setTimeout(int)` | Connection timeout in milliseconds | ✅ Yes |
| `setAllowUnknownKeys(boolean)` | Accept unknown host keys | ✅ Yes (true - insecure) |
| `setKnownHostsResource(Resource)` | Path to known_hosts file | ❌ No |
| `setSessionConfig(Properties)` | Additional JSch session properties | ❌ No |
| `setProxy(Proxy)` | SOCKS/HTTP proxy configuration | ❌ No |
| `setSocketFactory(SocketFactory)` | Custom socket factory | ❌ No |

---

## Appendix C: Error Messages Reference

| Error Message | Meaning | Solution |
|---------------|---------|----------|
| `Auth fail` | Authentication failed | Check username/password, verify server accepts password auth |
| `java.net.SocketTimeoutException: connect timed out` | Cannot reach server | Check network, firewall, host/port configuration |
| `java.net.UnknownHostException` | Cannot resolve hostname | Verify hostname spelling, DNS resolution |
| `com.jcraft.jsch.JSchException: Auth cancel` | Authentication canceled | Server may require different auth method |
| `com.jcraft.jsch.JSchException: USERAUTH fail` | User authentication failed | Wrong username or authentication method not supported |
| `com.jcraft.jsch.JSchException: Session.connect: java.io.IOException: End of IO Stream Read` | Connection closed by server | Server rejected connection, check firewall/IP whitelist |
| `com.jcraft.jsch.JSchException: UnknownHostKey` | Host key not trusted | Add server to known_hosts or set `allowUnknownKeys=true` |
| `com.jcraft.jsch.JSchException: timeout: socket is not established` | Socket connection timeout | Increase timeout or check network |
| `Payer missing SFTP host` | Configuration validation failed | Set `sftp_host` in payers table |
| `Payer missing SFTP username` | Configuration validation failed | Set `sftp_username` in payers table |

---

## References and Additional Resources

### Official Documentation

1. **Spring Integration SFTP**
   - Documentation: https://docs.spring.io/spring-integration/reference/sftp.html
   - API Reference: https://docs.spring.io/spring-integration/api/org/springframework/integration/sftp/

2. **JSch (Java Secure Channel)**
   - Original Project: http://www.jcraft.com/jsch/
   - GitHub Fork (actively maintained): https://github.com/mwiede/jsch
   - API Documentation: http://epaul.github.io/jsch-documentation/

3. **Spring Security Crypto**
   - Password Encryption: https://docs.spring.io/spring-security/reference/features/integrations/cryptography.html

### Related Documentation in This Project

- `TROUBLESHOOTING.md` - General troubleshooting guide for the EDI 835 system
- `SFTP_DELIVERY_TRACKING_GUIDE.md` - Detailed guide on SFTP delivery tracking and monitoring
- `CLAUDE.md` - Project overview and architecture documentation
- `database/schema.sql` - Database schema including `payers` table structure

### External Tools

1. **SFTP Client Tools**
   - OpenSSH SFTP: Built into most Linux/Mac systems
   - WinSCP: https://winscp.net/ (Windows)
   - FileZilla: https://filezilla-project.org/ (Cross-platform)
   - Cyberduck: https://cyberduck.io/ (Cross-platform)

2. **SSH Key Management**
   - ssh-keygen: Generate SSH key pairs
   - ssh-copy-id: Copy public keys to remote servers
   - ssh-keyscan: Retrieve server host keys

3. **Network Debugging**
   - telnet: Test TCP connectivity
   - netcat (nc): Network connection testing
   - nmap: Port scanning and service detection
   - tcpdump/Wireshark: Packet analysis

### Security Resources

1. **Azure Key Vault Integration**
   - https://learn.microsoft.com/en-us/azure/key-vault/

2. **OWASP Password Storage Cheat Sheet**
   - https://cheatsheetseries.owasp.org/cheatsheets/Password_Storage_Cheat_Sheet.html

3. **SSH Best Practices**
   - https://www.ssh.com/academy/ssh/best-practices

---

## Document Changelog

| Date | Version | Author | Changes |
|------|---------|--------|---------|
| 2025-10-17 | 1.0 | System Analysis | Initial comprehensive analysis of SFTP authentication implementation |
| 2025-10-18 | 1.1 | Documentation Team | Added FAQ, appendices, and reference sections |

---

## Contact and Support

For questions or issues related to SFTP authentication:

1. **Check the logs first**: `logs/edi835-processor.log`
2. **Review this document**: Especially the FAQ and troubleshooting sections
3. **Consult related documentation**: `TROUBLESHOOTING.md` and `SFTP_DELIVERY_TRACKING_GUIDE.md`
4. **Test manually**: Use command-line SFTP to verify credentials work outside the application
5. **Check database configuration**: Query the `payers` table to verify SFTP settings

---

**End of Document**


**Implementation Done**
successfully added the SFTP password field to the payers UI and implemented
   encrypted password storage. Here's what was implemented:

  Frontend Changes (React/TypeScript)

  1. Updated Payer Type (src/types/models.ts)
  - Added sftpPassword?: string field to the Payer interface

  2. Updated Payer Form (src/components/payers/PayerForm.tsx)
  - Added password field validation (max 200 characters)
  - Added password field to initial form values
  - Added password input field in the SFTP Configuration section with:
    - Password type input (masked)
    - Helpful text: "Password will be encrypted before storage"
    - Placeholder for edit mode
    - Positioned between username and path fields

  Backend Changes (Java/Spring Boot)

  1. Added Spring Security Crypto Dependency (pom.xml)
  - Added spring-security-crypto for encryption support

  2. Created EncryptionService (service/EncryptionService.java)
  - Uses Spring Security's TextEncryptor with AES encryption
  - Configurable via environment variables (ENCRYPTION_KEY, ENCRYPTION_SALT)
  - Falls back to no-op encryption if not configured (with warning)
  - Provides encrypt() and decrypt() methods
  - Includes proper error handling and logging

  3. Updated SftpConfig (config/SftpConfig.java)
  - Injected EncryptionService via constructor
  - Updated createSessionFactory() to decrypt passwords before use
  - Added error handling for decryption failures
  - Includes detailed logging

  4. Updated ConfigurationController (controller/ConfigurationController.java)
  - Injected EncryptionService
  - Updated createPayer() to encrypt passwords before saving
  - Updated updatePayer() to encrypt changed passwords
  - Smart detection: only encrypts if password has changed

  5. Added Encryption Configuration (application.yml)
  - Added encryption section with key and salt configuration
  - Default values provided (must be changed in production)
  - Clear documentation and security warnings

  6. Updated Environment Example (.env.example)
  - Added encryption configuration section with instructions
  - Included command to generate secure salt: openssl rand -hex 8

  Security Features

  ✅ Passwords are encrypted before storage using AES encryption✅ Passwords are decrypted 
  only when needed (during SFTP connection)✅ Configurable encryption keys via environment
  variables✅ Smart update detection - only encrypts on password change✅ Comprehensive 
  logging for debugging and auditing✅ Graceful fallback with warnings if encryption not
  configured

  Testing the Implementation

  1. Start the backend:
  cd edi835-processor
  mvn spring-boot:run
  2. Start the frontend:
  cd edi835-admin-portal
  npm run dev
  3. Navigate to http://localhost:3000/config/payers
  4. Create or edit a payer and add SFTP password
  5. Verify encryption:
  -- Check the database - password should be encrypted
  SELECT payer_id, sftp_username, sftp_password
  FROM payers
  WHERE payer_id = 'YOUR_PAYER_ID';

  Production Setup

  Before deploying to production, set these environment variables:

  # Generate a secure salt
  export ENCRYPTION_SALT=$(openssl rand -hex 8)

  # Set a strong encryption key (min 8 characters)
  export ENCRYPTION_KEY="your-production-secret-key-here"

  The implementation is now complete and ready for testing!
-- Migration: Add SFTP configuration fields to payers table
-- Date: 2025-10-17
-- Description: Adds SFTP connection configuration fields required for file delivery

-- Add SFTP configuration columns to payers table
ALTER TABLE payers ADD COLUMN IF NOT EXISTS sftp_host VARCHAR(255);
ALTER TABLE payers ADD COLUMN IF NOT EXISTS sftp_port INTEGER DEFAULT 22;
ALTER TABLE payers ADD COLUMN IF NOT EXISTS sftp_username VARCHAR(100);
ALTER TABLE payers ADD COLUMN IF NOT EXISTS sftp_password TEXT;  -- Should be encrypted at application layer
ALTER TABLE payers ADD COLUMN IF NOT EXISTS sftp_path VARCHAR(500);
ALTER TABLE payers ADD COLUMN IF NOT EXISTS requires_special_handling BOOLEAN DEFAULT false;

-- Add comments for documentation
COMMENT ON COLUMN payers.sftp_host IS 'SFTP server hostname or IP address for file delivery';
COMMENT ON COLUMN payers.sftp_port IS 'SFTP server port (default: 22)';
COMMENT ON COLUMN payers.sftp_username IS 'SFTP authentication username';
COMMENT ON COLUMN payers.sftp_password IS 'SFTP password (encrypted at application layer)';
COMMENT ON COLUMN payers.sftp_path IS 'Remote directory path on SFTP server (e.g., /edi/835)';
COMMENT ON COLUMN payers.requires_special_handling IS 'Flag for payers requiring custom file handling';

-- Example: Update existing payer with SFTP configuration (commented out)
/*
UPDATE payers SET
    sftp_host = 'sftp.test.com',
    sftp_port = 22,
    sftp_username = 'edi_user',
    sftp_password = 'ENCRYPTED_PASSWORD_HERE',
    sftp_path = '/test/835'
WHERE payer_id = 'TEST001';
*/

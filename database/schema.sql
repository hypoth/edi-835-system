-- PostgreSQL Schema for EDI 835 System
-- See artifact 'db_schema' for complete schema

CREATE TABLE IF NOT EXISTS payers (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    payer_id VARCHAR(50) UNIQUE NOT NULL,
    payer_name VARCHAR(255) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Add remaining tables from db_schema artifact

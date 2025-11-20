# Database Setup

This directory contains the **PostgreSQL production schema** for the EDI 835 system.

For **local development**, the system uses SQLite with schema located at:
`edi835-processor/src/main/resources/db/sqlite/admin-schema.sql`

## Quick Setup

### Local PostgreSQL
```bash
# Create database
createdb edi835config

# Initialize schema (all migrations are already incorporated)
psql -d edi835config -f schema.sql
```

### Docker
Automatically initialized with docker-compose.

---

## Schema Maintenance

### Important: Schema Synchronization

The PostgreSQL schema (`database/schema.sql`) **must be kept in sync** with the SQLite schema (`edi835-processor/src/main/resources/db/sqlite/admin-schema.sql`).

**When making schema changes:**

1. **Update SQLite schema** first (used for local development)
2. **Update PostgreSQL schema** in this folder (used for production)
3. **Update JPA entities** in `edi835-processor/src/main/java/com/healthcare/edi835/entity/`
4. **Test both databases** to ensure compatibility
5. **Document changes** in schema.sql header (SCHEMA MIGRATION HISTORY section)

### Key Differences Between SQLite and PostgreSQL

| Feature | PostgreSQL | SQLite |
|---------|-----------|--------|
| UUID/ID | `UUID` with `gen_random_uuid()` | `TEXT` with `lower(hex(randomblob(16)))` |
| Boolean | `BOOLEAN` | `INTEGER` with `CHECK(col IN (0, 1))` |
| Strings | `VARCHAR(n)` or `TEXT` | `TEXT` |
| Arrays | `TEXT[]` (native arrays) | `TEXT` (JSON string) |
| Decimal | `DECIMAL(15,2)` | `REAL` |

### Schema Verification

To verify schemas are in sync:

```bash
# Count tables in PostgreSQL schema
grep -c "^CREATE TABLE" database/schema.sql

# Count tables in SQLite schema
grep -c "^CREATE TABLE IF NOT EXISTS" edi835-processor/src/main/resources/db/sqlite/admin-schema.sql

# Both should return 16 (as of 2025-11-20)
```

### Migration History

- **Migration 001**: SFTP fields for payers table ✅ INCORPORATED
- **Migration 002**: NCPDP raw claims tables ✅ INCORPORATED

All migrations have been incorporated into `schema.sql`. The `migrations/` folder is kept for historical reference only.

---

## Production Deployment

See [ENABLE_POSTGRES_DB.md](../docs/guides/ENABLE_POSTGRES_DB.md) for complete PostgreSQL deployment guide.

### Quick Start for Production

```bash
# Set environment variables
export SPRING_PROFILE=postgres
export DB_HOST=your-postgres-host
export DB_PORT=5432
export DB_NAME=edi835config
export DB_USERNAME=postgres
export DB_PASSWORD=your-secure-password

# Initialize database
psql -h $DB_HOST -U $DB_USERNAME -d $DB_NAME -f database/schema.sql

# Start application
cd edi835-processor
mvn spring-boot:run
```

---

## Troubleshooting

**Schema mismatch errors on startup?**
- Ensure both SQLite and PostgreSQL schemas are synchronized
- Check JPA entity field names match database column names (snake_case in DB, camelCase in Java)
- Review schema.sql header for recent changes

**Missing tables after initialization?**
- Verify schema.sql executed without errors: `psql -d edi835config -c "\dt"`
- Check for PostgreSQL version compatibility (requires PostgreSQL 12+)

**Array field issues (approval_required_roles, override_permissions)?**
- PostgreSQL uses native `TEXT[]` arrays
- Ensure JPA entity mapping uses appropriate converter for TEXT[] ↔ List<String>

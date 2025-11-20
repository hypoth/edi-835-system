Based on my analysis of your codebase, here are the changes needed to run the backend with
  PostgreSQL:

  Summary of Current State

  ✅ Already configured:
  - PostgreSQL driver in pom.xml (edi835-processor/pom.xml:71-75)
  - PostgreSQL datasource configuration in application.yml:10-33
  - Complete database schema in database/schema.sql
  - JPA entities properly mapped for PostgreSQL
  - Hibernate configured with PostgreSQL dialect
  - PostgreSQL client installed (psql 16.10)

  ❌ Missing/needs changes:
  - Active profile points to SQLite (sqlite) instead of PostgreSQL
  - No application-postgres.yml profile file
  - PostgreSQL database not created yet
  - Database schema not initialized
  - Environment variables not set

  ---
  Required Changes

  1. Create PostgreSQL Database Profile (NEW FILE)

  Create: edi835-processor/src/main/resources/application-postgres.yml

  Should contain PostgreSQL-specific overrides:
  - Spring datasource configuration pointing to PostgreSQL
  - JPA/Hibernate settings with PostgreSQLDialect
  - Disable SQLite change feed, keep Cosmos/NCPDP change feeds configurable
  - Set ddl-auto: validate (schema managed separately)
  - Proper connection pool settings for PostgreSQL

  2. Database Setup (MANUAL STEPS)

  Execute these PostgreSQL commands:

  # Create database
  psql -U postgres -c "CREATE DATABASE edi835config;"

  # Run main schema (includes all migrations)
  psql -U postgres -d edi835config -f database/schema.sql

  Note: All migrations (001: SFTP fields, 002: NCPDP tables) are incorporated in schema.sql.
  No separate migration files need to be executed.

  3. Environment Variables

  Set these environment variables before starting the application:

  Required:
  export SPRING_PROFILE=postgres
  export DB_HOST=localhost
  export DB_PORT=5432
  export DB_NAME=edi835config
  export DB_USERNAME=postgres
  export DB_PASSWORD=<your-postgres-password>

  Optional but recommended:
  export CHANGEFEED_TYPE=cosmos  # or sqlite for testing
  export COSMOS_CHANGEFEED_ENABLED=false  # Set to true if using Cosmos DB
  export SQLITE_CHANGEFEED_ENABLED=false
  export EDI_OUTPUT_DIR=/data/edi/output
  export SFTP_ENABLED=false  # Enable when ready

  4. Application Configuration Update (application.yml:7)

  Change the default profile from sqlite to support PostgreSQL:
  # Current:
  active: ${SPRING_PROFILE:sqlite}

  # Should be:
  active: ${SPRING_PROFILE:postgres}

  Or keep default as sqlite and always set SPRING_PROFILE=postgres environment variable.

  5. Data Directory Creation

  Create necessary directories for EDI file output:
  mkdir -p /data/edi/output
  mkdir -p /data/edi/temp
  mkdir -p logs

  Or use relative paths:
  mkdir -p ./data/edi/output
  mkdir -p ./data/edi/temp

  6. Verify Entity Mappings

  Entities use UUID generation with @GenericGenerator. This works with PostgreSQL's
  gen_random_uuid() function used in schema.sql. Should be compatible, but verify that:
  - All entities use proper column names (snake_case in DB, camelCase in Java)
  - UUID fields map correctly
  - Timestamp fields use LocalDateTime consistently

  7. Connection Pool Tuning (Optional)

  Current HikariCP settings in application.yml:15-20:
  - maximum-pool-size: 10 - Good for moderate load
  - minimum-idle: 5 - Reasonable
  - May need adjustment based on actual load

  ---
  Verification Steps

  After making changes, verify the setup:

  # 1. Test database connection
  psql -U postgres -d edi835config -c "SELECT version();"

  # 2. Verify schema is created
  psql -U postgres -d edi835config -c "\dt"

  # 3. Check sample data (optional)
  psql -U postgres -d edi835config -c "SELECT count(*) FROM adjustment_code_mapping;"

  # 4. Build the application
  cd edi835-processor
  mvn clean install

  # 5. Run with PostgreSQL profile
  export SPRING_PROFILE=postgres
  export DB_PASSWORD=<your-password>
  mvn spring-boot:run

  # 6. Check health endpoint
  curl http://localhost:8080/actuator/health

  ---
  Migration from SQLite to PostgreSQL

  If you have existing data in SQLite that needs to be migrated:
  1. Export data from SQLite tables
  2. Transform data format (UUIDs, timestamps, etc.)
  3. Import into PostgreSQL using COPY or INSERT statements
  4. Verify data integrity

  ---
  Potential Issues to Watch For

  1. UUID Generation: Entities use Hibernate UUID generator; schema uses PostgreSQL
  gen_random_uuid(). Both should work, but verify on first insert.
  2. Array Fields: edi_commit_criteria table uses PostgreSQL TEXT[] arrays (lines 119, 120 in
  schema.sql). Ensure JPA mapping handles these correctly.
  3. SFTP Passwords: Stored as TEXT in payers.sftp_password (schema.sql:24). Should be
  encrypted at application layer using the encryption.key config.
  4. Change Feed Type: Currently set to sqlite. For production with PostgreSQL, you'd use
  cosmos or implement a PostgreSQL-based change feed.
  5. Hibernate Validation: With ddl-auto: validate, any mismatch between entity definitions and
   actual schema will cause startup failure.


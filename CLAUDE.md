# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## System Overview

This is a two-part system for processing healthcare claims and generating EDI 835 remittance advice files:

1. **edi835-processor**: Java Spring Boot backend that listens to Cosmos DB change feed and generates HIPAA-compliant EDI 835 files using StAEDI
2. **edi835-admin-portal**: React TypeScript frontend for configuring bucketing rules, thresholds, file naming templates, and managing approvals

**Technology Stack:**

Backend:
- Java 17+
- Spring Boot 3.x
- Azure Cosmos DB Change Feed
- StAEDI 1.28.0 (EDI processing)
- PostgreSQL (configuration/audit)
- Maven

Frontend:
- React 18 with TypeScript
- Material-UI (UI components)
- Redux Toolkit (state management)
- React Query (data fetching/caching)
- Formik & Yup (forms/validation)
- Recharts (data visualization)
- Vite (build tool)

## Common Commands

### Backend (Java)

```bash
cd edi835-processor

# Build the project
mvn clean install

# Run all tests
mvn test

# Run single test class
mvn test -Dtest=ClassName

# Run single test method
mvn test -Dtest=ClassName#methodName

# Run the application
mvn spring-boot:run

# Run with specific profile
mvn spring-boot:run -Dspring-boot.run.profiles=dev

# Package as JAR
mvn clean package

# Run the JAR
java -jar target/edi835-processor-1.0.0-SNAPSHOT.jar
```

### Frontend (React)

```bash
cd edi835-admin-portal

# Install dependencies
npm install

# Run development server (http://localhost:5173)
npm run dev

# Type check without emitting files
npm run type-check

# Lint code
npm run lint

# Build for production
npm run build

# Preview production build
npm run preview
```

### Database

```bash
# Create database
psql -U postgres -c "CREATE DATABASE edi835config;"

# Run schema
psql -U postgres -d edi835config -f database/schema.sql

# Connect to database
psql -U postgres -d edi835config
```

## Architecture

### Data Flow

```
D0 Claims Engine → Cosmos DB (claims container)
                        ↓
                  Change Feed Stream
                        ↓
    Java Change Feed Processor (ClaimChangeFeedProcessor)
                        ↓
          Claim Aggregation & Bucketing
          (ClaimAggregationService)
                        ↓
           Threshold Monitoring & Commit
           (ThresholdMonitorService)
                        ↓
        AUTO-COMMIT ←→ MANUAL-COMMIT ←→ HYBRID
                        ↓
              EDI 835 Generation
              (Edi835GeneratorService using StAEDI)
                        ↓
            File Naming & Delivery
            (FileNamingService, FileDeliveryService)
```

### Change Feed Processing

- Uses Azure Cosmos DB Change Feed Processor with partition-based parallel processing
- Lease container (`leases`) manages checkpoint state
- Configuration: `cosmos.changefeed.*` properties in application.yml
- Handler: `ChangeFeedHandler` processes claim documents and routes to `RemittanceProcessorService`
- Only processes relevant document types (filter for processed claims)
- Supports multiple concurrent buckets for parallel processing
- Handles claim adjustments and reversals automatically

### Bucketing & Aggregation

The system supports three bucketing strategies configurable via admin portal:

1. **PAYER_PAYEE** (default): Groups claims by payer/payee combination
2. **BIN_PCN**: Groups by insurance BIN/PCN numbers
3. **CUSTOM**: Uses custom grouping expressions from configuration

Active buckets track state in the `edi_file_buckets` table with status:
- `ACCUMULATING`: Actively receiving claims
- `PENDING_APPROVAL`: Waiting for manual approval
- `GENERATING`: EDI file being created
- `COMPLETED`: Successfully generated
- `FAILED`: Generation failed

### File Generation Triggers

Three commit modes control when EDI files are generated:

1. **AUTO-COMMIT**: Automatic generation when thresholds are met
   - Claim count threshold
   - Total amount threshold
   - Time-based (daily/weekly/biweekly/monthly)
   - Hybrid (first threshold triggers)

2. **MANUAL-COMMIT**: Requires admin approval via portal
   - Admin reviews pending buckets in approval queue
   - Can override thresholds
   - Schedule generation for specific time

3. **HYBRID**: Combination approach
   - Soft limits flag for approval
   - Hard limits trigger auto-generation
   - Configurable per bucketing rule

### EDI 835 Generation with StAEDI

The system uses StAEDI (Streaming API for EDI) for generating HIPAA 5010 X12 835 files:

- **Library**: io.xlate:staedi:1.28.0
- **Schema**: X12_005010_835.xml (located in `src/main/resources/edi-schemas/`)
- **Writer**: Uses `EDIStreamWriter` for memory-efficient streaming generation
- **Validation**: Real-time schema validation during generation prevents invalid files
- **Segments**: ISA/GS (envelope), BPR/TRN (payment), N1 (parties), CLP/SVC (claims/services), CAS (adjustments)

**Why StAEDI?**

StAEDI follows the same conventions as StAX (XML API) using a "pull" processing flow for EDI parsing and an emit flow for generation.

Key advantages:
1. **Streaming Performance**: Perfect for high-throughput claim processing with low memory footprint
2. **Native X12 Support**: Full support for X12, EDIFACT, and TRADACOMS standards
3. **HIPAA Compliance**: Schema-based validation including HIPAA implementation guides
4. **Bidirectional Processing**: Both reader/parser and writer/generator capabilities
5. **Active Development**: Well-maintained with regular updates

**StAEDI Usage Pattern:**

```java
// Initialize StAEDI writer
EDIOutputFactory factory = EDIOutputFactory.newFactory();
OutputStream outputStream = new FileOutputStream(fileName);
EDIStreamWriter writer = factory.createEDIStreamWriter(outputStream);

// Configure schema for validation
Schema schema = SchemaFactory.newFactory()
    .createSchema(getClass().getResourceAsStream("/edi-schemas/X12_005010_835.xml"));
writer.setSchema(schema);

// Write ISA segment
writer.startInterchange();
writer.writeStartSegment("ISA");
writer.writeElement("00"); // Authorization qualifier
writer.writeElement("          "); // Authorization info
// ... continue with ISA elements

// Write transaction set
writer.startTransaction("835");
// Write BPR, TRN, CLP, SVC segments...
writer.endTransaction();
writer.endInterchange();
```

**Error Handling:**

```java
// Validation error handling
writer.setErrorHandler(new EDIStreamValidationErrorHandler() {
    @Override
    public void error(EDIStreamEvent event, EDIStreamValidationError error) {
        log.error("Validation error at segment {}: {}",
            event.getLocation(), error.getMessage());
        // Log to claim_processing_log table
    }
});
```

Key service: `Edi835GeneratorService` orchestrates:
1. Initialize EDIOutputFactory and EDIStreamWriter
2. Load and apply X12 835 schema for validation
3. Stream-write segments (envelope → payment info → claim details → trailer)
4. Extract metadata for file naming (from ISA, N1, CLP, BPR segments)
5. Validate against HIPAA 5010 standards

**Performance Notes:**
- Streaming processes claims incrementally, ideal for large buckets
- Does not load entire EDI file in memory
- Can handle multiple concurrent file generations
- Each EDIStreamWriter instance isolated per bucket (thread-safe)

**EDI 835 File Structure:**

Generated files follow HIPAA 5010 X12 835 standard:

```
ISA*...                    (Interchange Control Header)
  GS*...                   (Functional Group Header)
    ST*835*...             (Transaction Set Header)
    BPR*...                (Financial Information)
    TRN*...                (Trace Number)
    N1*PR*...              (Payer Identification Loop)
    N1*PE*...              (Payee Identification Loop)
    CLP*...                (Claim Payment Information)
      SVC*...              (Service Line)
      CAS*...              (Adjustments)
    SE*...                 (Transaction Set Trailer)
  GE*...                   (Functional Group Trailer)
IEA*...                    (Interchange Control Trailer)
```

StAEDI handles loop structure (1000A, 1000B, 2000, 2100, etc.) automatically based on schema.

### File Naming

Dynamic file naming uses configurable templates with variables:

**Available Variables**:
- `{payerId}` - Payer identifier
- `{payeeId}` - Payee/Provider identifier
- `{bucketId}` - Bucket identifier
- `{timestamp}` - File creation timestamp
- `{date}` - Date in configured format (YYYYMMDD, YYYY-MM-DD, etc.)
- `{time}` - Time in configured format (HHmmss, HH-mm-ss, etc.)
- `{sequenceNumber}` - Auto-incrementing sequence
- `{binNumber}` - Insurance BIN (if applicable)
- `{pcnNumber}` - Insurance PCN (if applicable)
- `{claimCount}` - Number of claims in file
- `{totalAmount}` - Total claim amount (formatted)
- `{fileType}` - Fixed identifier (e.g., "835", "RMT")
- `{environment}` - Environment identifier (PROD, TEST, UAT)

**Template Examples**:
- `{payerId}_{payeeId}_{timestamp}.835`
- `EDI835_{bucketId}_{date}_{sequenceNumber}.txt`
- `RMT_{payerId}_{date}_{time}_{claimCount}.x12`
- `{payerId}_{binNumber}_{date}_SEQ{sequenceNumber}.edi`
- `{environment}_{payerId}_{payeeId}_{timestamp}.835`

**Format Options**:
- Date format: YYYYMMDD, YYYY-MM-DD, MMDDYYYY, etc.
- Time format: HHmmss, HH-mm-ss, HHmmssSSS (with milliseconds)
- Number padding: Sequence with leading zeros (e.g., 001, 0001)
- Case conversion: UPPER, lower, CamelCase
- Custom separators: underscore, hyphen, dot, none

Managed by `FileNamingService` with:
- Template syntax validation
- Variable availability check
- Preview generation with sample data
- Unique filename guarantee (sequence increment on collision)
- File system compatibility check (invalid character removal)

Configuration stored in `edi_file_naming_templates` table, linked to bucketing rules.

### File Delivery

The `FileDeliveryService` handles delivery of generated EDI 835 files:

**Protocols Supported**:
- SFTP: Secure File Transfer Protocol
- AS2: Applicability Statement 2 (for trading partners)

**Features**:
- File encryption (if required by trading partner)
- Transmission acknowledgment tracking
- Automatic retry mechanism with configurable backoff
- Delivery status tracking in `file_generation_history` table
- Multiple delivery attempt logging

**Configuration** (in application.yml):
```yaml
file-delivery:
  sftp:
    enabled: true
    host: sftp.partner.com
    port: 22
    username: edi_user
    remote-directory: /edi/835
  retry:
    max-attempts: 3
    backoff-ms: 5000
```

**Delivery States**:
- PENDING: File generated, awaiting delivery
- DELIVERED: Successfully transmitted
- FAILED: Delivery failed after max retries
- RETRY: Currently retrying delivery

### Azure Cosmos DB

**Containers**:
- `claims`: Stores claim documents from D0 claims engine
  - Partition key: `/payerId` (for efficient queries by payer)
  - Contains processed claims ready for EDI generation
- `leases`: Change Feed lease management
  - Partition key: `/id`
  - Tracks change feed processor state and checkpoints

**Change Feed Configuration**:
- Container: `claims`
- Lease container: `leases`
- Host name: Instance identifier (e.g., `edi835-processor-01`)
- Max items per batch: 100 (configurable via `max-items-count`)
- Poll interval: 5000ms (configurable via `poll-interval-ms`)

**Partition Strategy**:
- Claims container uses `/payerId` for balanced distribution
- Change feed processor automatically distributes partitions across instances
- Enables horizontal scaling by adding more processor instances

### Configuration Database (PostgreSQL)

Core configuration tables:
- `payers`: Payer configurations (ISA/GS identifiers)
- `payees`: Provider/payee configurations
- `insurance_plans`: BIN/PCN reference data
- `edi_bucketing_rules`: Claim grouping strategies (PAYER_PAYEE, BIN_PCN, CUSTOM)
- `edi_generation_thresholds`: File generation criteria (CLAIM_COUNT, AMOUNT, TIME, HYBRID)
- `edi_commit_criteria`: AUTO/MANUAL/HYBRID mode configuration with approval roles
- `edi_file_naming_templates`: File naming patterns with format options
- `file_naming_sequence`: Sequence number tracking with reset frequency
- `payment_methods`: EFT/Check payment configurations
- `adjustment_code_mapping`: CARC/RARC mappings

Operational tables:
- `edi_file_buckets`: Active accumulations with real-time state (ACCUMULATING, PENDING_APPROVAL, GENERATING, COMPLETED, FAILED)
- `claim_processing_log`: Audit trail for claims with rejection tracking
- `file_generation_history`: Generated files tracking with delivery status
- `bucket_approval_log`: Approval workflow audit with scheduled generation times

### REST API Structure

Backend exposes REST APIs at `/api/v1/*`:

**Configuration Endpoints**:
- `/bucketing-rules`: Manage bucketing strategies
- `/generation-thresholds`: Configure thresholds
- `/commit-criteria`: Setup AUTO/MANUAL/HYBRID modes
- `/file-naming-templates`: Manage templates with preview/validation
- `/payers`, `/payees`: Payer/payee management

**Operations Endpoints**:
- `/dashboard/summary`: Executive summary stats
- `/dashboard/active-buckets`: Live bucket monitoring
- `/dashboard/rejections`: Rejection analytics
- `/dashboard/pending-approvals`: Approval queue
- `/files/buckets/pending-approval`: Buckets awaiting approval
- `/files/buckets/{id}/approve`: Approve bucket
- `/files/buckets/{id}/reject`: Reject bucket
- `/files/buckets/{id}/generate`: Force generation

Frontend services (in `src/services/`) call these APIs using Axios with React Query for caching.

## Project Structure

### Backend Service Layer

Expected services in `src/main/java/com/healthcare/edi835/service/`:

- `RemittanceProcessorService`: Main orchestrator for claim processing
- `ClaimAggregationService`: Groups claims into buckets based on rules
- `BucketManagerService`: Manages bucket lifecycle and state transitions
- `ThresholdMonitorService`: Monitors thresholds and triggers generation
- `CommitCriteriaService`: Evaluates AUTO/MANUAL/HYBRID commit logic
- `ApprovalWorkflowService`: Handles approval queue and workflow
- `Edi835GeneratorService`: Generates EDI 835 files using StAEDI
- `FileNamingService`: Parses templates and generates file names
- `ConfigurationService`: Fetches configuration from PostgreSQL
- `FileDeliveryService`: Delivers files via SFTP/AS2

Utilities in `src/main/java/com/healthcare/edi835/util/`:
- `EdiValidator`: EDI data validation
- `SegmentBuilder`: Helper for building EDI segments
- `StaediSchemaLoader`: Loads and caches EDI schemas
- `FileNameTemplateParser`: Parses and evaluates file naming templates

### Frontend Component Structure

Expected components in `edi835-admin-portal/src/components/`:

**Dashboard** (`dashboard/`):
- `OperationsDashboard.tsx`: Executive summary for Operations Manager
  - Real-time active EDI accumulations overview
  - Total number of pending files
  - Claims count per bucket (live updates)
  - Aggregated claim amounts by bucket
  - Rejection/denial counts and percentages
  - Files pending manual approval count
  - Recent file generation activity timeline
  - Alert notifications for thresholds approaching
- `SummaryCards.tsx`: KPI cards with metrics
- `ActiveBucketsWidget.tsx`: Live bucket monitoring with status
- `ClaimMetricsChart.tsx`: Visual analytics with Recharts (throughput, amounts over time)
- `RejectionAnalytics.tsx`: Rejection/denial tracking and trends
- `PendingApprovalsAlert.tsx`: Alert widget highlighting approval queue

**Configuration** (`bucketing/`, `thresholds/`, `commitcriteria/`, `filenaming/`):
- Bucketing: `BucketingRulesList.tsx`, `BucketingRuleForm.tsx`, `RulePreview.tsx`
- Thresholds: `ThresholdsList.tsx`, `ThresholdForm.tsx`, individual threshold type components
- Commit Criteria: `AutoCommitConfig.tsx`, `ManualApprovalConfig.tsx`, `HybridCommitConfig.tsx`
- File Naming: `TemplateBuilder.tsx`, `VariableSelector.tsx`, `TemplatePreview.tsx`, `TemplateValidator.tsx`, `FormatOptions.tsx`

**Payers & Payments** (`payers/`, `payments/`):
- `PayerList.tsx`, `PayerForm.tsx`, `PayerDetails.tsx`
- `PaymentMethods.tsx`, `BankingInfo.tsx`

**Monitoring** (`monitoring/`):
- `DetailedMonitoring.tsx`: Comprehensive monitoring view
- `ActiveBuckets.tsx`: Real-time bucket states
- `FileHistory.tsx`: Historical file generation data
- `ErrorLogs.tsx`: Error and exception tracking

**Approvals** (`approvals/`):
- `ApprovalQueue.tsx`: List of pending buckets
- `BucketApprovalCard.tsx`: Individual bucket approval card
- `BucketDetailsModal.tsx`: Detailed bucket information
- `ApprovalHistory.tsx`: Audit trail of approvals/rejections

Service layer in `src/services/`:
- Each domain has a dedicated service (payerService.ts, bucketingService.ts, etc.)
- `api.ts`: Axios instance with interceptors for auth and error handling

Redux slices in `src/store/`:
- One slice per domain for state management
- `store.ts`: Configures Redux store with middleware

## Key Design Patterns

### Backend Patterns

1. **Change Feed Listener Pattern**: `ChangeFeedHandler` processes Cosmos DB changes asynchronously with checkpointing
2. **Service Layer Architecture**: Business logic separated into focused services
   - `RemittanceProcessorService`: Orchestrates the entire flow
   - `ClaimAggregationService`: Handles bucketing logic
   - `BucketManagerService`: Manages bucket state machine
   - `ThresholdMonitorService`: Monitors and evaluates thresholds
   - `CommitCriteriaService`: Determines commit mode behavior
   - `ApprovalWorkflowService`: Manages manual approval flow
3. **Strategy Pattern**: Bucketing strategies (PAYER_PAYEE, BIN_PCN, CUSTOM) selected at runtime
4. **Template Method Pattern**: File naming template parsing and variable substitution in `FileNamingService`
5. **Repository Pattern**: Spring Data JPA for PostgreSQL, Azure Cosmos SDK for Cosmos DB
6. **State Machine Pattern**: Bucket status transitions (ACCUMULATING → PENDING_APPROVAL → GENERATING → COMPLETED)

### Frontend Patterns

1. **Component Hierarchy**: Common components in `src/components/common/`, feature-specific in subdirectories
2. **State Management**: Redux Toolkit with slices for each domain (payers, bucketing, thresholds, approvals, dashboard)
3. **API Integration**: Service layer (`src/services/`) abstracts API calls
4. **Form Handling**: Formik + Yup for forms and validation
5. **Data Fetching**: React Query for caching and optimistic updates

## Environment Configuration

### Backend Environment Variables

Required:
- `COSMOS_ENDPOINT`: Azure Cosmos DB endpoint
- `COSMOS_KEY`: Cosmos DB access key
- `DB_HOST`, `DB_PORT`, `DB_NAME`: PostgreSQL connection
- `DB_USERNAME`, `DB_PASSWORD`: Database credentials

Optional:
- `EDI_OUTPUT_DIR`: Output directory for EDI files (default: `/data/edi/output`)
- `SFTP_ENABLED`, `SFTP_HOST`, `SFTP_PORT`: File delivery configuration
- `SERVER_PORT`: API server port (default: 8080)

### Frontend Environment Variables

Create `.env` in `edi835-admin-portal/`:
```
VITE_API_BASE_URL=http://localhost:8080/api/v1
```

## Testing Approach

### Backend Tests

- Unit tests for services using Mockito
- Integration tests with H2 in-memory database (test scope)
- Change feed processor tests mock Cosmos DB interactions
- EDI generation tests validate against StAEDI schema

### Frontend Tests

- Component tests (setup with Vite + React Testing Library)
- Service layer mocking for API calls
- Redux slice tests for state management

## Important Implementation Notes

### StAEDI Integration

- **Never load entire EDI file in memory**: Use streaming with `EDIStreamWriter`
- **Always set schema**: Call `writer.setSchema(schema)` for validation
- **Error handler required**: Implement `EDIStreamValidationErrorHandler` to log validation errors to `claim_processing_log`
- **Thread safety**: Each bucket generation needs isolated `EDIStreamWriter` instance
- **Schema location**: `src/main/resources/edi-schemas/X12_005010_835.xml`

### Change Feed Processing

- **Lease management critical**: Ensure `leases` container exists in Cosmos DB with `/id` partition key
- **Partition strategy**: Change feed processor automatically distributes partitions across instances
- **Checkpoint frequency**: Balance between performance and reliability (configured via `poll-interval-ms`)
- **Error handling**: Failed claim processing should log to `claim_processing_log` without stopping change feed

### File Naming Sequences

- **Collision handling**: `FileNamingService` must increment sequence on collision
- **Reset logic**: Sequences reset based on `reset_frequency` in `file_naming_sequence` table
- **Thread safety**: Use database-level locking when incrementing sequences

### Approval Workflow

- **Status transitions**: ACCUMULATING → PENDING_APPROVAL → GENERATING → COMPLETED
- **Audit trail**: All approvals/rejections logged to `bucket_approval_log`
- **Role-based**: Check `approval_required_roles` before allowing approval
- **Scheduled generation**: Support `scheduled_generation_time` for delayed generation

## Authentication & Security

- Authentication handled by external ecosystem auth provider
- API expects auth tokens in request headers
- Role-based access control via token claims
- No internal authentication mechanism in this system
- Sensitive data (banking info) encrypted in `payment_methods.account_number_encrypted`

## Health Checks & Monitoring

Available at `/actuator/*`:
- `/actuator/health`: Overall health status
- `/actuator/metrics`: JVM and application metrics
- `/actuator/prometheus`: Prometheus-formatted metrics

Logging:
- Console: Simple format for container logs
- File: `logs/edi835-processor.log` (max 10MB, 30 day retention)
- Level: DEBUG for `com.healthcare.edi835`, INFO for dependencies

## Troubleshooting

### Change Feed Not Processing

1. Verify Cosmos DB connection: Check `COSMOS_ENDPOINT` and `COSMOS_KEY`
2. Ensure lease container exists: Container named `leases` with partition key `/id`
3. Check logs: Look for connection errors or lease acquisition failures
4. Verify partition key: Claims container should use `/payerId` as partition key

### EDI Generation Fails

1. Check StAEDI schema: Ensure `X12_005010_835.xml` exists in classpath
2. Validate claim data: Missing required fields cause validation errors
3. Review validation errors: Check `EDIStreamValidationErrorHandler` logs
4. Output directory: Verify `EDI_OUTPUT_DIR` exists and is writable

### Frontend Can't Connect to Backend

1. Check `VITE_API_BASE_URL` in `.env`
2. Verify backend is running: `curl http://localhost:8080/api/v1/actuator/health`
3. CORS configuration: Backend should allow frontend origin
4. Network connectivity: Ensure no firewall blocking port 8080

## Deployment

### Backend (Docker)

```dockerfile
FROM openjdk:17-jdk-slim
COPY target/edi835-processor-1.0.0-SNAPSHOT.jar app.jar
ENTRYPOINT ["java","-jar","/app.jar"]
```

Build and run:
```bash
mvn clean package
docker build -t edi835-processor .
docker run -p 8080:8080 -e COSMOS_ENDPOINT=... -e DB_HOST=... edi835-processor
```

### Frontend (Nginx)

```dockerfile
FROM node:18 AS build
WORKDIR /app
COPY package*.json ./
RUN npm install
COPY . .
RUN npm run build

FROM nginx:alpine
COPY --from=build /app/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/nginx.conf
```

### Azure Deployment

**Backend (Java Service)**:
- Package as Spring Boot JAR
- Deploy to Azure App Service or AKS (Azure Kubernetes Service)
- Configure Cosmos DB connection with proper throughput (RU/s) for change feed
- Set up PostgreSQL connection pool for configuration data
- Configure Change Feed processor with appropriate lease settings
- Enable health checks & metrics (Azure Monitor)
- Set up retry policies for transient failures

**Cosmos DB Setup**:
1. Create Cosmos DB account in Azure Portal
2. Create database: `claims`
3. Create container: `claims` with partition key `/payerId`
4. Create container: `leases` with partition key `/id`
5. Provision sufficient RU/s for change feed processing (consider burst capacity)

**Frontend (React Portal)**:
- Build with `npm run build`
- Deploy to Azure Static Web Apps, Azure App Service, or Nginx/Apache
- Serve static files from `/dist` directory
- Configure reverse proxy or CORS for backend API access
- Enable HTTPS/TLS

**Security**:
- Authentication handled by ecosystem (external auth provider)
- Role-based access control (RBAC) via headers/tokens from auth provider
- API rate limiting
- Comprehensive audit logging
- Encrypted database fields for sensitive data (banking info in `payment_methods.account_number_encrypted`)
- HTTPS/TLS for all communications

**Monitoring**:
- Azure Monitor for application insights
- Prometheus metrics endpoint at `/actuator/prometheus`
- Cosmos DB metrics (RU consumption, throttling)
- Application logs to Azure Log Analytics

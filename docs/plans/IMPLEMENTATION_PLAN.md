# EDI 835 System - Implementation Plan

## Overview

This document provides a comprehensive implementation plan for the EDI 835 Remittance Processing System. The implementation follows the architecture defined in CLAUDE.md.

## Implementation Status

### ✅ Completed
- [x] Project structure (pom.xml, package.json)
- [x] Database schema (schema.sql)
- [x] Configuration classes (CosmosDbConfig, ChangeFeedConfig, RestClientConfig, SchedulerConfig)
- [x] ChangeFeedHandler for processing Cosmos DB changes
- [x] Claim domain model

### 🚧 In Progress
- [ ] Domain models and JPA entities
- [ ] Repository interfaces
- [ ] Service layer implementation
- [ ] REST controllers

### ⏳ Pending
- [ ] EDI 835 Generator with StAEDI
- [ ] File Naming Service
- [ ] File Delivery Service
- [ ] Frontend React application
- [ ] Integration tests
- [ ] Deployment configurations

## Phase 1: Core Backend (Java Spring Boot)

### 1.1 Domain Models (`src/main/java/com/healthcare/edi835/model/`)

**Already Complete:**
- ✅ `Claim.java` - Cosmos DB claim document

**To Create:**
```
model/
├── RemittanceAdvice.java      # Complete remittance data structure
├── PaymentInfo.java           # BPR segment data
├── EdiSegment.java            # Generic EDI segment holder
└── dto/                       # Data Transfer Objects
    ├── BucketSummaryDTO.java
    ├── DashboardSummaryDTO.java
    ├── ApprovalRequestDTO.java
    └── FileGenerationRequestDTO.java
```

### 1.2 JPA Entities (`src/main/java/com/healthcare/edi835/entity/`)

Based on database/schema.sql, create JPA entities:

```
entity/
├── Payer.java
├── Payee.java
├── InsurancePlan.java
├── EdiBucketingRule.java
├── EdiGenerationThreshold.java
├── EdiCommitCriteria.java
├── EdiFileNamingTemplate.java
├── FileNamingSequence.java
├── PaymentMethod.java
├── AdjustmentCodeMapping.java
├── EdiFileBucket.java
├── BucketApprovalLog.java
├── ClaimProcessingLog.java
└── FileGenerationHistory.java
```

**Key entity relationships:**
- EdiBucketingRule → EdiGenerationThreshold (One-to-Many)
- EdiBucketingRule → EdiCommitCriteria (One-to-Many)
- EdiBucketingRule → EdiFileNamingTemplate (One-to-Many)
- EdiFileBucket → EdiFileNamingTemplate (Many-to-One)
- Payer → InsurancePlan (One-to-Many)

### 1.3 Repository Interfaces (`src/main/java/com/healthcare/edi835/repository/`)

**JPA Repositories (PostgreSQL):**
```
repository/
├── PayerRepository.java
├── PayeeRepository.java
├── InsurancePlanRepository.java
├── EdiBucketingRuleRepository.java
├── EdiGenerationThresholdRepository.java
├── EdiCommitCriteriaRepository.java
├── EdiFileNamingTemplateRepository.java
├── FileNamingSequenceRepository.java
├── PaymentMethodRepository.java
├── AdjustmentCodeMappingRepository.java
├── EdiFileBucketRepository.java
├── BucketApprovalLogRepository.java
├── ClaimProcessingLogRepository.java
└── FileGenerationHistoryRepository.java
```

**Cosmos DB Repository:**
```
repository/cosmos/
└── ClaimRepository.java
```

**Custom Query Methods:**
- `EdiFileBucketRepository`: findByStatus, findPendingApprovals
- `EdiBucketingRuleRepository`: findActiveRules, findByPriority
- `FileNamingSequenceRepository`: findByTemplateIdAndPayerId
- `FileGenerationHistoryRepository`: findRecentHistory, findByDeliveryStatus

### 1.4 Service Layer (`src/main/java/com/healthcare/edi835/service/`)

**Core Services:**
```
service/
├── RemittanceProcessorService.java       # Main orchestrator
├── ClaimAggregationService.java          # Bucketing logic
├── BucketManagerService.java             # Bucket lifecycle
├── ThresholdMonitorService.java          # Threshold evaluation
├── CommitCriteriaService.java            # AUTO/MANUAL/HYBRID logic
├── ApprovalWorkflowService.java          # Approval queue management
├── Edi835GeneratorService.java           # StAEDI integration
├── FileNamingService.java                # Template parsing
├── ConfigurationService.java             # Config retrieval
└── FileDeliveryService.java              # SFTP/AS2 delivery
```

**Service Implementation Notes:**

**RemittanceProcessorService:**
- Receives claims from ChangeFeedHandler
- Routes to ClaimAggregationService
- Orchestrates entire flow

**ClaimAggregationService:**
- Implements bucketing strategies (PAYER_PAYEE, BIN_PCN, CUSTOM)
- Creates/updates EdiFileBucket records
- Maintains bucket state

**BucketManagerService:**
- Manages bucket state transitions
- ACCUMULATING → PENDING_APPROVAL → GENERATING → COMPLETED
- Handles concurrent bucket operations

**ThresholdMonitorService:**
- Scheduled task (runs every minute)
- Evaluates CLAIM_COUNT, AMOUNT, TIME, HYBRID thresholds
- Triggers generation or approval flow

**CommitCriteriaService:**
- Evaluates commit mode (AUTO/MANUAL/HYBRID)
- Determines if approval required
- Checks approval roles

**ApprovalWorkflowService:**
- Manages approval queue
- Approve/reject bucket operations
- Logs to BucketApprovalLog

**Edi835GeneratorService:**
- Initializes StAEDI EDIStreamWriter
- Generates ISA/GS/ST/BPR/TRN/N1/CLP/SVC/CAS segments
- Validates against X12_005010_835.xml schema
- Returns generated file path

**FileNamingService:**
- Parses templates like `{payerId}_{date}_{sequenceNumber}.835`
- Substitutes variables
- Manages sequence numbers
- Handles collision detection

**FileDeliveryService:**
- SFTP delivery implementation
- Retry logic with exponential backoff
- Updates FileGenerationHistory delivery status

### 1.5 Utility Classes (`src/main/java/com/healthcare/edi835/util/`)

```
util/
├── EdiValidator.java             # EDI data validation
├── SegmentBuilder.java           # Helper for building segments
├── StaediSchemaLoader.java       # Loads/caches schemas
└── FileNameTemplateParser.java   # Template parsing logic
```

### 1.6 REST Controllers (`src/main/java/com/healthcare/edi835/controller/`)

```
controller/
├── PayerController.java                    # /api/v1/payers
├── PayeeController.java                    # /api/v1/payees
├── BucketingRuleController.java            # /api/v1/bucketing-rules
├── GenerationThresholdController.java      # /api/v1/generation-thresholds
├── CommitCriteriaController.java           # /api/v1/commit-criteria
├── FileNamingTemplateController.java       # /api/v1/file-naming-templates
├── PaymentMethodController.java            # /api/v1/payment-methods
├── FileBucketController.java               # /api/v1/files/buckets
├── DashboardController.java                # /api/v1/dashboard
└── MonitoringController.java               # /api/v1/monitoring
```

**Key Endpoints:**

**BucketingRuleController:**
- GET /api/v1/bucketing-rules
- POST /api/v1/bucketing-rules
- PUT /api/v1/bucketing-rules/{id}
- DELETE /api/v1/bucketing-rules/{id}

**FileNamingTemplateController:**
- GET /api/v1/file-naming-templates
- POST /api/v1/file-naming-templates
- POST /api/v1/file-naming-templates/{id}/preview
- GET /api/v1/file-naming-templates/variables
- POST /api/v1/file-naming-templates/validate

**FileBucketController:**
- GET /api/v1/files/buckets/active
- GET /api/v1/files/buckets/pending-approval
- POST /api/v1/files/buckets/{id}/approve
- POST /api/v1/files/buckets/{id}/reject
- POST /api/v1/files/buckets/{id}/generate

**DashboardController:**
- GET /api/v1/dashboard/summary
- GET /api/v1/dashboard/active-buckets
- GET /api/v1/dashboard/rejections
- GET /api/v1/dashboard/pending-approvals

## Phase 2: Frontend (React TypeScript)

### 2.1 Project Setup

```bash
cd edi835-admin-portal
npm install
```

### 2.2 Component Structure

```
src/
├── components/
│   ├── common/
│   │   ├── Header.tsx
│   │   ├── Sidebar.tsx
│   │   ├── Table.tsx
│   │   ├── LoadingSpinner.tsx
│   │   └── ErrorBoundary.tsx
│   ├── dashboard/
│   │   ├── OperationsDashboard.tsx
│   │   ├── SummaryCards.tsx
│   │   ├── ActiveBucketsWidget.tsx
│   │   ├── ClaimMetricsChart.tsx
│   │   ├── RejectionAnalytics.tsx
│   │   └── PendingApprovalsAlert.tsx
│   ├── payers/
│   │   ├── PayerList.tsx
│   │   ├── PayerForm.tsx
│   │   └── PayerDetails.tsx
│   ├── bucketing/
│   │   ├── BucketingRulesList.tsx
│   │   ├── BucketingRuleForm.tsx
│   │   └── RulePreview.tsx
│   ├── thresholds/
│   │   ├── ThresholdsList.tsx
│   │   ├── ThresholdForm.tsx
│   │   ├── ClaimCountThreshold.tsx
│   │   ├── AmountThreshold.tsx
│   │   └── TimeBasedThreshold.tsx
│   ├── commitcriteria/
│   │   ├── CommitCriteriaList.tsx
│   │   ├── CommitCriteriaForm.tsx
│   │   ├── AutoCommitConfig.tsx
│   │   ├── ManualApprovalConfig.tsx
│   │   └── HybridCommitConfig.tsx
│   ├── filenaming/
│   │   ├── FileNamingTemplateList.tsx
│   │   ├── TemplateBuilder.tsx
│   │   ├── VariableSelector.tsx
│   │   ├── TemplatePreview.tsx
│   │   ├── FormatOptions.tsx
│   │   └── TemplateValidator.tsx
│   ├── approvals/
│   │   ├── ApprovalQueue.tsx
│   │   ├── BucketApprovalCard.tsx
│   │   ├── BucketDetailsModal.tsx
│   │   └── ApprovalHistory.tsx
│   ├── payments/
│   │   ├── PaymentMethods.tsx
│   │   └── BankingInfo.tsx
│   └── monitoring/
│       ├── DetailedMonitoring.tsx
│       ├── ActiveBuckets.tsx
│       ├── FileHistory.tsx
│       └── ErrorLogs.tsx
├── services/
│   ├── api.ts
│   ├── payerService.ts
│   ├── bucketingService.ts
│   ├── thresholdService.ts
│   ├── commitCriteriaService.ts
│   ├── fileNamingService.ts
│   ├── approvalService.ts
│   ├── dashboardService.ts
│   └── configService.ts
├── store/
│   ├── payerSlice.ts
│   ├── bucketingSlice.ts
│   ├── thresholdSlice.ts
│   ├── commitCriteriaSlice.ts
│   ├── fileNamingSlice.ts
│   ├── approvalSlice.ts
│   ├── dashboardSlice.ts
│   └── store.ts
├── types/
│   ├── Payer.ts
│   ├── BucketingRule.ts
│   ├── Threshold.ts
│   ├── CommitCriteria.ts
│   ├── FileNamingTemplate.ts
│   ├── Approval.ts
│   ├── Dashboard.ts
│   └── Payment.ts
├── utils/
│   ├── validators.ts
│   └── formatters.ts
├── App.tsx
└── main.tsx
```

### 2.3 Key Frontend Features

**Dashboard (Operations Manager View):**
- Real-time active bucket count
- Pending files count
- Rejection rate analytics
- Approval queue alerts
- Recent activity timeline
- Threshold approaching alerts

**Template Builder:**
- Drag-and-drop variable insertion
- Real-time preview with sample data
- Syntax validation
- Format options (date/time formats, padding, case)
- Template library (save/load templates)

**Approval Queue:**
- List of pending buckets
- Bucket details (claims, amount, threshold status)
- Approve/Reject buttons
- Schedule generation for later
- Approval history

## Phase 3: Testing & Deployment

### 3.1 Backend Tests

```
src/test/java/com/healthcare/edi835/
├── service/
│   ├── ClaimAggregationServiceTest.java
│   ├── ThresholdMonitorServiceTest.java
│   ├── Edi835GeneratorServiceTest.java
│   └── FileNamingServiceTest.java
├── changefeed/
│   └── ChangeFeedHandlerTest.java
└── integration/
    ├── BucketingIntegrationTest.java
    └── Edi835GenerationIntegrationTest.java
```

### 3.2 Frontend Tests

```
src/__tests__/
├── components/
│   ├── OperationsDashboard.test.tsx
│   ├── TemplateBuilder.test.tsx
│   └── ApprovalQueue.test.tsx
└── services/
    └── bucketingService.test.ts
```

### 3.3 Deployment

**Docker:**
- Backend: Dockerfile for Java app
- Frontend: Multi-stage build (Node → Nginx)

**Azure:**
- Cosmos DB provisioning
- PostgreSQL setup
- App Service / AKS deployment
- Environment variables configuration

## Implementation Priority

1. **High Priority (P0)** - Core functionality
   - JPA Entities (all 14 tables)
   - Repository interfaces
   - RemittanceProcessorService
   - ClaimAggregationService
   - BucketManagerService
   - Edi835GeneratorService
   - FileNamingService

2. **Medium Priority (P1)** - Essential features
   - ThresholdMonitorService
   - CommitCriteriaService
   - ApprovalWorkflowService
   - REST Controllers
   - Dashboard components
   - Approval queue components

3. **Lower Priority (P2)** - Supporting features
   - FileDeliveryService (SFTP)
   - Monitoring components
   - Advanced template builder features
   - Comprehensive test suite

## Next Steps

1. Create all JPA entities based on schema.sql
2. Create repository interfaces with custom queries
3. Implement core service layer (RemittanceProcessor, ClaimAggregation, BucketManager)
4. Implement Edi835GeneratorService with StAEDI
5. Implement FileNamingService
6. Create REST controllers for configuration
7. Build frontend dashboard
8. Implement approval workflow UI
9. Integration testing
10. Deployment

## Notes

- All configuration classes are complete and well-documented
- ChangeFeedHandler is implemented and ready
- Database schema is comprehensive and includes all necessary tables
- StAEDI library (1.28.0) is already in pom.xml
- Frontend dependencies are configured in package.json

## References

- See CLAUDE.md for detailed architecture
- See architecture.txt for original design
- See database/schema.sql for complete schema
- See README.md for setup instructions

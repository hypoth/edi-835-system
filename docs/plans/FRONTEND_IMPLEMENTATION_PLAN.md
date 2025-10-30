# EDI 835 Admin Portal - Frontend Implementation Plan

**Status:** In Progress
**Tech Stack:** React 18 + TypeScript + Material-UI + React Query + Vite

---

## 📁 Current Status

### ✅ Already Created
- `package.json` - All dependencies configured
- `tsconfig.json` - TypeScript configuration
- `vite.config.ts` - Vite build configuration
- Basic directory structure (`src/components`, `src/services`, `src/types`)
- Basic `dashboardService.ts` and `api.ts`

### ✅ Just Created
- `App.tsx` - Main app with routing and theme
- `MainLayout.tsx` - Navigation drawer and app bar
- `apiClient.ts` - Axios interceptors with error handling

---

## 🎯 Components to Implement

### 1. **Core Pages (High Priority)**

#### Dashboard Page (`src/pages/Dashboard.tsx`)
**Purpose:** Main landing page with real-time metrics

**Components Needed:**
- `MetricsCard.tsx` - Display individual metrics (buckets, files, claims)
- `StatusChart.tsx` - Pie/bar charts for status distribution
- `RecentActivityList.tsx` - Recent buckets/files
- `AlertsPanel.tsx` - System alerts and warnings
- `QuickActions.tsx` - Quick action buttons

**API Endpoints:**
- `GET /api/v1/dashboard/summary`
- `GET /api/v1/dashboard/buckets/metrics`
- `GET /api/v1/dashboard/files/metrics`
- `GET /api/v1/dashboard/claims/metrics`
- `GET /api/v1/dashboard/health`
- `GET /api/v1/dashboard/alerts`

---

#### Bucket List Page (`src/pages/buckets/BucketList.tsx`)
**Purpose:** View, search, and filter all buckets

**Components Needed:**
- `BucketTable.tsx` - Data table with sorting/filtering
- `BucketFilters.tsx` - Status, payer, payee filters
- `BucketStatusChip.tsx` - Colored status badges
- `BucketActions.tsx` - Action buttons (evaluate, transition)

**API Endpoints:**
- `GET /api/v1/buckets`
- `GET /api/v1/buckets/search`
- `POST /api/v1/buckets/{id}/evaluate-thresholds`

---

#### Bucket Details Page (`src/pages/buckets/BucketDetails.tsx`)
**Purpose:** Detailed view of single bucket

**Components Needed:**
- `BucketInfo.tsx` - Basic info (payer, payee, counts, amounts)
- `BucketTimeline.tsx` - State transition history
- `ClaimList.tsx` - Claims in this bucket
- `BucketActions.tsx` - State transition buttons

**API Endpoints:**
- `GET /api/v1/buckets/{bucketId}`
- `GET /api/v1/buckets/{bucketId}/details`
- `POST /api/v1/buckets/{bucketId}/transition-to-generation`
- `POST /api/v1/buckets/{bucketId}/transition-to-approval`

---

#### Approval Queue Page (`src/pages/approvals/ApprovalQueue.tsx`)
**Purpose:** Manage pending approvals

**Components Needed:**
- `ApprovalTable.tsx` - Pending buckets table
- `ApprovalDialog.tsx` - Approve/reject modal
- `ApprovalHistory.tsx` - Past approvals
- `BulkApprovalActions.tsx` - Bulk operations

**API Endpoints:**
- `GET /api/v1/approvals/pending`
- `POST /api/v1/approvals/approve/{bucketId}`
- `POST /api/v1/approvals/reject/{bucketId}`
- `POST /api/v1/approvals/approve/bulk`
- `GET /api/v1/approvals/history/{bucketId}`

---

#### File List Page (`src/pages/files/FileList.tsx`)
**Purpose:** View generated EDI files

**Components Needed:**
- `FileTable.tsx` - Files with download links
- `FileFilters.tsx` - Status, payer, date filters
- `FilePreviewDialog.tsx` - Preview EDI content
- `FileDownloadButton.tsx` - Download file

**API Endpoints:**
- `GET /api/v1/files/history`
- `GET /api/v1/files/download/{fileId}`
- `GET /api/v1/files/preview/{fileId}`
- `GET /api/v1/files/search`

---

#### Delivery Tracking Page (`src/pages/delivery/DeliveryTracking.tsx`)
**Purpose:** Monitor SFTP delivery status

**Components Needed:**
- `DeliveryTable.tsx` - Files with delivery status
- `DeliveryRetryButton.tsx` - Retry failed deliveries
- `DeliveryQueuePanel.tsx` - Pending delivery queue
- `DeliveryErrorsPanel.tsx` - Error messages

**API Endpoints:**
- `GET /api/v1/delivery/pending`
- `GET /api/v1/delivery/failed`
- `POST /api/v1/delivery/retry/{fileId}`
- `POST /api/v1/delivery/retry-all-failed`
- `GET /api/v1/delivery/errors`

---

### 2. **Configuration Pages (Medium Priority)**

#### Payer List (`src/pages/configuration/PayerList.tsx`)
**Components:** PayerTable, PayerForm, SftpConfigForm

**API Endpoints:**
- `GET /api/v1/config/payers`
- `POST /api/v1/config/payers`
- `PUT /api/v1/config/payers/{id}`
- `DELETE /api/v1/config/payers/{id}`

---

#### Payee List (`src/pages/configuration/PayeeList.tsx`)
**Components:** PayeeTable, PayeeForm

**API Endpoints:**
- `GET /api/v1/config/payees`
- `POST /api/v1/config/payees`
- `PUT /api/v1/config/payees/{id}`

---

#### Bucketing Rules (`src/pages/configuration/BucketingRuleList.tsx`)
**Components:** RuleTable, RuleForm, RuleTypeSelector

**API Endpoints:**
- `GET /api/v1/config/rules`
- `POST /api/v1/config/rules`
- `PUT /api/v1/config/rules/{id}`

---

#### Thresholds (`src/pages/configuration/ThresholdList.tsx`)
**Components:** ThresholdTable, ThresholdForm, ThresholdTypeSelector

**API Endpoints:**
- `GET /api/v1/config/thresholds`
- `POST /api/v1/config/thresholds`

---

#### Commit Criteria (`src/pages/configuration/CommitCriteriaList.tsx`)
**Components:** CriteriaTable, CriteriaForm, CommitModeSelector

**API Endpoints:**
- `GET /api/v1/config/commit-criteria`
- `POST /api/v1/config/commit-criteria`

---

#### File Naming Templates (`src/pages/configuration/TemplateList.tsx`)
**Components:** TemplateTable, TemplateForm, TemplatePreview

**API Endpoints:**
- `GET /api/v1/config/templates`
- `POST /api/v1/config/templates`
- `POST /api/v1/files/validate-template`

---

### 3. **Shared Components**

#### Layout Components
- ✅ `MainLayout.tsx` - Main app layout with drawer
- `PageHeader.tsx` - Page title and breadcrumbs
- `LoadingSpinner.tsx` - Loading state
- `ErrorBoundary.tsx` - Error handling

#### Data Display
- `DataTable.tsx` - Reusable table with sorting/pagination
- `StatusChip.tsx` - Colored status badges
- `MetricCard.tsx` - Card with metric value
- `EmptyState.tsx` - No data placeholder

#### Forms
- `ConfirmDialog.tsx` - Confirmation modal
- `FormDialog.tsx` - Generic form modal
- `SelectField.tsx` - Custom select input
- `DateRangePicker.tsx` - Date range selector

#### Charts
- `PieChart.tsx` - Status distribution
- `BarChart.tsx` - Metrics comparison
- `LineChart.tsx` - Trends over time
- `StatCard.tsx` - Simple stat display

---

## 🛠️ Services to Create

### API Services (`src/services/`)

1. **bucketService.ts**
   - getBuckets, getBucketById, searchBuckets
   - evaluateThresholds, transitionToGeneration
   - getBucketStatistics

2. **approvalService.ts**
   - getPendingApprovals, approveBucket, rejectBucket
   - bulkApprove, getApprovalHistory

3. **fileService.ts**
   - getFiles, getFileById, downloadFile
   - previewFile, searchFiles
   - generateFile

4. **deliveryService.ts**
   - getPendingDeliveries, getFailedDeliveries
   - retryDelivery, retryAllFailed
   - getDeliveryStatistics

5. **configurationService.ts**
   - Payers: getPayers, createPayer, updatePayer, deletePayer
   - Payees: getPayees, createPayee, updatePayee
   - Rules: getRules, createRule, updateRule
   - Thresholds: getThresholds, createThreshold
   - Criteria: getCriteria, createCriteria
   - Templates: getTemplates, createTemplate

6. ✅ **dashboardService.ts** - Already exists (needs enhancement)

7. ✅ **apiClient.ts** - Already created

---

## 📊 TypeScript Types (`src/types/`)

### Create `types/index.ts` with:

```typescript
// Enums
export enum BucketStatus {
  ACCUMULATING = 'ACCUMULATING',
  PENDING_APPROVAL = 'PENDING_APPROVAL',
  GENERATING = 'GENERATING',
  COMPLETED = 'COMPLETED',
  FAILED = 'FAILED',
}

export enum DeliveryStatus {
  PENDING = 'PENDING',
  DELIVERED = 'DELIVERED',
  FAILED = 'FAILED',
  RETRY = 'RETRY',
}

export enum RuleType {
  PAYER_PAYEE = 'PAYER_PAYEE',
  BIN_PCN = 'BIN_PCN',
  CUSTOM = 'CUSTOM',
}

export enum ThresholdType {
  CLAIM_COUNT = 'CLAIM_COUNT',
  AMOUNT = 'AMOUNT',
  TIME = 'TIME',
  HYBRID = 'HYBRID',
}

export enum CommitMode {
  AUTO = 'AUTO',
  MANUAL = 'MANUAL',
  HYBRID = 'HYBRID',
}

// Interfaces
export interface Bucket { ... }
export interface File { ... }
export interface Payer { ... }
export interface Payee { ... }
export interface BucketingRule { ... }
export interface Threshold { ... }
export interface CommitCriteria { ... }
export interface Template { ... }
```

---

## 🎨 Styling & Theme

### Already Configured in App.tsx:
✅ Material-UI theme with custom colors
✅ CssBaseline for consistent styling
✅ Custom component overrides (Card, Button)

### Additional Styling Needs:
- Custom CSS for tables
- Responsive breakpoints
- Loading skeletons
- Toast notifications (react-toastify) ✅

---

## 🔄 State Management

### React Query (Already configured)
✅ QueryClient setup in App.tsx
✅ 5-minute stale time
✅ Auto refetch disabled

### Custom Hooks to Create:
- `useDebounce.ts` - Debounce search input
- `usePagination.ts` - Table pagination
- `useFilters.ts` - Filter state management
- `usePolling.ts` - Auto-refresh for dashboard

---

## 📱 Responsive Design

### Breakpoints:
- xs: 0px (mobile)
- sm: 600px (tablet)
- md: 900px (laptop)
- lg: 1200px (desktop)
- xl: 1536px (large desktop)

### Mobile Considerations:
- Hamburger menu for navigation ✅
- Responsive tables (horizontal scroll)
- Touch-friendly buttons (min 44px)
- Bottom navigation for mobile

---

## ✅ Implementation Checklist

### Phase 1: Core Pages (HIGH PRIORITY)
- [ ] Dashboard page with metrics and charts
- [ ] Bucket List with filters
- [ ] Bucket Details page
- [ ] Approval Queue
- [ ] File List with download
- [ ] Delivery Tracking

### Phase 2: Configuration (MEDIUM PRIORITY)
- [ ] Payer management
- [ ] Payee management
- [ ] Bucketing Rules
- [ ] Thresholds
- [ ] Commit Criteria
- [ ] File Naming Templates

### Phase 3: Enhancements (LOW PRIORITY)
- [ ] Advanced search
- [ ] Export to CSV/Excel
- [ ] Custom reports
- [ ] User preferences
- [ ] Dark mode toggle
- [ ] Real-time notifications

---

## 🚀 Build & Deployment

### Development
```bash
cd edi835-admin-portal
npm install
npm run dev
```

### Production Build
```bash
npm run build
npm run preview
```

### Docker Build
```bash
docker build -t edi835-admin-portal .
docker run -p 80:80 edi835-admin-portal
```

---

## 📝 Environment Variables

Create `.env` file:
```
VITE_API_BASE_URL=http://localhost:8080/api/v1
VITE_ENABLE_POLLING=true
VITE_POLLING_INTERVAL=30000
```

---

## 🎯 Next Steps

1. ✅ Create App.tsx with routing
2. ✅ Create MainLayout with navigation
3. ✅ Create apiClient with interceptors
4. **NEXT:** Create Dashboard page with metrics
5. **THEN:** Create Bucket List and Details pages
6. **THEN:** Create Approval Queue
7. **THEN:** Create File and Delivery pages
8. **THEN:** Create Configuration pages

---

**Estimated Timeline:**
- **Phase 1 (Core Pages):** 2-3 days
- **Phase 2 (Configuration):** 2 days
- **Phase 3 (Enhancements):** 1-2 days
- **Total:** 5-7 days for complete frontend

**Current Progress:** ~10% (App structure, layout, routing)
**Target:** 100% functional admin portal

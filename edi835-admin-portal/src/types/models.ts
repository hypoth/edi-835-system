// Enums
export enum BucketStatus {
  ACCUMULATING = 'ACCUMULATING',
  PENDING_APPROVAL = 'PENDING_APPROVAL',
  GENERATING = 'GENERATING',
  COMPLETED = 'COMPLETED',
  FAILED = 'FAILED',
  MISSING_CONFIGURATION = 'MISSING_CONFIGURATION',
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

// Dashboard Types
export interface DashboardSummary {
  totalBuckets: number;
  activeBuckets: number;
  pendingApprovalBuckets: number;
  totalFiles: number;
  pendingDeliveryFiles: number;
  failedDeliveryFiles: number;
  totalClaims: number;
  processedClaims: number;
  rejectedClaims: number;
}

export interface SystemHealth {
  status: 'HEALTHY' | 'WARNING' | 'CRITICAL';
  staleBuckets: number;
  failedDeliveries: number;
  pendingApprovals: number;
  timestamp: string;
}

export interface Alert {
  severity: 'INFO' | 'WARNING' | 'ERROR';
  message: string;
  type: string;
  count: number;
}

// Bucket Types
export interface Bucket {
  bucketId: string;
  bucketingRule?: BucketingRule;
  bucketingRuleId?: string;
  bucketingRuleName: string;
  payerId: string;
  payerName: string;
  payeeId: string;
  payeeName: string;
  binNumber?: string;
  pcnNumber?: string;
  status: BucketStatus;
  claimCount: number;
  totalAmount: number;
  createdAt: string;
  updatedAt: string;
  awaitingApprovalSince?: string;
  generationStartedAt?: string;
  generationCompletedAt?: string;
  // Error tracking fields
  lastErrorMessage?: string;
  lastErrorAt?: string;
}

// File Types
export interface FileHistory {
  fileId: string;
  fileName: string;
  bucket: Bucket;
  fileSizeBytes: number;
  claimCount: number;
  totalAmount: number;
  generatedBy: string;
  generatedAt: string;
  deliveryStatus: DeliveryStatus;
  deliveredAt?: string;
  retryCount: number;
  errorMessage?: string;
}

// Configuration Types
export interface Payer {
  id: string;
  payerId: string;
  payerName: string;
  isaQualifier?: string;
  isaSenderId: string;
  gsApplicationSenderId?: string;
  addressStreet?: string;
  addressCity?: string;
  addressState?: string;
  addressZip?: string;
  sftpHost?: string;
  sftpPort?: number;
  sftpUsername?: string;
  sftpPassword?: string;
  sftpPath?: string;
  requiresSpecialHandling?: boolean;
  isActive: boolean;
  createdAt?: string;
  updatedAt?: string;
  createdBy?: string;
  updatedBy?: string;
}

export interface Payee {
  id: string;
  payeeId: string;
  payeeName: string;
  npi?: string;
  taxId?: string;
  contactEmail?: string;
  contactPhone?: string;
  requiresSpecialHandling?: boolean;
  isActive: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface BucketingRule {
  id: string;
  ruleId?: string; // Alias for backward compatibility
  ruleName: string;
  ruleType: RuleType;
  priority: number;
  groupingExpression?: string;
  linkedPayerId?: string;
  linkedPayerName?: string;
  linkedPayeeId?: string;
  linkedPayeeName?: string;
  description?: string;
  isActive: boolean;
  createdAt: string;
  updatedAt: string;
  createdBy?: string;
  updatedBy?: string;
}

export interface GenerationThreshold {
  thresholdId: string;
  thresholdName: string;
  thresholdType: ThresholdType;
  linkedBucketingRule: BucketingRule;
  maxClaims?: number;
  maxAmount?: number;
  timeDuration?: 'DAILY' | 'WEEKLY' | 'BIWEEKLY' | 'MONTHLY';
  isActive: boolean;
  createdAt: string;
}

// Type alias for compatibility
export type Threshold = GenerationThreshold & { id: string };

export interface CommitCriteria {
  id: string;
  criteriaId?: string; // Alias for backward compatibility
  criteriaName?: string;
  linkedBucketingRuleId?: string;
  linkedBucketingRuleName?: string;
  linkedBucketingRule?: BucketingRule; // For compatibility
  commitMode: CommitMode;
  autoCommitThreshold?: number;
  manualApprovalThreshold?: number;
  approvalClaimCountThreshold?: number;
  approvalAmountThreshold?: number;
  approvalRequiredRoles?: string[];
  approvalRoles?: string;
  overridePermissions?: string[];
  isActive: boolean;
  createdAt: string;
  updatedAt?: string;
  createdBy?: string;
  updatedBy?: string;
}

export interface FileNamingTemplate {
  templateId: string;
  templateName: string;
  templatePattern: string;
  linkedBucketingRule?: BucketingRule;
  isDefault: boolean;
  caseConversion?: 'UPPERCASE' | 'LOWERCASE' | 'CAPITALIZE' | 'NONE';
  createdAt: string;
}

// Approval Types
export interface ApprovalLog {
  approvalId: string;
  bucket: Bucket;
  action: 'APPROVAL' | 'REJECTION';
  approvedBy: string;
  comments?: string;
  approvedAt: string;
}

export interface ApprovalRequest {
  actionBy: string;
  comments?: string;
  rejectionReason?: string;
}

// Configuration Check Types
export interface BucketConfigurationCheck {
  bucketId: string;
  hasAllConfiguration: boolean;
  payerExists: boolean;
  payeeExists: boolean;
  missingPayerId?: string;
  missingPayerName?: string;
  missingPayeeId?: string;
  missingPayeeName?: string;
  actionRequired: 'CREATE_PAYER' | 'CREATE_PAYEE' | 'CREATE_BOTH' | 'NONE';
}

export interface CreatePayerFromBucketRequest {
  bucketId: string;
  payerId: string;
  payerName: string;
  isaSenderId: string;
  isaQualifier?: string;
  gsApplicationSenderId?: string;
  addressStreet?: string;
  addressCity?: string;
  addressState?: string;
  addressZip?: string;
  sftpHost?: string;
  sftpPort?: number;
  sftpUsername?: string;
  sftpPassword?: string;
  sftpPath?: string;
  requiresSpecialHandling?: boolean;
  isActive?: boolean;
  createdBy?: string;
}

export interface CreatePayeeFromBucketRequest {
  bucketId: string;
  payeeId: string;
  payeeName: string;
  npi?: string;
  taxId?: string;
  addressStreet?: string;
  addressCity?: string;
  addressState?: string;
  addressZip?: string;
  requiresSpecialHandling?: boolean;
  isActive?: boolean;
  createdBy?: string;
}

// Check Payment Types (Phase 1: Check Payment Implementation)

export enum CheckStatus {
  RESERVED = 'RESERVED',
  ASSIGNED = 'ASSIGNED',
  ACKNOWLEDGED = 'ACKNOWLEDGED',
  ISSUED = 'ISSUED',
  VOID = 'VOID',
  CANCELLED = 'CANCELLED',
}

export enum ReservationStatus {
  ACTIVE = 'ACTIVE',
  EXHAUSTED = 'EXHAUSTED',
  CANCELLED = 'CANCELLED',
}

export interface CheckPayment {
  id: string;
  bucketId: string;
  checkNumber: string;
  checkAmount: number;
  checkDate: string;
  bankName?: string;
  routingNumber?: string;
  accountNumberLast4?: string;
  status: CheckStatus;
  assignedBy?: string;
  assignedAt?: string;
  acknowledgedBy?: string;
  acknowledgedAt?: string;
  issuedBy?: string;
  issuedAt?: string;
  voidReason?: string;
  voidedBy?: string;
  voidedAt?: string;
  paymentMethodId?: string;
  createdAt: string;
  updatedAt: string;
  canBeVoided?: boolean;
  hoursUntilVoidDeadline?: number;
}

export interface CheckReservation {
  id: string;
  checkNumberStart: string;
  checkNumberEnd: string;
  totalChecks: number;
  checksUsed: number;
  checksRemaining: number;
  usagePercentage: number;
  bankName: string;
  routingNumber?: string;
  accountNumberLast4?: string;
  paymentMethodId?: string;
  payerId: string;
  payerName?: string;
  status: ReservationStatus;
  createdAt: string;
  updatedAt: string;
  createdBy: string;
  isLowStock?: boolean;
  isExhausted?: boolean;
}

export interface CheckAuditLog {
  id: number;
  checkPaymentId: string;
  checkNumber: string;
  action: string;
  bucketId?: string;
  amount?: number;
  performedBy: string;
  notes?: string;
  createdAt: string;
}

export interface ManualCheckAssignmentRequest {
  checkNumber: string;
  checkDate: string;
  bankName?: string;
  routingNumber?: string;
  accountLast4?: string;
  assignedBy: string;
}

export interface CreateCheckReservationRequest {
  payerId: string;
  checkNumberStart: string;
  checkNumberEnd: string;
  bankName: string;
  routingNumber?: string;
  accountLast4?: string;
  paymentMethodId?: string;
  createdBy: string;
}

export interface AcknowledgeCheckRequest {
  acknowledgedBy: string;
}

export interface IssueCheckRequest {
  issuedBy: string;
}

export interface VoidCheckRequest {
  reason: string;
  voidedBy: string;
}

export interface ReservationSummary {
  totalActiveReservations: number;
  totalAvailableChecks: number;
  totalUsedChecks: number;
  lowStockReservations: number;
  needsAttention: boolean;
}

// Check Payment Workflow Configuration Types
export enum WorkflowMode {
  NONE = 'NONE',           // No check payment required (EFT/other)
  SEPARATE = 'SEPARATE',   // Approve first, then assign check separately
  COMBINED = 'COMBINED',   // Approve and assign check in single dialog
}

export enum AssignmentMode {
  MANUAL = 'MANUAL',       // Manual check entry only
  AUTO = 'AUTO',           // Auto-assign from reservations only
  BOTH = 'BOTH',           // User can choose manual or auto
}

export interface CheckPaymentWorkflowConfig {
  id: string;
  configName: string;
  workflowMode: WorkflowMode;
  assignmentMode: AssignmentMode;
  requireAcknowledgment: boolean;
  linkedThresholdId: string;
  linkedThresholdName?: string;
  linkedBucketingRuleName?: string;
  description?: string;
  isActive: boolean;
  createdAt: string;
  updatedAt: string;
  createdBy?: string;
  updatedBy?: string;
}

export interface CheckPaymentWorkflowConfigRequest {
  configName: string;
  workflowMode: string;
  assignmentMode: string;
  requireAcknowledgment: boolean;
  linkedThresholdId: string;
  description?: string;
  isActive?: boolean;
  createdBy?: string;
  updatedBy?: string;
}

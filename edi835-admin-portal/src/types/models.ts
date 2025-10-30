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
  bucketingRule: BucketingRule;
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

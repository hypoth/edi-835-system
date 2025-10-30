// src/types/index.ts

export interface Payer {
  id: string;
  payerId: string;
  payerName: string;
  isaQualifier: string;
  isaSenderId: string;
  gsApplicationSenderId: string;
  address?: Address;
  isActive: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface Payee {
  id: string;
  payeeId: string;
  payeeName: string;
  npi?: string;
  taxId?: string;
  address?: Address;
  isActive: boolean;
}

export interface Address {
  street: string;
  city: string;
  state: string;
  zip: string;
  country?: string;
}

export interface BucketingRule {
  id: string;
  ruleName: string;
  ruleType: 'PAYER_PAYEE' | 'BIN_PCN' | 'CUSTOM';
  groupingExpression?: string;
  priority: number;
  isActive: boolean;
  linkedPayerId?: string;
  linkedPayeeId?: string;
  description?: string;
  createdAt: string;
  updatedAt: string;
}

export interface GenerationThreshold {
  id: string;
  thresholdName: string;
  thresholdType: 'CLAIM_COUNT' | 'AMOUNT' | 'TIME' | 'HYBRID';
  maxClaims?: number;
  maxAmount?: number;
  timeDuration?: 'DAILY' | 'WEEKLY' | 'BIWEEKLY' | 'MONTHLY';
  generationSchedule?: string;
  linkedBucketingRuleId: string;
  isActive: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface CommitCriteria {
  id: string;
  criteriaName: string;
  commitMode: 'AUTO' | 'MANUAL' | 'HYBRID';
  autoCommitThreshold?: number;
  manualApprovalThreshold?: number;
  approvalRequiredRoles?: string[];
  overridePermissions?: string[];
  linkedBucketingRuleId: string;
  isActive: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface FileNamingTemplate {
  id: string;
  templateName: string;
  templatePattern: string;
  dateFormat: string;
  timeFormat: string;
  sequencePadding: number;
  caseConversion: 'UPPER' | 'LOWER' | 'NONE';
  separatorType: '_' | '-' | '.' | 'NONE';
  isDefault: boolean;
  linkedBucketingRuleId?: string;
  version: number;
  createdBy: string;
  createdAt: string;
  updatedAt: string;
}

export interface EdiBucket {
  bucketId: string;
  status: 'ACCUMULATING' | 'PENDING_APPROVAL' | 'GENERATING' | 'COMPLETED' | 'FAILED';
  bucketingRuleId: string;
  bucketingRuleName: string;
  payerId: string;
  payerName: string;
  payeeId: string;
  payeeName: string;
  claimCount: number;
  totalAmount: number;
  rejectionCount: number;
  lastUpdated: string;
  awaitingApprovalSince?: string;
  approvedBy?: string;
  approvedAt?: string;
  fileNamingTemplateId?: string;
}

export interface BucketApproval {
  bucketId: string;
  action: 'APPROVE' | 'REJECT';
  comments?: string;
  scheduledGenerationTime?: string;
}

export interface FileGenerationHistory {
  id: string;
  bucketId: string;
  generatedFileName: string;
  fileSize: number;
  claimCount: number;
  totalAmount: number;
  generatedAt: string;
  generatedBy: string;
  deliveryStatus: 'PENDING' | 'DELIVERED' | 'FAILED';
  deliveredAt?: string;
  errorMessage?: string;
}

export interface DashboardSummary {
  activeBucketsCount: number;
  pendingApprovalCount: number;
  totalClaimsAccumulated: number;
  totalAmountAccumulated: number;
  totalRejections: number;
  rejectionRate: number;
  filesGeneratedToday: number;
  filesGeneratedThisWeek: number;
  averageClaimsPerFile: number;
  averageFileAmount: number;
}

export interface ActiveBucketMetrics {
  buckets: EdiBucket[];
  timestamp: string;
}

export interface RejectionAnalytics {
  totalRejections: number;
  rejectionRate: number;
  topRejectionReasons: Array<{
    reasonCode: string;
    reasonDescription: string;
    count: number;
    percentage: number;
  }>;
  rejectionTrend: Array<{
    date: string;
    count: number;
  }>;
}

export interface PaymentMethod {
  id: string;
  methodType: 'EFT' | 'CHECK';
  routingNumber?: string;
  accountNumber?: string;
  bankName?: string;
  isActive: boolean;
}

// API Response types
export interface ApiResponse<T> {
  data: T;
  message?: string;
  timestamp: string;
}

export interface PaginatedResponse<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  size: number;
  number: number;
}

export interface ErrorResponse {
  error: string;
  message: string;
  timestamp: string;
  path: string;
}

// Form types
export interface BucketingRuleForm {
  ruleName: string;
  ruleType: 'PAYER_PAYEE' | 'BIN_PCN' | 'CUSTOM';
  groupingExpression?: string;
  priority: number;
  linkedPayerId?: string;
  linkedPayeeId?: string;
  description?: string;
}

export interface ThresholdForm {
  thresholdName: string;
  thresholdType: 'CLAIM_COUNT' | 'AMOUNT' | 'TIME' | 'HYBRID';
  maxClaims?: number;
  maxAmount?: number;
  timeDuration?: 'DAILY' | 'WEEKLY' | 'BIWEEKLY' | 'MONTHLY';
  generationSchedule?: string;
  linkedBucketingRuleId: string;
}

export interface FileNamingTemplateForm {
  templateName: string;
  templatePattern: string;
  dateFormat: string;
  timeFormat: string;
  sequencePadding: number;
  caseConversion: 'UPPER' | 'LOWER' | 'NONE';
  separatorType: '_' | '-' | '.' | 'NONE';
  isDefault: boolean;
  linkedBucketingRuleId?: string;
}